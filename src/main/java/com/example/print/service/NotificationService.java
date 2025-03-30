package com.example.print.service;

import com.example.print.model.PrintTask;
import com.example.print.model.PrintTaskStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class NotificationService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * 向特定商户的所有客户端广播打印任务
     */
    public void broadcastToPrintersByMerchant(int merchantId, PrintTask task) {
        try {
            // 1. 向商户特定主题发送消息
            messagingTemplate.convertAndSend("/topic/merchant/" + merchantId + "/print-tasks", task);

            // 2. 同时兼容现有客户端，发送到通用主题
            messagingTemplate.convertAndSend("/topic/print-tasks", task);

            log.info("向商户 {} 广播打印任务: {}", merchantId, task.getTaskId());
        } catch (Exception e) {
            log.error("广播打印任务失败: {}", task.getTaskId(), e);
        }
    }


    /**
     * 向特定门店的所有客户端广播打印任务
     */
    public void broadcastToPrintersByStore(int storeId, PrintTask task) {
        try {
            String destination = "/topic/store/" + storeId + "/print-tasks";
            log.info("准备广播打印任务到主题: {}", destination);

            // 转为JSON先记录日志
            ObjectMapper mapper = new ObjectMapper();
            String jsonTask = mapper.writeValueAsString(task);
            log.info("打印任务JSON: {}", jsonTask.substring(0, Math.min(200, jsonTask.length())) + "...");

            // 发送消息
            messagingTemplate.convertAndSend(destination, task);
            log.info("已成功广播打印任务: {} 到主题: {}", task.getTaskId(), destination);
        } catch (Exception e) {
            log.error("广播打印任务失败: {}", task.getTaskId(), e);
        }
    }


    /**
     * 任务状态更新通知
     */
    public void notifyTaskStatusUpdate(PrintTask task) {
        try {
            // 创建状态更新消息
            Map<String, Object> statusUpdate = new HashMap<>();
            statusUpdate.put("taskId", task.getTaskId());
            statusUpdate.put("status", task.getStatus().name());
            statusUpdate.put("orderNo", task.getOrderNo());
            statusUpdate.put("updateTime", task.getLastUpdateTime().toString());

            // 1. 向店铺特定主题发送更新
            messagingTemplate.convertAndSend(
                    "/topic/store/" + task.getStoreId() + "/print-status",
                    statusUpdate);

            // 2. 向通用主题发送更新
            messagingTemplate.convertAndSend("/topic/print-status", statusUpdate);

            log.info("发送任务状态更新: {} -> {}", task.getTaskId(), task.getStatus());
        } catch (Exception e) {
            log.error("发送任务状态更新失败: {}", task.getTaskId(), e);
        }
    }

    /**
     * 发送系统通知
     */
    public void sendSystemNotification(String type, String message) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", type);
            notification.put("message", message);
            notification.put("timestamp", System.currentTimeMillis());

            messagingTemplate.convertAndSend("/topic/system-notifications", notification);
            log.info("发送系统通知: {} - {}", type, message);
        } catch (Exception e) {
            log.error("发送系统通知失败", e);
        }
    }

    /**
     * 向特定客户端发送消息
     */
    public void sendMessageToClient(String clientId, String type, Object data) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", type);
            message.put("data", data);
            message.put("timestamp", System.currentTimeMillis());

            messagingTemplate.convertAndSend("/topic/client/" + clientId, message);
            log.info("向客户端 {} 发送消息: {}", clientId, type);
        } catch (Exception e) {
            log.error("向客户端发送消息失败: {}", clientId, e);
        }
    }

    /**
     * 发送错误通知
     */
    public void sendErrorNotification(String clientId, String errorMessage) {
        try {
            Map<String, Object> error = new HashMap<>();
            error.put("type", "ERROR");
            error.put("message", errorMessage);
            error.put("timestamp", System.currentTimeMillis());

            if (clientId != null) {
                // 发送给特定客户端
                messagingTemplate.convertAndSend("/topic/client/" + clientId, error);
            } else {
                // 广播错误
                messagingTemplate.convertAndSend("/topic/print-errors", error);
            }

            log.info("发送错误通知: {}", errorMessage);
        } catch (Exception e) {
            log.error("发送错误通知失败", e);
        }
    }
}
