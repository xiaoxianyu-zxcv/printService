package com.example.print.service;

import com.example.print.model.PrintTask;
import com.example.print.model.PrintTaskStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
@Slf4j
public class OrderSyncService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PrintTaskService printTaskService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${print.order.sync.batch-size:50}")
    private int batchSize;

    @Value("${print.order.last-sync-id:0}")
    private int initialLastSyncId;

    private int lastSyncOrderId;

    @PostConstruct
    public void init() {
        // 加载最后同步的订单ID，实际应用中可能从配置文件或数据库加载
        lastSyncOrderId = initialLastSyncId;
        log.info("初始化订单同步服务，上次同步的订单ID: {}", lastSyncOrderId);
    }

    /**
     * 定时同步已付款订单
     * 每分钟执行一次
     */
    @Scheduled(fixedRate = 10000)
    @Transactional
    public void syncPaidOrders() {
        log.info("开始同步已付款订单，从ID: {} 开始", lastSyncOrderId);

        try {
            // 查询新的已付款订单
            String sql = "SELECT * FROM tp_retail_bill_order WHERE id > ? AND pay_state = 1 " +
                    "ORDER BY id ASC LIMIT ?";

            List<Map<String, Object>> newOrders = jdbcTemplate.queryForList(
                    sql, lastSyncOrderId, batchSize);

            if (newOrders.isEmpty()) {
                log.info("没有新的已付款订单");
                return;
            }

            log.info("发现 {} 个新的已付款订单", newOrders.size());

            for (Map<String, Object> order : newOrders) {
                int orderId = ((Number) order.get("id")).intValue();

                // 检查该订单是否已经创建过打印任务
                if (!printTaskService.isOrderExists(orderId)) {
                    try {
                        // 创建打印任务
                        PrintTask task = createPrintTask(order);
                        log.info("创建打印任务: {}, 订单号: {}", task.getTaskId(), task.getOrderNo());

                        // 向该商户的所有客户端广播打印任务
                        int storeId = ((Number) order.get("store_id")).intValue();
                        notificationService.broadcastToPrintersByStore(storeId, task);
                    } catch (Exception e) {
                        log.error("处理订单 {} 失败", orderId, e);
                    }
                } else {
                    log.info("订单 {} 已存在打印任务，跳过", orderId);
                }

                // 更新最后同步的订单ID
                lastSyncOrderId = orderId;
            }

            log.info("订单同步完成，最后同步的订单ID更新为: {}", lastSyncOrderId);

        } catch (Exception e) {
            log.error("同步已付款订单失败", e);
        }
    }

    /**
     * 创建打印任务
     */
    private PrintTask createPrintTask(Map<String, Object> order) throws Exception {
        // 根据订单信息生成打印内容
        String content = generatePrintContent(order);

        // 创建打印任务
        PrintTask task = new PrintTask();
        task.setTaskId(UUID.randomUUID().toString());
        task.setOrderId(((Number) order.get("id")).intValue());
        task.setOrderNo((String) order.get("order_no"));
        task.setMerchantId(String.valueOf(((Number) order.get("merchant_id")).intValue()));
        task.setStoreId(((Number) order.get("store_id")).intValue());
        task.setContent(content);
        task.setStatus(PrintTaskStatus.PENDING);
        task.setCreateTime(LocalDateTime.now());
        task.setLastUpdateTime(LocalDateTime.now());

        // 保存打印任务
        return printTaskService.createTask(task);
    }

    /**
     * 根据订单信息生成打印内容
     */
    private String generatePrintContent(Map<String, Object> order) throws Exception {
        // 查询订单商品信息
        List<Map<String, Object>> orderItems = getOrderItems(((Number) order.get("id")).intValue());

        // 查询用户信息
        Map<String, Object> userInfo = getUserInfo(((Number) order.get("uid")).intValue());

        // 查询线上订单信息（如果是线上订单）
        Map<String, Object> onlineInfo = null;
        // 安全地获取订单类型
        int orderType = 1; // 默认为线上
        if (order.get("type") instanceof Number) {
            orderType = ((Number) order.get("type")).intValue();
        } else if (order.get("type") instanceof Boolean) {
            // 如果type是布尔类型，true转为1，false转为2
            orderType = ((Boolean) order.get("type")) ? 1 : 2;
        }

        if (orderType == 1) {
            onlineInfo = getOnlineOrderInfo(((Number) order.get("id")).intValue());
        }

        // 构建打印数据
        Map<String, Object> printData = new HashMap<>();
        printData.put("orderNo", order.get("order_no"));
        printData.put("merchant", "指尖赤壁"); // 可从商户表中查询
        printData.put("day_index", getDayOrderIndex(((Number) order.get("merchant_id")).intValue()));

        // 处理时间戳
        Long payTime = (Long) order.get("pay_time");
        printData.put("payTime", payTime);
        printData.put("orderTime", formatTimestamp(payTime));

        // 订单类型信息 - 安全处理类型
        printData.put("type", orderType); // 1线上 2线下

        // 安全获取支付类型
        int payType = 0;
        if (order.get("pay_type") instanceof Number) {
            payType = ((Number) order.get("pay_type")).intValue();
        }
        printData.put("pay_type", payType);

        // 商品和金额信息 - 安全获取数值
        double goodsPrice = getDoubleValue(order, "goods_price", 0.0);
        double payMoney = getDoubleValue(order, "pay_money", 0.0);
        double deliveryFee = getDoubleValue(order, "delivery_fee", 0.0);

        printData.put("goods_price", goodsPrice);
        printData.put("pay_money", payMoney);
        printData.put("delivery_fee", deliveryFee);

        // 将订单商品信息转换为数组形式，包含详细信息
        List<Map<String, Object>> goodsArray = new ArrayList<>();
        for (Map<String, Object> item : orderItems) {
            Map<String, Object> goodsItem = new HashMap<>();
            goodsItem.put("goods_name", item.get("goods_name"));

            // 安全获取数量和价格
            double sellNum = getDoubleValue(item, "sell_num", 0.0);
            double sellPrice = getDoubleValue(item, "sell_price", 0.0);

            goodsItem.put("sell_num", (int)sellNum); // 销售数量转为整数
            goodsItem.put("sell_price", sellPrice); // 销售单价
            goodsItem.put("sell_subtotal", sellNum * sellPrice); // 计算小计

            goodsArray.add(goodsItem);
        }
        printData.put("goodsItems", goodsArray); // 商品数组，包含详细信息

        // 线上订单特有信息
        if (onlineInfo != null) {
            // 安全获取pickup_type
            int pickupType = 0;
            if (onlineInfo.get("pickup_type") instanceof Number) {
                pickupType = ((Number) onlineInfo.get("pickup_type")).intValue();
            }
            printData.put("pickup_type", pickupType);

            printData.put("user_name", onlineInfo.getOrDefault("user_name", ""));
            printData.put("user_phone", onlineInfo.getOrDefault("user_phone", ""));
            printData.put("user_address", onlineInfo.getOrDefault("user_address", ""));
            printData.put("remark", onlineInfo.getOrDefault("remark", ""));
        }

        // 用户信息
        if (userInfo != null) {
            printData.put("customer", userInfo.getOrDefault("nickname", ""));
            printData.put("customerPhone", userInfo.getOrDefault("mobile", ""));
            // 如果没有线上订单地址信息，则使用用户基本地址
            if (!printData.containsKey("user_address") || printData.get("user_address") == null) {
                printData.put("user_address", userInfo.getOrDefault("address", ""));
            }
        } else {
            printData.put("customer", "未知用户");
            printData.put("customerPhone", "");
        }

        return objectMapper.writeValueAsString(printData);
    }

    /**
     * 从Map中安全获取double值
     */
    private double getDoubleValue(Map<String, Object> map, String key, double defaultValue) {
        if (!map.containsKey(key) || map.get(key) == null) {
            return defaultValue;
        }

        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        } else if (value instanceof Boolean) {
            return ((Boolean) value) ? 1.0 : 0.0;
        }

        return defaultValue;
    }


    /**
     * 获取线上订单信息
     */
    private Map<String, Object> getOnlineOrderInfo(int orderId) {
        String sql = "SELECT * FROM tp_retail_bill_online WHERE bill_id = ?";
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, orderId);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * 获取订单商品信息
     */
    private List<Map<String, Object>> getOrderItems(int orderId) {
        String sql = "SELECT * FROM tp_retail_bill_sell WHERE bill_id = ?";
        return jdbcTemplate.queryForList(sql, orderId);
    }

    /**
     * 获取用户信息
     */
    private Map<String, Object> getUserInfo(int uid) {
        String sql = "SELECT * FROM tp_user WHERE id = ?";
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, uid);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * 格式化订单商品
     */
    private String formatOrderItems(List<Map<String, Object>> items) {
        if (items.isEmpty()) {
            return "未知商品";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = items.get(i);
            sb.append(item.get("goods_name"));
            sb.append(" x").append(item.get("nums"));

            if (i < items.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    /**
     * 获取当天商户订单序号
     */
    private String getDayOrderIndex(int merchantId) {
        // 获取当天日期
        String today = java.time.LocalDate.now().toString();

        // 查询当天该商户的订单数
        String sql = "SELECT COUNT(*) FROM tp_retail_bill_order " +
                "WHERE merchant_id = ? AND pay_state = 1 AND DATE(FROM_UNIXTIME(pay_time)) = ?";

        int count = jdbcTemplate.queryForObject(sql, Integer.class, merchantId, today);
        return String.valueOf(count + 1);
    }

    /**
     * 格式化Unix时间戳
     */
    private String formatTimestamp(Long timestamp) {
        if (timestamp == null || timestamp == 0) {
            return "";
        }

        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(timestamp),
                ZoneId.systemDefault());

        return dateTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * 获取支付方式描述
     */
    private String getPaymentMethod(int payType) {
        switch (payType) {
            case 1:
                return "微信小程序";
            case 2:
                return "余额支付";
            default:
                return "其他方式";
        }
    }

    /**
     * 获取配送状态描述
     */
    private String getDeliveryStatus(int status) {
        switch (status) {
            case 0:
                return "未支付";
            case 1:
                return "待发货";
            case 2:
                return "已发货";
            case 3:
                return "已收货";
            case 4:
                return "已评价";
            default:
                return "未知状态";
        }
    }
}
