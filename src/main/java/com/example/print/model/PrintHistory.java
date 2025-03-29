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
@Table(name = "print_history")
public class PrintHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String taskId;

    @Column(nullable = false)
    private String clientId;

    @Column(nullable = false)
    private Integer orderId;

    @Column(nullable = false)
    private String orderNo;

    @Column(nullable = false)
    private Integer merchantId;

    @Column(nullable = false)
    private Integer storeId;

    @Column(nullable = false)
    private LocalDateTime printTime;

    @Column(nullable = false)
    private String status; // SUCCESS, FAILED

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    // 可以添加更多字段来存储打印的详细信息
    private String printerName;

    private String ipAddress;

    // 构造函数、getter和setter方法由Lombok自动生成
}