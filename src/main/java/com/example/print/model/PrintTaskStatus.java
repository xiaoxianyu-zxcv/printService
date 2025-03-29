package com.example.print.model;

public enum PrintTaskStatus {
    PENDING("待打印"),
    PRINTING("打印中"),
    COMPLETED("已完成"),
    FAILED("失败");

    private final String description;

    PrintTaskStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}