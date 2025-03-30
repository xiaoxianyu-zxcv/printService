package com.example.print.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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

            log.info("收到登录请求: username={}", username);

            try {
                // 第一步：只根据用户名查询用户
                String sql = "SELECT id, `name` as username, merchant_id, store_id, PASSWORD FROM tp_retail_manager WHERE mobile = ?";
                List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, username);

                if (!results.isEmpty()) {
                    Map<String, Object> user = results.get(0);
                    String storedPassword = (String) user.get("password");

                    // 加密输入的密码
                    String encryptedInputPassword = encryptPassword(password);
                    log.debug("加密后的输入密码: {}", encryptedInputPassword);
                    log.debug("数据库存储的密码: {}", storedPassword);

                    if (storedPassword.equals(encryptedInputPassword)) {
                        // 密码匹配，登录成功
                        Map<String, Object> response = new HashMap<>();
                        response.put("userId", user.get("id"));
                        response.put("username", user.get("username"));
                        response.put("merchantId", user.get("merchant_id"));
                        response.put("storeId", user.get("store_id"));

                        log.info("登录成功: userId={}", user.get("id"));
                        return ResponseEntity.ok(response);
                    } else {
                        // 密码不匹配
                        log.warn("登录失败: 密码不匹配");
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("用户名或密码错误");
                    }
                } else {
                    log.warn("登录失败: 用户不存在");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("用户名或密码错误");
                }
            } catch (Exception e) {
                log.error("登录失败", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("登录处理失败: " + e.getMessage());
            }
    }


    //@PostMapping("/login")
    //public ResponseEntity<?> login(@RequestBody Map<String, String> loginData) {
    //    String username = loginData.get("username");
    //    String password = loginData.get("password");
    //
    //    log.info("收到登录请求: username={}", username);
    //
    //    try {
    //        // 第一步：只根据用户名查询用户
    //        String sql = "SELECT id, username, merchant_id, store_id, password FROM tp_admin WHERE username = ?";
    //        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, username);
    //
    //        if (!results.isEmpty()) {
    //            Map<String, Object> user = results.get(0);
    //            String storedPassword = (String) user.get("password");
    //
    //            // 加密输入的密码
    //            String encryptedInputPassword = encryptPassword(password);
    //            log.debug("加密后的输入密码: {}", encryptedInputPassword);
    //            log.debug("数据库存储的密码: {}", storedPassword);
    //
    //            if (storedPassword.equals(encryptedInputPassword)) {
    //                // 密码匹配，登录成功
    //                Map<String, Object> response = new HashMap<>();
    //                response.put("userId", user.get("id"));
    //                response.put("username", user.get("username"));
    //                response.put("merchantId", user.get("merchant_id"));
    //                response.put("storeId", user.get("store_id"));
    //
    //                log.info("登录成功: userId={}", user.get("id"));
    //                return ResponseEntity.ok(response);
    //            } else {
    //                // 密码不匹配
    //                log.warn("登录失败: 密码不匹配");
    //                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("用户名或密码错误");
    //            }
    //        } else {
    //            log.warn("登录失败: 用户不存在");
    //            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("用户名或密码错误");
    //        }
    //    } catch (Exception e) {
    //        log.error("登录失败", e);
    //        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("登录处理失败: " + e.getMessage());
    //    }
    //}

    /**
     * 实现与PHP相同的密码加密算法
     * @param password 原始密码
     * @return 加密后的密码
     */
    private String encryptPassword(String password) {
        if (password == null || password.isEmpty()) {
            return "";
        }

        // 配置的盐值，与PHP配置中的pass_salt一致
        String salt = "77$$88&%99@*^66*11";

        try {
            // 第一步：对密码进行SHA1加密
            MessageDigest sha1Digest = MessageDigest.getInstance("SHA-1");
            byte[] sha1Hash = sha1Digest.digest(password.getBytes(StandardCharsets.UTF_8));
            String sha1Result = bytesToHex(sha1Hash);

            // 第二步：拼接SHA1结果和盐值
            String combined = sha1Result + salt;

            // 第三步：对拼接结果进行MD5加密
            MessageDigest md5Digest = MessageDigest.getInstance("MD5");
            byte[] md5Hash = md5Digest.digest(combined.getBytes(StandardCharsets.UTF_8));

            // 返回最终的32位小写MD5码
            return bytesToHex(md5Hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("加密密码失败", e);
            return "";
        }
    }

    /**
     * 辅助方法：将字节数组转换为十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @GetMapping("/test-encrypt")
    public String testEncrypt(@RequestParam String password) {
        return encryptPassword(password);
    }

}
