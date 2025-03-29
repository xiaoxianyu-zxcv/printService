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
    @Scheduled(fixedRate = 60000)
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
                        int merchantId = ((Number) order.get("merchant_id")).intValue();
                        notificationService.broadcastToPrintersByMerchant(merchantId, task);
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

        // 构建打印数据
        Map<String, Object> printData = new HashMap<>();
        printData.put("orderNo", order.get("order_no"));
        printData.put("merchant", "指尖赤壁"); // 可从商户表中查询
        printData.put("day_index", getDayOrderIndex(((Number) order.get("merchant_id")).intValue()));

        // 处理时间戳
        Long payTime = (Long) order.get("pay_time");
        printData.put("orderTime", formatTimestamp(payTime));

        // 订单商品信息
        printData.put("goods", formatOrderItems(orderItems));

        // 金额信息
        printData.put("deliveryFee", order.get("delivery_fee"));
        printData.put("totalPrice", order.get("goods_price"));
        printData.put("actualPayment", order.get("pay_money"));

        // 支付方式
        printData.put("paymentMethod", getPaymentMethod(((Number) order.get("pay_type")).intValue()));

        // 配送状态
        printData.put("delivery_status", getDeliveryStatus(((Number) order.get("status")).intValue()));

        // 用户信息
        if (userInfo != null) {
            printData.put("customer", userInfo.get("nickname"));
            printData.put("customerPhone", userInfo.get("mobile"));
            printData.put("address", userInfo.get("address"));
        } else {
            printData.put("customer", "未知用户");
            printData.put("customerPhone", "");
            printData.put("address", "");
        }

        return objectMapper.writeValueAsString(printData);
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