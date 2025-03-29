package com.example.print.repository;

import com.example.print.model.PrintClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PrintClientRepository extends JpaRepository<PrintClient, String> {

    // 查询指定商户的在线客户端
    List<PrintClient> findByMerchantIdAndOnlineTrue(int merchantId);

    // 查询指定门店的在线客户端
    List<PrintClient> findByStoreIdAndOnlineTrue(int storeId);

    // 查询一段时间内未活跃的客户端
    List<PrintClient> findByOnlineTrueAndLastActiveTimeBefore(LocalDateTime threshold);

    // 查询所有在线的客户端
    List<PrintClient> findByOnlineTrue();

    // 按IP地址查询客户端
    List<PrintClient> findByIpAddress(String ipAddress);

    // 查询特定商户的客户端数量
    @Query("SELECT COUNT(c) FROM PrintClient c WHERE c.merchantId = :merchantId")
    long countByMerchant(@Param("merchantId") int merchantId);

    // 查询特定商户的在线客户端数量
    @Query("SELECT COUNT(c) FROM PrintClient c WHERE c.merchantId = :merchantId AND c.online = true")
    long countOnlineByMerchant(@Param("merchantId") int merchantId);

    List<PrintClient> findByMerchantIdAndOnlineTrue(String merchantId);

    // 根据商户ID查询客户端
    List<PrintClient> findByMerchantId(int merchantId);
}