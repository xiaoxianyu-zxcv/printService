package com.example.print.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@Slf4j
public class UserController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> loginData) {
        String username = loginData.get("username");
        String password = loginData.get("password");

        try {
            // 简单示例，实际应用中应使用加密密码
            String sql = "SELECT id, username, merchant_id, store_id FROM tp_user WHERE username = ? AND password = ?";
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, username, password);

            if (!results.isEmpty()) {
                Map<String, Object> user = results.get(0);
                Map<String, Object> response = new HashMap<>();
                response.put("userId", user.get("id"));
                response.put("username", user.get("username"));
                response.put("merchantId", user.get("merchant_id"));
                response.put("storeId", user.get("store_id"));

                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("用户名或密码错误");
            }
        } catch (Exception e) {
            log.error("登录失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("登录处理失败");
        }
    }
}
