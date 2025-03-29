package com.example.print.model;

public enum PrintTaskPriority {
    HIGH("高"),
    MEDIUM("中"),
    LOW("低");

    private final String description;

    PrintTaskPriority(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}