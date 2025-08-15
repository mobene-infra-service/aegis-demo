package com.aegis.demo.controller;


import com.aegis.demo.annotation.RequireAuth;
import com.aegis.demo.model.UserInfo;
import com.aegis.demo.service.OAuth2Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * REST API 控制器 - 处理 OAuth2 + JWT 认证的前后端分离架构
 * 
 * 提供 JSON API 接口：
 * 1. 提供授权URL接口
 * 2. 处理授权回调，返回 JWT token
 * 3. 提供用户信息查询接口
 * 4. 使用 @RequireAuth 注解保护需要认证的接口
 * 5. 通过 AOP 切面自动验证 JWT token
 * 
 * 优势：
 * - 前后端完全分离
 * - RESTful API 设计
 * - 使用 JWT token 进行无状态认证
 * - 支持多种前端框架
 */
@RestController
@RequestMapping("/api")
@Slf4j
public class HomeController {
    

    // 注入我们自定义的 OAuth2 服务
    @Resource
    private OAuth2Service oauth2Service;
    

    
    // 用于格式化 JSON 数据显示
    @Resource
    private ObjectMapper objectMapper;
    
    // =========================== 获取登录授权URL ===========================
    
    /**
     * 获取登录授权URL
     * 
     * 生成OAuth2授权请求URL，前端可以重定向用户到此URL进行登录
     * 
     * @return JSON响应，包含授权URL
     */
    @GetMapping("/auth/url")
    public ResponseEntity<Map<String, Object>> getAuthUrl() {
        log.info("获取登录授权URL");
        
        try {
            String authUrl = oauth2Service.generateAuthorizationUrl();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("authUrl", authUrl);
            response.put("message", "授权URL生成成功");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("生成授权URL失败", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "生成授权URL失败: " + e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    

    
    // =========================== 处理登录回调 - 授权码交换 ===========================

    /**
     * 处理 OAuth2 授权回调
     *
     * 这是 OAuth2 授权码流程的核心步骤。当用户在 Keycloak 完成身份验证后：
     * 1. Keycloak 重定向回这个端点，携带授权码和 state 参数
     * 2. 我们验证 state 参数确保请求未被篡改
     * 3. 使用授权码向 Keycloak 交换访问令牌
     * 4. 解析令牌获取用户信息
     * 5. 生成 JWT token 并返回JSON响应
     *
     * @param code 从 Keycloak 返回的授权码
     * @param state 防 CSRF 攻击的状态参数
     * @param error 如果授权失败，Keycloak 返回的错误信息
     * @return JSON响应，包含JWT token或错误信息
     */
    @GetMapping("/auth/callback")
    public ResponseEntity<Map<String, Object>> handleCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error) {

        log.info("收到授权回调 - code: {}, state: {}, error: {}",
                   code != null ? code.substring(0, Math.min(10, code.length())) + "..." : null,
                   state, error);

        Map<String, Object> response = new HashMap<>();

        // 检查是否有授权错误
        if (error != null) {
            log.error("授权失败: {}", error);
            response.put("success", false);
            response.put("error", "登录失败: " + error);
            return ResponseEntity.badRequest().body(response);
        }

        // 检查必需参数
        if (code == null || state == null) {
            log.error("缺少必需的回调参数 - code: {}, state: {}", code, state);
            response.put("success", false);
            response.put("error", "无效的回调参数");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            // 调用我们的 OAuth2 服务处理回调
            String jwtToken = oauth2Service.handleCallback(code, state);

            log.info("用户登录成功，生成 JWT token");

            response.put("success", true);
            response.put("token", jwtToken);
            response.put("message", "登录成功");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("处理授权回调时发生错误", e);
            response.put("success", false);
            response.put("error", "登录处理失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    // =========================== 用户信息API ===========================
    
    /**
     * 获取用户信息API
     * 
     * 返回已登录用户的详细信息JSON，包括：
     * - 基本信息（用户名、邮箱、姓名等）
     * - 令牌声明 (Claims) 数据
     * 
     * 这个方法使用 @RequireAuth 注解进行认证保护，
     * AOP 切面会自动验证 JWT token 并将用户信息设置到请求属性中
     * 
     * @param request HTTP请求，包含JWT token
     * @return JSON响应，包含用户信息
     */
    @RequireAuth
    @GetMapping("/user/info")
    public ResponseEntity<Map<String, Object>> getUserInfo(HttpServletRequest request) {
        log.info("获取用户信息API");
        
        // 从请求头中获取JWT token
        String authHeader = request.getHeader("Authorization");
        String jwtToken = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwtToken = authHeader.substring(7);
        } else {
            // 也可以从查询参数中获取
            jwtToken = request.getParameter("token");
        }
        
        Map<String, Object> response = new HashMap<>();
        
        if (jwtToken == null) {
            log.error("无法获取JWT token");
            response.put("success", false);
            response.put("error", "无法获取认证token");
            return ResponseEntity.status(401).body(response);
        }
        
        // 从 JWT token 中获取完整的用户信息
        UserInfo currentUser = oauth2Service.getUserInfoFromJwt(jwtToken);
        if (currentUser == null) {
            log.error("无法从JWT token获取用户信息");
            response.put("success", false);
            response.put("error", "无法获取用户信息");
            return ResponseEntity.status(401).body(response);
        }
        
        log.info("返回用户信息: {}", currentUser.getPreferredUsername());
        
        // 构建用户信息响应
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("username", currentUser.getPreferredUsername());
        userInfo.put("email", currentUser.getEmail());
        userInfo.put("fullName", currentUser.getDisplayName());
        userInfo.put("sessionState", currentUser.getSessionState());
        userInfo.put("claims", currentUser.getAllClaims());
        
        response.put("success", true);
        response.put("user", userInfo);
        response.put("message", "获取用户信息成功");
        
        return ResponseEntity.ok(response);
    }
    
    // =========================== 登出功能（已简化） ===========================
    
    /**
     * 处理登出请求API
     * 
     * 由于使用JWT无状态认证，服务端不需要处理登出逻辑，
     * 只需要返回成功响应，告知客户端可以清除本地存储的token
     * 
     * @param request HTTP请求
     * @return JSON响应，包含登出成功信息
     */
    @RequireAuth(allowAnonymous = true)
    @GetMapping("/auth/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {
        log.info("处理登出请求");
        
        Map<String, Object> response = new HashMap<>();
        
        // 尝试从请求中获取JWT token以获取用户信息（用于日志记录）
        String authHeader = request.getHeader("Authorization");
        String jwtToken = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwtToken = authHeader.substring(7);
        } else {
            jwtToken = request.getParameter("token");
        }
        
        String username = "匿名用户";
        boolean wasLoggedIn = false;
        
        // 如果有token，尝试获取用户信息
        if (jwtToken != null) {
            UserInfo currentUser = oauth2Service.getUserInfoFromJwt(jwtToken);
            if (currentUser != null) {
                username = currentUser.getPreferredUsername();
                wasLoggedIn = true;
                log.info("用户登出: {}", username);
            }
        }
        
        if (!wasLoggedIn) {
            log.info("匿名用户访问登出接口");
        }
        
        response.put("success", true);
        response.put("message", "登出成功，请清除客户端存储的JWT token");
        response.put("wasLoggedIn", wasLoggedIn);
        response.put("username", username);
        
        return ResponseEntity.ok(response);
    }

}
