package com.example.print.repository;

import com.example.print.model.PrintHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PrintHistoryRepository extends JpaRepository<PrintHistory, Integer> {

    // 查询任务的打印历史
    List<PrintHistory> findByTaskId(String taskId);

    // 查询客户端的打印历史
    List<PrintHistory> findByClientId(String clientId);

    // 查询订单的打印历史
    List<PrintHistory> findByOrderId(int orderId);

    // 查询订单号的打印历史
    List<PrintHistory> findByOrderNo(String orderNo);

    // 分页查询商户的打印历史
    Page<PrintHistory> findByMerchantIdOrderByPrintTimeDesc(int merchantId, Pageable pageable);

    // 查询时间范围内的打印历史
    List<PrintHistory> findByPrintTimeBetween(LocalDateTime start, LocalDateTime end);

    // 查询成功打印的历史记录
    List<PrintHistory> findByStatus(String status);

    // 查询商户当天的打印历史统计
    @Query("SELECT COUNT(h) FROM PrintHistory h WHERE h.merchantId = :merchantId AND DATE(h.printTime) = CURRENT_DATE")
    long countTodayHistoryByMerchant(@Param("merchantId") int merchantId);

    // 查询商户打印成功率
    @Query("SELECT COUNT(h) FROM PrintHistory h WHERE h.merchantId = :merchantId AND h.status = 'SUCCESS'")
    long countSuccessByMerchant(@Param("merchantId") int merchantId);
}