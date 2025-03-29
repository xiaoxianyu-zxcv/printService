package com.example.print.service;

import com.example.print.model.PrintClient;
import com.example.print.repository.PrintClientRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class PrintClientService {

    @Autowired
    private PrintClientRepository clientRepository;

    @Autowired
    private NotificationService notificationService;

    @Value("${print.client.heartbeat-timeout:2}")
    private int heartbeatTimeoutMinutes;

    /**
     * 注册客户端
     */
    @Transactional
    public PrintClient registerClient(PrintClient client) {
        // 检查客户端是否已存在
        Optional<PrintClient> existingClient = clientRepository.findById(client.getClientId());

        if (existingClient.isPresent()) {
            // 更新现有客户端
            PrintClient existing = existingClient.get();
            existing.setClientName(client.getClientName());
            existing.setMerchantId(client.getMerchantId());
            existing.setStoreId(client.getStoreId());

            if (client.getPrinterName() != null) {
                existing.setPrinterName(client.getPrinterName());
            }

            if (client.getIpAddress() != null) {
                existing.setIpAddress(client.getIpAddress());
            }

            existing.setOnline(true);
            existing.setLastActiveTime(LocalDateTime.now());
            existing.setUpdateTime(LocalDateTime.now());

            log.info("更新现有客户端: {}", client.getClientId());
            return clientRepository.save(existing);
        } else {
            // 创建新客户端
            client.setOnline(true);
            client.setLastActiveTime(LocalDateTime.now());
            client.setCreateTime(LocalDateTime.now());
            client.setUpdateTime(LocalDateTime.now());

            log.info("注册新客户端: {}", client.getClientId());
            return clientRepository.save(client);
        }
    }

    /**
     * 更新客户端心跳
     */
    @Transactional
    public PrintClient updateHeartbeat(String clientId) {
        Optional<PrintClient> optionalClient = clientRepository.findById(clientId);

        if (optionalClient.isPresent()) {
            PrintClient client = optionalClient.get();
            client.setLastActiveTime(LocalDateTime.now());
            client.setOnline(true);
            client.setUpdateTime(LocalDateTime.now());

            log.debug("更新客户端心跳: {}", clientId);
            return clientRepository.save(client);
        } else {
            log.warn("更新心跳失败，客户端不存在: {}", clientId);
            return null;
        }
    }

    /**
     * 查询指定商户的在线客户端
     */
    public List<PrintClient> findAvailableClientsByMerchant(Integer merchantId) {
        if (merchantId == null) {
            return clientRepository.findByOnlineTrue();
        } else {
            return clientRepository.findByMerchantIdAndOnlineTrue(merchantId);
        }
    }

    /**
     * 查询指定门店的在线客户端
     */
    public List<PrintClient> findAvailableClientsByStore(Integer storeId) {
        return clientRepository.findByStoreIdAndOnlineTrue(storeId);
    }

    /**
     * 检查客户端在线状态
     * 每分钟执行一次
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void checkClientsHealth() {
        log.debug("开始检查客户端健康状态");

        // 查找超过心跳超时时间未活跃的客户端
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(heartbeatTimeoutMinutes);
        List<PrintClient> inactiveClients = clientRepository.findByOnlineTrueAndLastActiveTimeBefore(threshold);

        if (!inactiveClients.isEmpty()) {
            log.info("发现 {} 个超时未心跳的客户端", inactiveClients.size());

            for (PrintClient client : inactiveClients) {
                // 标记为离线
                client.setOnline(false);
                client.setUpdateTime(LocalDateTime.now());
                clientRepository.save(client);

                log.info("客户端标记为离线: {}, 商户: {}", client.getClientId(), client.getMerchantId());

                // 通知系统
                notificationService.sendSystemNotification(
                        "CLIENT_OFFLINE",
                        "客户端离线: " + client.getClientName() + " (" + client.getClientId() + ")");
            }
        }
    }

    /**
     * 获取客户端详情
     */
    public Optional<PrintClient> getClient(String clientId) {
        return clientRepository.findById(clientId);
    }

    /**
     * 更新客户端信息
     */
    @Transactional
    public PrintClient updateClient(PrintClient client) {
        if (!clientRepository.existsById(client.getClientId())) {
            log.warn("更新失败，客户端不存在: {}", client.getClientId());
            return null;
        }

        client.setUpdateTime(LocalDateTime.now());
        return clientRepository.save(client);
    }

    /**
     * 获取商户的所有客户端
     */
    public List<PrintClient> getClientsByMerchant(int merchantId) {
        return clientRepository.findByMerchantId(merchantId);
    }
}