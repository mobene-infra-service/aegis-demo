package com.aegis.demo.service;

import com.aegis.demo.config.KeycloakConfig;
import com.aegis.demo.model.UserInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.annotation.PostConstruct;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OAuth2/OpenID Connect 手动实现服务
 * 
 * 这个服务类展示了如何手动实现完整的 OAuth2 授权码流程，不依赖 Spring Security OAuth2 Client。
 * 
 * 主要功能包括：
 * 1. 生成授权请求 URL
 * 2. 处理授权回调，交换授权码获取令牌
 * 3. 解析和验证 JWT 令牌
 * 4. 获取用户信息
 * 5. 处理令牌刷新
 * 6. 实现安全登出
 * 
 * 这种实现方式的教育价值：
 * - 展示 OAuth2 协议的底层工作原理
 * - 理解各个步骤之间的数据交换
 * - 学习如何处理安全相关的细节
 * 
 * 注意：生产环境建议使用 Spring Security OAuth2 Client 以获得更好的安全性和维护性
 */
@Service
public class OAuth2Service {
    
    private static final Logger logger = LoggerFactory.getLogger(OAuth2Service.class);
    
    @Autowired
    private KeycloakConfig keycloakConfig;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private WebClient webClient;
    
    // 用于存储 state 参数，防止 CSRF 攻击
    // 生产环境应使用 Redis 等分布式存储
    private final Map<String, String> stateStore = new ConcurrentHashMap<>();
    
    // PKCE (Proof Key for Code Exchange) 参数存储
    // 增强安全性，防止授权码拦截攻击
    private final Map<String, String> codeVerifierStore = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
        
        logger.info("OAuth2Service 初始化完成");
        logger.info("Keycloak 配置: {}", keycloakConfig);
    }
    
    // =========================== 第一步：生成授权请求 URL ===========================
    
    /**
     * 生成 OAuth2 授权请求 URL
     * 
     * 这是 OAuth2 授权码流程的第一步。应用程序将用户重定向到这个 URL，
     * 用户在 Keycloak 上完成身份验证后，会带着授权码返回到我们的应用。
     * 
     * URL 包含的关键参数：
     * - response_type=code: 表示我们要使用授权码流程
     * - client_id: 标识我们的应用程序
     * - redirect_uri: 用户授权后的回调地址
     * - scope: 请求的权限范围
     * - state: 防止 CSRF 攻击的随机字符串
     * - code_challenge: PKCE 安全增强参数
     * 
     * @return 完整的授权请求 URL
     */
    public String generateAuthorizationUrl() {
        logger.info("开始生成授权请求 URL");
        
        // 生成随机的 state 参数，用于防止 CSRF 攻击
        String state = generateRandomString(32);
        stateStore.put(state, "valid");
        logger.debug("生成 state 参数: {}", state);
        
        // 生成 PKCE 参数，增强安全性
        String codeVerifier = generateRandomString(64);
        String codeChallenge = generateCodeChallenge(codeVerifier);
        codeVerifierStore.put(state, codeVerifier);
        logger.debug("生成 PKCE 参数 - verifier: {}, challenge: {}", 
                    codeVerifier.substring(0, 10) + "...", codeChallenge.substring(0, 10) + "...");
        
        // 构建授权请求 URL
        String authUrl = UriComponentsBuilder.fromHttpUrl(keycloakConfig.getAuthorizationUrl())
                .queryParam("response_type", "code")                    // 授权码流程
                .queryParam("client_id", keycloakConfig.getClientId())  // 客户端 ID
                .queryParam("redirect_uri", keycloakConfig.getRedirectUri())  // 回调 URI
                .queryParam("scope", "openid profile email")           // 请求的权限范围
                .queryParam("state", state)                             // CSRF 保护
                .queryParam("code_challenge", codeChallenge)            // PKCE 挑战
                .queryParam("code_challenge_method", "S256")            // PKCE 挑战方法
                .build()
                .toUriString();
        
        logger.info("生成授权 URL 成功: {}", authUrl);
        return authUrl;
    }
    
    // =========================== 第二步：处理授权回调 ===========================
    
    /**
     * 处理授权回调，交换授权码获取访问令牌
     * 
     * 这是 OAuth2 授权码流程的第二步。当用户在 Keycloak 完成身份验证后，
     * 浏览器会重定向回我们的应用，并携带授权码。我们需要：
     * 1. 验证 state 参数，确保请求没有被篡改
     * 2. 使用授权码向 Keycloak 的令牌端点请求访问令牌
     * 3. 解析返回的令牌和用户信息
     * 
     * @param code 授权码
     * @param state state 参数，用于 CSRF 保护验证
     * @return 用户信息对象
     * @throws RuntimeException 如果交换令牌失败
     */
    public UserInfo handleCallback(String code, String state) {
        logger.info("开始处理授权回调 - code: {}, state: {}", 
                   code.substring(0, Math.min(10, code.length())) + "...", state);
        
        // 第一步：验证 state 参数，防止 CSRF 攻击
        if (!validateState(state)) {
            logger.error("State 参数验证失败: {}", state);
            throw new RuntimeException("无效的 state 参数，可能存在 CSRF 攻击");
        }
        
        // 获取对应的 code_verifier
        String codeVerifier = codeVerifierStore.get(state);
        if (codeVerifier == null) {
            logger.error("找不到对应的 code_verifier: {}", state);
            throw new RuntimeException("无效的会话状态");
        }
        
        try {
            // 第二步：构建令牌请求参数
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "authorization_code");           // 授权类型
            formData.add("client_id", keycloakConfig.getClientId());    // 客户端 ID
            formData.add("client_secret", keycloakConfig.getClientSecret()); // 客户端密钥
            formData.add("code", code);                                 // 授权码
            formData.add("redirect_uri", keycloakConfig.getRedirectUri()); // 必须与授权请求中的一致
            formData.add("code_verifier", codeVerifier);                // PKCE 验证参数
            
            logger.debug("准备发送令牌请求到: {}", keycloakConfig.getTokenUrl());
            
            // 第三步：发送 HTTP POST 请求到 Keycloak 令牌端点
            String response = webClient.post()
                    .uri(keycloakConfig.getTokenUrl())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            logger.debug("收到令牌响应: {}", response.substring(0, Math.min(200, response.length())) + "...");
            
            // 第四步：解析令牌响应
            Map<String, Object> tokenResponse = objectMapper.readValue(response, 
                    new TypeReference<Map<String, Object>>() {});
            
            String accessToken = (String) tokenResponse.get("access_token");
            String idToken = (String) tokenResponse.get("id_token");
            String refreshToken = (String) tokenResponse.get("refresh_token");
            
            if (accessToken == null || idToken == null) {
                logger.error("令牌响应中缺少必要的令牌");
                throw new RuntimeException("无效的令牌响应");
            }
            
            logger.info("成功获取令牌 - access_token: {}, id_token: {}", 
                       accessToken.substring(0, 20) + "...", idToken.substring(0, 20) + "...");
            
            // 第五步：解析 ID Token 获取用户信息
            UserInfo userInfo = parseIdToken(idToken);
            
            // 可选：从 UserInfo 端点获取更多用户信息
            enrichUserInfoFromEndpoint(userInfo, accessToken);
            
            // 清理临时存储的参数
            stateStore.remove(state);
            codeVerifierStore.remove(state);
            
            logger.info("用户信息解析完成: {}", userInfo);
            return userInfo;
            
        } catch (Exception e) {
            logger.error("处理授权回调时发生错误", e);
            throw new RuntimeException("令牌交换失败: " + e.getMessage(), e);
        }
    }
    
    // =========================== 第三步：解析和验证令牌 ===========================
    
    /**
     * 解析 ID Token 获取用户信息
     * 
     * ID Token 是一个 JWT (JSON Web Token)，包含了用户的身份信息。
     * 在生产环境中，我们应该：
     * 1. 验证令牌签名
     * 2. 检查令牌是否过期
     * 3. 验证 issuer 和 audience
     * 
     * 为了简化演示，这里只进行基础解析。
     * 
     * @param idToken ID Token 字符串
     * @return 解析后的用户信息
     */
    private UserInfo parseIdToken(String idToken) {
        logger.debug("开始解析 ID Token");
        
        try {
            // 注意：这里为了演示目的，跳过了令牌签名验证
            // 生产环境中必须验证签名以确保令牌的真实性和完整性
            
            // JWT 由三部分组成：header.payload.signature
            // 我们主要关心 payload 部分，它包含了用户声明 (claims)
            String[] parts = idToken.split("\\.");
            if (parts.length != 3) {
                throw new RuntimeException("无效的 JWT 格式");
            }
            
            // 解码 payload 部分（Base64 URL 编码）
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            logger.debug("ID Token payload: {}", payload);
            
            // 解析 JSON 获取声明
            Map<String, Object> claims = objectMapper.readValue(payload, 
                    new TypeReference<Map<String, Object>>() {});
            
            logger.info("成功解析 ID Token，包含 {} 个声明", claims.size());
            
            return new UserInfo(claims);
            
        } catch (Exception e) {
            logger.error("解析 ID Token 时发生错误", e);
            throw new RuntimeException("ID Token 解析失败: " + e.getMessage(), e);
        }
    }
    
    // =========================== 第四步：获取用户详细信息 ===========================
    
    /**
     * 从 UserInfo 端点获取更多用户信息
     * 
     * 虽然 ID Token 包含了基本的用户信息，但 UserInfo 端点可能包含更多详细信息。
     * 这一步是可选的，取决于应用程序的需求。
     * 
     * @param userInfo 现有的用户信息对象
     * @param accessToken 访问令牌
     */
    private void enrichUserInfoFromEndpoint(UserInfo userInfo, String accessToken) {
        logger.debug("开始从 UserInfo 端点获取详细信息");
        
        try {
            // 使用访问令牌调用 UserInfo 端点
            String response = webClient.get()
                    .uri(keycloakConfig.getUserInfoUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            if (response != null) {
                Map<String, Object> userInfoClaims = objectMapper.readValue(response, 
                        new TypeReference<Map<String, Object>>() {});
                
                // 合并来自 UserInfo 端点的信息
                // 这里可以根据需要更新或添加更多字段
                logger.debug("从 UserInfo 端点获取到 {} 个额外声明", userInfoClaims.size());
            }
            
        } catch (Exception e) {
            // UserInfo 端点调用失败不应该影响整个登录流程
            logger.warn("从 UserInfo 端点获取信息时发生错误（非致命）", e);
        }
    }
    
    // =========================== 登出功能 ===========================
    
    /**
     * 生成单点登出 URL
     * 
     * 单点登出允许用户从所有相关应用程序中登出。
     * 
     * @return 登出 URL
     */
    public String generateLogoutUrl() {
        String logoutUrl = UriComponentsBuilder.fromHttpUrl(keycloakConfig.getLogoutUrl())
                .queryParam("post_logout_redirect_uri", keycloakConfig.getPostLogoutRedirectUri())
                .build()
                .toUriString();
        
        logger.info("生成登出 URL: {}", logoutUrl);
        return logoutUrl;
    }
    
    // =========================== 辅助方法 ===========================
    
    /**
     * 验证 state 参数
     * 
     * @param state 要验证的 state 参数
     * @return true 如果 state 有效
     */
    private boolean validateState(String state) {
        if (state == null || state.trim().isEmpty()) {
            return false;
        }
        return stateStore.containsKey(state);
    }
    
    /**
     * 生成随机字符串
     * 
     * @param length 字符串长度
     * @return 随机字符串
     */
    private String generateRandomString(int length) {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    
    /**
     * 生成 PKCE code challenge
     * 
     * @param codeVerifier code verifier
     * @return code challenge
     */
    private String generateCodeChallenge(String codeVerifier) {
        try {
            byte[] bytes = codeVerifier.getBytes(StandardCharsets.UTF_8);
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("生成 code challenge 失败", e);
        }
    }
    
    // =========================== 生产环境注意事项 ===========================
    
    /*
     * 重要安全注意事项（生产环境必须实现）：
     * 
     * 1. JWT 签名验证：
     *    - 从 JWKS 端点获取公钥
     *    - 验证 JWT 签名确保令牌未被篡改
     *    - 验证 issuer、audience 等声明
     * 
     * 2. 令牌生命周期管理：
     *    - 实现令牌刷新机制
     *    - 安全存储 refresh token
     *    - 处理令牌过期和撤销
     * 
     * 3. 状态管理：
     *    - 使用分布式缓存（如 Redis）存储 state 和 PKCE 参数
     *    - 设置适当的过期时间
     *    - 实现状态清理机制
     * 
     * 4. 错误处理和日志：
     *    - 详细的错误日志用于问题诊断
     *    - 用户友好的错误消息
     *    - 安全日志审计
     * 
     * 5. 网络安全：
     *    - 使用 HTTPS
     *    - 验证 SSL 证书
     *    - 实现请求重试和超时机制
     * 
     * 建议：在生产环境中使用成熟的 OAuth2 客户端库，如 Spring Security OAuth2 Client
     */
}
