package com.aegis.demo.controller;

import com.aegis.demo.config.KeycloakConfig;
import com.aegis.demo.model.UserInfo;
import com.aegis.demo.service.OAuth2Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 主控制器 - 处理用户界面和 OAuth2 登录流程
 * 
 * 这个控制器展示了如何手动处理 OAuth2/OpenID Connect 登录流程的各个步骤：
 * 1. 展示登录页面
 * 2. 发起授权请求  
 * 3. 处理授权回调
 * 4. 展示用户信息
 * 5. 处理登出
 * 
 * 与使用 Spring Security OAuth2 Client 的自动化处理不同，这里的每个步骤都是手动实现的，
 * 便于开发者理解 OAuth2 协议的工作原理。
 */
@Controller
public class HomeController {
    
    private static final Logger logger = LoggerFactory.getLogger(HomeController.class);
    
    // 注入我们自定义的 OAuth2 服务
    @Autowired
    private OAuth2Service oauth2Service;
    
    // 注入 Keycloak 配置，用于获取基础 URL
    @Autowired
    private KeycloakConfig keycloakConfig;
    
    // 用于格式化 JSON 数据显示
    @Autowired
    private ObjectMapper objectMapper;
    
    // 简单的内存会话管理（生产环境应使用 Redis 等）
    private UserInfo currentUser = null;
    
    // =========================== 首页 - 登录入口 ===========================
    
    /**
     * 首页控制器
     * 
     * 根据用户登录状态显示不同的页面：
     * - 未登录：显示登录页面
     * - 已登录：重定向到用户信息页面
     * 
     * @param model Thymeleaf 模型对象
     * @return 视图名称
     */
    @GetMapping("/")
    public String home(Model model) {
        logger.info("访问首页，检查用户登录状态");
        
        if (currentUser != null) {
            logger.info("用户已登录，重定向到用户页面: {}", currentUser.getPreferredUsername());
            String userUrl = keycloakConfig.getAppBaseUrl() + "/user";
            logger.debug("重定向到用户页面: {}", userUrl);
            return "redirect:" + userUrl;
        }
        
        logger.info("用户未登录，显示登录页面");
        return "index";
    }
    
    // =========================== 发起登录 - OAuth2 授权请求 ===========================
    
    /**
     * 发起 OAuth2 登录流程
     * 
     * 这是 OAuth2 授权码流程的起点。当用户点击"登录"按钮时：
     * 1. 生成包含安全参数的授权请求 URL
     * 2. 重定向用户到 Keycloak 进行身份验证
     * 
     * 关键安全措施：
     * - state 参数：防止 CSRF 攻击
     * - PKCE：增强安全性，防止授权码拦截
     * - scope：限制请求的权限范围
     * 
     * @return 重定向到 Keycloak 授权端点
     */
    @GetMapping("/login")
    public String login() {
        logger.info("用户发起登录请求");
        
        try {
            // 调用我们的 OAuth2 服务生成授权 URL
            String authorizationUrl = oauth2Service.generateAuthorizationUrl();
            
            logger.info("重定向用户到 Keycloak 进行身份验证");
            logger.debug("授权 URL: {}", authorizationUrl);
            
            // 重定向到 Keycloak 登录页面
            return "redirect:" + authorizationUrl;
            
        } catch (Exception e) {
            logger.error("生成授权 URL 时发生错误", e);
            // 在实际应用中，应该显示用户友好的错误页面
            throw new RuntimeException("登录失败: " + e.getMessage(), e);
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
     * 5. 建立用户会话
     * 
     * 这个过程展示了以下重要概念：
     * - 授权码只能使用一次
     * - 客户端必须提供正确的 client_secret 进行身份验证
     * - PKCE 参数确保即使授权码被拦截也无法滥用
     * 
     * @param code 从 Keycloak 返回的授权码
     * @param state 防 CSRF 攻击的状态参数  
     * @param error 如果授权失败，Keycloak 返回的错误信息
     * @param model Thymeleaf 模型对象
     * @return 重定向到用户信息页面或错误页面
     */
    @GetMapping("/auth/callback")
    public String handleCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error,
            Model model) {
        
        logger.info("收到授权回调 - code: {}, state: {}, error: {}", 
                   code != null ? code.substring(0, Math.min(10, code.length())) + "..." : null, 
                   state, error);
        
        // 检查是否有授权错误
        if (error != null) {
            logger.error("授权失败: {}", error);
            model.addAttribute("error", "登录失败: " + error);
            return "index";
        }
        
        // 检查必需参数
        if (code == null || state == null) {
            logger.error("缺少必需的回调参数 - code: {}, state: {}", code, state);
            model.addAttribute("error", "无效的回调参数");
            return "index";
        }
        
        try {
            // 调用我们的 OAuth2 服务处理回调
            // 这个方法会：
            // 1. 验证 state 参数
            // 2. 使用授权码交换访问令牌
            // 3. 解析 ID Token 获取用户信息
            currentUser = oauth2Service.handleCallback(code, state);
            
            logger.info("用户登录成功: {}", currentUser.getPreferredUsername());
            
            // 重定向到用户信息页面（使用绝对URL）
            String userUrl = keycloakConfig.getAppBaseUrl() + "/user";
            logger.debug("登录成功，重定向到用户页面: {}", userUrl);
            return "redirect:" + userUrl;
            
        } catch (Exception e) {
            logger.error("处理授权回调时发生错误", e);
            model.addAttribute("error", "登录处理失败: " + e.getMessage());
            return "index";
        }
    }
    
    // =========================== 用户信息页面 ===========================
    
    /**
     * 用户信息页面
     * 
     * 显示已登录用户的详细信息，包括：
     * - 基本信息（用户名、邮箱、姓名等）
     * - 令牌声明 (Claims) 数据
     * - 会话信息
     * 
     * 这个页面展示了从 OAuth2/OIDC 流程中获取的各种用户数据，
     * 帮助开发者理解令牌中包含的信息类型和结构。
     * 
     * @param model Thymeleaf 模型对象
     * @return 用户信息页面视图
     */
    @GetMapping("/user")
    public String user(Model model) {
        logger.info("访问用户信息页面");
        
        // 检查用户是否已登录
        if (currentUser == null) {
            logger.warn("用户未登录，重定向到首页");
            String homeUrl = keycloakConfig.getAppBaseUrl();
            logger.debug("重定向到首页: {}", homeUrl);
            return "redirect:" + homeUrl;
        }
        
        // 检查令牌是否已过期
        if (currentUser.isTokenExpired()) {
            logger.warn("用户令牌已过期: {}", currentUser.getPreferredUsername());
            currentUser = null;  // 清除过期的用户信息
            model.addAttribute("error", "登录已过期，请重新登录");
            return "index";
        }
        
        logger.info("显示用户信息: {}", currentUser.getPreferredUsername());
        
        // 设置页面数据
        model.addAttribute("username", currentUser.getPreferredUsername());
        model.addAttribute("email", currentUser.getEmail());
        model.addAttribute("fullName", currentUser.getDisplayName());
        
        // 格式化 claims 数据用于显示
        String claimsString = "";
        try {
            if (currentUser.getAllClaims() != null) {
                claimsString = objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(currentUser.getAllClaims());
            }
        } catch (Exception e) {
            logger.warn("格式化 Claims 数据时发生错误", e);
            claimsString = "无法格式化 Claims 数据: " + e.getMessage();
        }
        
        model.addAttribute("claimsString", claimsString);
        model.addAttribute("tokenRemainingTime", currentUser.getTokenRemainingTime());
        model.addAttribute("sessionState", currentUser.getSessionState());
        
        return "user";
    }
    
    // =========================== 登出功能 ===========================
    
    /**
     * 处理 GET 登出请求 - 显示登出确认页面
     * 
     * @param model Thymeleaf 模型对象
     * @return 登出确认页面视图
     */
    @GetMapping("/logout")
    public String showLogout(Model model) {
        logger.info("显示登出确认页面");
        
        // 添加用户信息到模型（如果已登录）
        if (currentUser != null) {
            model.addAttribute("username", currentUser.getPreferredUsername());
            model.addAttribute("isLoggedIn", true);
        } else {
            model.addAttribute("isLoggedIn", false);
        }
        
        return "logout";
    }
    
    /**
     * 处理 POST 登出请求 - 执行实际登出操作
     * 
     * 实现单点登出功能：
     * 1. 清除本地用户会话
     * 2. 重定向到 Keycloak 进行全局登出
     * 
     * 这确保用户从所有相关应用程序中登出，而不仅仅是当前应用。
     * 
     * @return 重定向到 Keycloak 登出端点
     */
    @PostMapping("/logout")
    public String logout() {
        UserInfo userToLogout = currentUser; // 保存用户信息用于生成登出URL
        
        if (currentUser != null) {
            logger.info("用户登出: {}", currentUser.getPreferredUsername());
            currentUser = null;  // 清除本地会话
        } else {
            logger.info("匿名用户尝试登出");
        }
        
        try {
            // 生成 Keycloak 单点登出 URL，包含 id_token_hint 参数
            String logoutUrl = oauth2Service.generateLogoutUrl(userToLogout);
            
            logger.info("重定向到 Keycloak 进行单点登出");
            return "redirect:" + logoutUrl;
            
        } catch (Exception e) {
            logger.error("生成登出 URL 时发生错误", e);
            // 即使 SSO 登出失败，也要确保本地登出，重定向到首页（使用绝对URL）
            String homeUrl = keycloakConfig.getAppBaseUrl();
            logger.debug("登出异常处理，重定向到首页: {}", homeUrl);
            return "redirect:" + homeUrl;
        }
    }
}
