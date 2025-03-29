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
@Table(name = "print_tasks")
public class PrintTask {
    @Id
    private String taskId;                 // 任务ID

    @Column(columnDefinition = "TEXT")
    private String content;                // 打印内容

    @Enumerated(EnumType.STRING)
    private PrintTaskStatus status;        // 任务状态

    private int retryCount;                // 重试次数
    private LocalDateTime createTime;      // 创建时间
    private String printerName;            // 打印机名称

    @Enumerated(EnumType.STRING)
    private PrintTaskPriority priority;    // 任务优先级

    private String merchantId;             // 商家ID
    private String assignedClientId;       // 分配的客户端ID
    private LocalDateTime printTime;       // 打印时间
    private LocalDateTime lastUpdateTime;  // 最后更新时间

    @Column(columnDefinition = "TEXT")
    private String errorMessage;           // 错误信息



    @Column(nullable = false)
    private Integer orderId;

    @Column(nullable = false)
    private String orderNo;


    @Column(nullable = false)
    private Integer storeId;


}