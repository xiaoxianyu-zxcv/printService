package com.example.print.repository;

import com.example.print.model.PrintTask;
import com.example.print.model.PrintTaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PrintTaskRepository extends JpaRepository<PrintTask, String> {
    // 按订单ID查询任务数量
    long countByOrderId(int orderId);

    // 按订单号查询任务
    List<PrintTask> findByOrderNo(String orderNo);

    // 按商户ID查询任务
    List<PrintTask> findByMerchantId(int merchantId);

    // 按状态查询任务
    List<PrintTask> findByStatus(PrintTaskStatus status);

    // 按商户ID和状态查询任务
    List<PrintTask> findByMerchantIdAndStatus(int merchantId, PrintTaskStatus status);

    // 按门店ID和状态查询任务
    List<PrintTask> findByStoreIdAndStatus(int storeId, PrintTaskStatus status);

    // 分页查询任务
    Page<PrintTask> findByMerchantIdOrderByCreateTimeDesc(int merchantId, Pageable pageable);

    // 查询指定时间范围内的任务
    List<PrintTask> findByCreateTimeBetween(LocalDateTime start, LocalDateTime end);

    // 查询长时间未完成的任务
    @Query("SELECT t FROM PrintTask t WHERE t.status = :status AND t.createTime < :threshold")
    List<PrintTask> findStuckTasks(@Param("status") PrintTaskStatus status, @Param("threshold") LocalDateTime threshold);

    // 查询特定商户当天的任务数
    @Query("SELECT COUNT(t) FROM PrintTask t WHERE t.merchantId = :merchantId AND DATE(t.createTime) = CURRENT_DATE")
    long countTodayTasksByMerchant(@Param("merchantId") int merchantId);

    List<PrintTask> findByStatusAndMerchantId(PrintTaskStatus status, String merchantId);
    List<PrintTask> findByAssignedClientId(String clientId);



}