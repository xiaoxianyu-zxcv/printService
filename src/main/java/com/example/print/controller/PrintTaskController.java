package com.example.print.controller;

import com.example.print.model.PrintTask;
import com.example.print.model.PrintTaskStatus;
import com.example.print.service.PrintTaskService;
import com.example.print.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/print-tasks")
@Slf4j
public class PrintTaskController {

    @Autowired
    private PrintTaskService taskService;

    @Autowired
    private NotificationService notificationService;

    /**
     * 创建打印任务
     */
    @PostMapping
    public ResponseEntity<PrintTask> createTask(@RequestBody PrintTask task) {
        log.info("收到创建打印任务请求: {}", task);

        try {
            PrintTask savedTask = taskService.createTask(task);

            // 基于store_id推送（优先）创建完成后，通过WebSocket通知客户端
            if (task.getStoreId() != null) {
                notificationService.broadcastToPrintersByStore(task.getStoreId(), savedTask);
            }
            // 兼容现有逻辑
            else if (task.getMerchantId() != null) {
                notificationService.broadcastToPrintersByMerchant(Integer.parseInt(task.getMerchantId()), savedTask);
            }



            return ResponseEntity.ok(savedTask);
        } catch (Exception e) {
            log.error("创建打印任务失败", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 获取待处理任务
     */
    @GetMapping("/pending")
    public ResponseEntity<List<PrintTask>> getPendingTasks(
            @RequestParam(required = false) Integer merchantId,
            @RequestParam(required = false) Integer storeId) {

        List<PrintTask> tasks;

        try {
            // 按优先级：先按storeId查询，再按merchantId，最后全部
            if (storeId != null) {
                tasks = taskService.getPendingTasksByStore(storeId);
                log.info("获取门店 {} 的待处理任务，共 {} 个", storeId, tasks.size());
            } else if (merchantId != null) {
                tasks = taskService.getPendingTasksByMerchant(merchantId);
                log.info("获取商户 {} 的待处理任务，共 {} 个", merchantId, tasks.size());
            } else {
                tasks = taskService.getPendingTasks();
                log.info("获取所有待处理任务，共 {} 个", tasks.size());
            }

            return ResponseEntity.ok(tasks);
        } catch (Exception e) {
            log.error("获取待处理任务失败", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 获取任务详情
     */
    @GetMapping("/{taskId}")
    public ResponseEntity<PrintTask> getTask(@PathVariable String taskId) {
        log.info("获取任务详情: {}", taskId);

        Optional<PrintTask> task = taskService.getTask(taskId);

        return task.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 更新任务状态
     */
    @PutMapping("/{taskId}/status")
    public ResponseEntity<PrintTask> updateTaskStatus(
            @PathVariable String taskId,
            @RequestBody String status,
            @RequestParam(required = false) String clientId) {

        log.info("更新任务状态: {} -> {}, 客户端: {}", taskId, status, clientId);

        try {
            PrintTaskStatus taskStatus = PrintTaskStatus.valueOf(status.toUpperCase());

            // 如果没有提供客户端ID，使用默认值
            String actualClientId = clientId != null ? clientId : "API_CLIENT";

            PrintTask updatedTask = taskService.updateTaskStatus(taskId, taskStatus, actualClientId);

            if (updatedTask != null) {
                return ResponseEntity.ok(updatedTask);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IllegalArgumentException e) {
            log.error("无效的任务状态: {}", status);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("更新任务状态失败", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 标记任务已接收
     */
    @PostMapping("/{taskId}/received")
    public ResponseEntity<Void> markTaskReceived(
            @PathVariable String taskId,
            @RequestParam(required = false) String clientId) {

        log.info("标记任务已接收: {}, 客户端: {}", taskId, clientId);

        try {
            // 如果没有提供客户端ID，使用默认值
            String actualClientId = clientId != null ? clientId : "API_CLIENT";

            PrintTask updatedTask = taskService.updateTaskStatus(
                    taskId, PrintTaskStatus.PRINTING, actualClientId);

            if (updatedTask != null) {
                return ResponseEntity.ok().build();
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("标记任务已接收失败: {}", taskId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 获取商户的任务列表
     */
    @GetMapping("/merchant/{merchantId}")
    public ResponseEntity<Page<PrintTask>> getTasksByMerchant(
            @PathVariable int merchantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("获取商户 {} 的任务列表，页码: {}, 大小: {}", merchantId, page, size);

        try {
            Page<PrintTask> tasks = taskService.getTasksByMerchant(merchantId, page, size);
            return ResponseEntity.ok(tasks);
        } catch (Exception e) {
            log.error("获取商户任务列表失败", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 创建测试打印任务
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> createTestTask(
            @RequestParam(required = false) Integer merchantId,
            @RequestParam(required = false) String content) {

        log.info("创建测试打印任务，商户: {}", merchantId);

        try {
            // 默认商户ID
            int actualMerchantId = merchantId != null ? merchantId : 1;

            // 默认内容
            String actualContent = content != null ? content :
                    "测试打印内容\n这是一条测试消息\n打印时间: " + LocalDateTime.now();

            // 创建测试任务
            PrintTask task = new PrintTask();
            task.setTaskId(java.util.UUID.randomUUID().toString());
            task.setOrderId(0); // 测试任务
            task.setOrderNo("TEST_" + System.currentTimeMillis());
            task.setMerchantId(String.valueOf(actualMerchantId));
            task.setStoreId(0);
            task.setContent(actualContent);
            task.setStatus(PrintTaskStatus.PENDING);
            task.setCreateTime(LocalDateTime.now());

            PrintTask savedTask = taskService.createTask(task);


            // 基于store_id推送（优先）
            if (task.getStoreId() != null) {
                notificationService.broadcastToPrintersByStore(task.getStoreId(), savedTask);
            }
            // 兼容现有逻辑
            else if (task.getMerchantId() != null) {
                notificationService.broadcastToPrintersByMerchant(Integer.parseInt(task.getMerchantId()), savedTask);
            }


            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "测试打印任务已创建");
            response.put("taskId", savedTask.getTaskId());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("创建测试打印任务失败", e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "创建测试打印任务失败: " + e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }
}
