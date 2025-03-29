package com.example.print;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PrintServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(PrintServerApplication.class, args);
    }
}