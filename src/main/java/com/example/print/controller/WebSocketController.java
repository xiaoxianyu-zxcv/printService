package com.example.print.controller;

import com.example.print.model.PrintClient;
import com.example.print.model.PrintTask;
import com.example.print.model.PrintTaskStatus;
import com.example.print.service.PrintClientService;
import com.example.print.service.PrintTaskService;
import com.example.print.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Controller
@Slf4j
public class WebSocketController {

    @Autowired
    private PrintTaskService taskService;

    @Autowired
    private PrintClientService clientService;

    @Autowired
    private NotificationService notificationService;

    /**
     * 处理客户端打印请求
     */
    @MessageMapping("/print")
    @SendTo("/topic/print-status")
    public Map<String, Object> handlePrintRequest(@Payload PrintTask task) {
        log.info("通过WebSocket收到打印请求: {}", task);

        Map<String, Object> response = new HashMap<>();

        try {
            // 生成任务ID
            if (task.getTaskId() == null) {
                task.setTaskId(UUID.randomUUID().toString());
            }

            // 创建并保存打印任务
            PrintTask savedTask = taskService.createTask(task);

            // 向商户的所有客户端广播这个任务
            notificationService.broadcastToPrintersByMerchant(Integer.parseInt(task.getMerchantId()), savedTask);

            // 返回成功响应
            response.put("success", true);
            response.put("message", "打印请求已接收");
            response.put("taskId", savedTask.getTaskId());

        } catch (Exception e) {
            log.error("处理打印请求失败", e);

            response.put("success", false);
            response.put("message", "处理打印请求失败: " + e.getMessage());
        }

        return response;
    }

    /**
     * 处理客户端打印结果
     */
    @MessageMapping("/print-result")
    public void handlePrintResult(@Payload Map<String, Object> result) {
        String taskId = (String) result.get("taskId");
        String status = (String) result.get("status");
        String clientId = (String) result.get("clientId");
        String errorMessage = (String) result.get("errorMessage");

        log.info("收到打印结果: 任务={}, 状态={}, 客户端={}", taskId, status, clientId);

        if (taskId != null && status != null && clientId != null) {
            try {
                // 更新客户端心跳
                clientService.updateHeartbeat(clientId);

                // 更新任务状态
                PrintTaskStatus taskStatus = PrintTaskStatus.valueOf(status.toUpperCase());
                taskService.updateTaskStatus(taskId, taskStatus, clientId);

            } catch (Exception e) {
                log.error("处理打印结果失败", e);
            }
        }
    }

    /**
     * 处理客户端注册
     */
    @MessageMapping("/register")
    public void handleClientRegister(@Payload PrintClient client, SimpMessageHeaderAccessor headerAccessor) {
        log.info("收到客户端注册请求: {}", client);

        try {
            // 如果没有客户端ID，生成一个
            if (client.getClientId() == null || client.getClientId().isEmpty()) {
                client.setClientId(UUID.randomUUID().toString());
            }

            // 设置IP地址（如果有）
            String sessionId = headerAccessor.getSessionId();
            if (sessionId != null) {
                // 这里可以从headerAccessor中获取一些连接信息
                client.setIpAddress(headerAccessor.getSessionAttributes().getOrDefault("ip", "").toString());
            }

            // 注册客户端
            PrintClient registeredClient = clientService.registerClient(client);

            // 发送注册成功消息给客户端
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("clientId", registeredClient.getClientId());
            response.put("message", "注册成功");

            notificationService.sendMessageToClient(registeredClient.getClientId(), "REGISTER_RESPONSE", response);

            log.info("客户端注册成功: {}", registeredClient.getClientId());

        } catch (Exception e) {
            log.error("客户端注册失败", e);

            // 发送错误消息
            notificationService.sendErrorNotification(null, "客户端注册失败: " + e.getMessage());
        }
    }

    /**
     * 处理客户端心跳
     */
    @MessageMapping("/heartbeat")
    @SendTo("/topic/heartbeat")
    public Map<String, Object> handleHeartbeat(@Payload Map<String, String> payload) {
        String clientId = payload.get("clientId");

        if (clientId != null && !clientId.isEmpty()) {
            clientService.updateHeartbeat(clientId);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", System.currentTimeMillis());
        response.put("serverTime", LocalDateTime.now().toString());
        return response;
    }

    /**
     * 处理任务状态更新
     */
    @MessageMapping("/task-status")
    public void handleTaskStatusUpdate(@Payload Map<String, String> statusUpdate) {
        String taskId = statusUpdate.get("taskId");
        String status = statusUpdate.get("status");
        String clientId = statusUpdate.get("clientId");

        if (taskId != null && status != null && clientId != null) {
            try {
                PrintTaskStatus taskStatus = PrintTaskStatus.valueOf(status.toUpperCase());
                taskService.updateTaskStatus(taskId, taskStatus, clientId);
            } catch (Exception e) {
                log.error("更新任务状态失败: {} -> {}", taskId, status, e);
            }
        }
    }
}