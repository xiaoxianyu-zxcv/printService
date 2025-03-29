package com.example.print.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "print_clients")
public class PrintClient {

    @Id
    private String clientId;

    @Column(nullable = false)
    private String clientName;

    @Column(nullable = false)
    private Integer merchantId;

    @Column(nullable = false)
    private Integer storeId;

    private String printerName;

    private String ipAddress;

    @Column(nullable = false)
    private Boolean online;

    private LocalDateTime lastActiveTime;

    @Column(nullable = false)
    private LocalDateTime createTime;

    @Column(nullable = false)
    private LocalDateTime updateTime;

    // 可以添加更多客户端相关的字段
    private String version;

    private String osInfo;

    // 构造函数、getter和setter方法由Lombok自动生成
}