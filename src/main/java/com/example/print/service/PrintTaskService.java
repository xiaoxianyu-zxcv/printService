package com.example.print.service;

import com.example.print.model.PrintClient;
import com.example.print.model.PrintHistory;
import com.example.print.model.PrintTask;
import com.example.print.model.PrintTaskStatus;
import com.example.print.repository.PrintHistoryRepository;
import com.example.print.repository.PrintTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class PrintTaskService {

    @Autowired
    private PrintTaskRepository taskRepository;

    @Autowired
    private PrintHistoryRepository historyRepository;

    @Autowired
    private PrintClientService clientService;

    @Autowired
    private NotificationService notificationService;

    /**
     * 创建打印任务
     */
    @Transactional
    public PrintTask createTask(PrintTask task) {
        // 设置默认值
        if (task.getCreateTime() == null) {
            task.setCreateTime(LocalDateTime.now());
        }
        if (task.getLastUpdateTime() == null) {
            task.setLastUpdateTime(LocalDateTime.now());
        }
        if (task.getStatus() == null) {
            task.setStatus(PrintTaskStatus.PENDING);
        }

        log.info("创建打印任务: {}, 订单号: {}", task.getTaskId(), task.getOrderNo());
        return taskRepository.save(task);
    }

    /**
     * 检查是否已为此订单创建过打印任务
     */
    public boolean isOrderExists(int orderId) {
        return taskRepository.countByOrderId(orderId) > 0;
    }

    /**
     * 获取打印任务
     */
    public Optional<PrintTask> getTask(String taskId) {
        return taskRepository.findById(taskId);
    }

    /**
     * 获取待处理任务列表
     */
    public List<PrintTask> getPendingTasks() {
        return taskRepository.findByStatus(PrintTaskStatus.PENDING);
    }

    /**
     * 获取商户的待处理任务
     */
    public List<PrintTask> getPendingTasksByMerchant(int merchantId) {
        return taskRepository.findByMerchantIdAndStatus(merchantId, PrintTaskStatus.PENDING);
    }

    /**
     * 获取店铺的待处理任务
     */
    public List<PrintTask> getPendingTasksByStore(int storeId) {
        return taskRepository.findByStoreIdAndStatus(storeId, PrintTaskStatus.PENDING);
    }




    /**
     * 分页获取商户的任务历史
     */
    public Page<PrintTask> getTasksByMerchant(int merchantId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        return taskRepository.findByMerchantIdOrderByCreateTimeDesc(merchantId, pageable);
    }

    /**
     * 更新任务状态
     */
    @Transactional
    public PrintTask updateTaskStatus(String taskId, PrintTaskStatus status, String clientId) {
        Optional<PrintTask> taskOpt = taskRepository.findById(taskId);

        if (!taskOpt.isPresent()) {
            log.warn("找不到任务: {}", taskId);
            return null;
        }

        PrintTask task = taskOpt.get();

        // 先检查任务是否已经是完成状态，避免重复处理
        if (task.getStatus() == PrintTaskStatus.COMPLETED && status == PrintTaskStatus.COMPLETED) {
            log.info("任务已经是完成状态: {}", taskId);
            return task;
        }

        // 只有非失败状态的任务才能标记为完成
        if (status == PrintTaskStatus.COMPLETED && task.getStatus() != PrintTaskStatus.FAILED) {
            log.info("任务完成: {}, 客户端: {}", taskId, clientId);

            task.setStatus(status);
            task.setPrintTime(LocalDateTime.now());
            task.setLastUpdateTime(LocalDateTime.now());

            // 记录打印历史
            recordPrintHistory(task, clientId, "SUCCESS", null);

            // 保存任务
            taskRepository.save(task);

            // 通知其他客户端任务状态变更
            notificationService.notifyTaskStatusUpdate(task);

            return task;
        }
        // 处理失败状态
        else if (status == PrintTaskStatus.FAILED) {
            log.info("任务失败: {}, 客户端: {}", taskId, clientId);

            task.setStatus(status);
            task.setLastUpdateTime(LocalDateTime.now());
            task.setRetryCount(task.getRetryCount() + 1);

            // 记录打印历史
            recordPrintHistory(task, clientId, "FAILED", "打印失败");

            // 保存任务
            taskRepository.save(task);

            // 通知其他客户端任务状态变更
            notificationService.notifyTaskStatusUpdate(task);

            return task;
        }
        // 更新为打印中状态
        else if (status == PrintTaskStatus.PRINTING) {
            log.info("任务打印中: {}, 客户端: {}", taskId, clientId);

            task.setStatus(status);
            task.setLastUpdateTime(LocalDateTime.now());

            // 保存任务
            taskRepository.save(task);

            // 通知其他客户端任务状态变更
            notificationService.notifyTaskStatusUpdate(task);

            return task;
        }

        return task;
    }

    /**
     * 记录打印历史
     */
    private void recordPrintHistory(PrintTask task, String clientId, String status, String errorMessage) {
        PrintHistory history = new PrintHistory();
        history.setTaskId(task.getTaskId());
        history.setClientId(clientId);
        history.setOrderId(task.getOrderId());
        history.setOrderNo(task.getOrderNo());
        history.setMerchantId(Integer.valueOf(task.getMerchantId()));
        history.setStoreId(task.getStoreId());
        history.setPrintTime(LocalDateTime.now());
        history.setStatus(status);
        history.setErrorMessage(errorMessage);

        historyRepository.save(history);
        log.info("记录打印历史: 任务={}, 客户端={}, 状态={}", task.getTaskId(), clientId, status);
    }

    /**
     * 定时检查长时间未完成的任务
     * 每10分钟执行一次
     */
    @Scheduled(fixedRate = 600000)
    public void checkStuckTasks() {
        log.info("开始检查卡住的任务");

        // 查找30分钟前创建但仍处于PENDING或PRINTING状态的任务
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(30);

        List<PrintTask> stuckTasks = taskRepository.findStuckTasks(PrintTaskStatus.PRINTING, threshold);
        stuckTasks.addAll(taskRepository.findStuckTasks(PrintTaskStatus.PENDING, threshold));

        log.info("发现 {} 个卡住的任务", stuckTasks.size());

        for (PrintTask task : stuckTasks) {
            // 检查是否有活跃的客户端可以重新打印
            List<PrintClient> availableClients = clientService.findAvailableClientsByMerchant(Integer.valueOf(task.getMerchantId()));

            if (!availableClients.isEmpty()) {
                // 重置任务状态为待处理
                task.setStatus(PrintTaskStatus.PENDING);
                task.setLastUpdateTime(LocalDateTime.now());
                taskRepository.save(task);

                // 通知客户端重新打印
                notificationService.broadcastToPrintersByMerchant(Integer.parseInt(task.getMerchantId()), task);
                log.info("重新分发卡住的任务: {}", task.getTaskId());
            }
        }
    }

    /**
     * 删除旧的打印任务记录
     * 每天凌晨1点执行
     */
    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void cleanupOldTasks() {
        log.info("开始清理旧的打印任务");

        // 删除30天前的已完成任务
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);

        List<PrintTask> oldTasks = taskRepository.findByCreateTimeBetween(
                LocalDateTime.now().minusYears(1), threshold);

        int count = 0;
        for (PrintTask task : oldTasks) {
            // 只删除已完成的任务
            if (task.getStatus() == PrintTaskStatus.COMPLETED) {
                taskRepository.delete(task);
                count++;
            }
        }

        log.info("清理了 {} 个旧的打印任务", count);
    }
}
