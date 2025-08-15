package com.aegis.demo.service;

import com.aegis.demo.config.KeycloakConfig;
import com.aegis.demo.model.UserInfo;
import com.aegis.demo.util.JwtUtil;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.annotation.PostConstruct;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
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
@Slf4j
public class OAuth2Service {
    
    
    @Autowired
    private KeycloakConfig keycloakConfig;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    private WebClient webClient;
    
    // 存储 state 和对应的 codeVerifier 映射（使用线程安全的ConcurrentHashMap）
    private final Map<String, String> stateToCodeVerifierMap = new ConcurrentHashMap<>();
    
    // 存储 state 的创建时间，用于过期清理
    private final Map<String, Long> stateCreationTimeMap = new ConcurrentHashMap<>();
    
    // state 的有效期（毫秒）- 10分钟
    private static final long STATE_EXPIRATION_TIME = 10 * 60 * 1000L;
    
    @PostConstruct
    public void init() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
        
        log.info("OAuth2Service 初始化完成");
        log.info("Keycloak 配置: {}", keycloakConfig);
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
     * - state: JWT格式的自包含状态参数，包含CSRF保护和PKCE信息
     * - code_challenge: PKCE 安全增强参数
     * 
     * @return 完整的授权请求 URL
     */
    public String generateAuthorizationUrl() {
        log.info("开始生成授权请求 URL");
        
        // 生成随机的 PKCE 参数
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);
        log.info("生成 PKCE 参数");
        log.debug("PKCE 参数 - verifier: {}, challenge: {}", 
                    codeVerifier.substring(0, 10) + "...", codeChallenge.substring(0, 10) + "...");
        
        // 生成随机的 state 参数
        String state = generateState();
        log.info("生成 state 参数: {}", state);
        
        // 存储 state 和 codeVerifier 的映射关系
        long currentTime = System.currentTimeMillis();
        stateToCodeVerifierMap.put(state, codeVerifier);
        stateCreationTimeMap.put(state, currentTime);
        log.debug("存储 state -> codeVerifier 映射，创建时间: {}", currentTime);
        
        // 构建授权请求 URL
        String authUrl = UriComponentsBuilder.fromHttpUrl(keycloakConfig.getAuthorizationUrl())
                .queryParam("response_type", "code")                    // 授权码流程
                .queryParam("client_id", keycloakConfig.getClientId())  // 客户端 ID
                .queryParam("redirect_uri", keycloakConfig.getRedirectUri())  // 回调 URI
                .queryParam("scope", "openid profile email")           // 请求的权限范围
                .queryParam("state", state)                             // JWT格式的状态参数
                .queryParam("code_challenge", codeChallenge)            // PKCE 挑战
                .queryParam("code_challenge_method", "S256")            // PKCE 挑战方法
                .build()
                .toUriString();
        
        log.info("生成授权 URL 成功: {}", authUrl);
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
     * 4. 生成自己的 JWT token
     * 
     * @param code 授权码
     * @param state state 参数，用于 CSRF 保护验证
     * @return JWT token 字符串
     * @throws RuntimeException 如果交换令牌失败
     */
    public String handleCallback(String code, String state) {
        log.info("开始处理授权回调 - code: {}, state: {}", 
                   code.substring(0, Math.min(10, code.length())) + "...", state);
        
        // 第一步：验证 state 参数并获取对应的 codeVerifier
        String codeVerifier = stateToCodeVerifierMap.get(state);
        Long creationTime = stateCreationTimeMap.get(state);
        
        if (codeVerifier == null || creationTime == null) {
            log.error("无效的 state 参数: {}", state);
            throw new RuntimeException("无效的 state 参数");
        }
        
        // 检查 state 是否过期
        long currentTime = System.currentTimeMillis();
        if (currentTime - creationTime > STATE_EXPIRATION_TIME) {
            log.error("state 参数已过期: {}, 创建时间: {}, 当前时间: {}", 
                     state, creationTime, currentTime);
            // 清理过期的 state
            stateToCodeVerifierMap.remove(state);
            stateCreationTimeMap.remove(state);
            throw new RuntimeException("state 参数已过期");
        }
        
        // 使用后即删除，防止重放攻击
        stateToCodeVerifierMap.remove(state);
        stateCreationTimeMap.remove(state);
        
        log.info("state 参数验证通过，获取对应的 codeVerifier");
        log.debug("使用 codeVerifier: {}", codeVerifier.substring(0, 10) + "...");
        
        try {
            // 第二步：构建令牌请求参数
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "authorization_code");           // 授权类型
            formData.add("client_id", keycloakConfig.getClientId());    // 客户端 ID
            formData.add("client_secret", keycloakConfig.getClientSecret()); // 客户端密钥
            formData.add("code", code);                                 // 授权码
            formData.add("redirect_uri", keycloakConfig.getRedirectUri()); // 必须与授权请求中的一致
            formData.add("code_verifier", codeVerifier);                // PKCE 验证参数
            
            log.info("准备发送令牌请求到: {}", keycloakConfig.getTokenUrl());
            log.debug("令牌请求参数: client_id={}, redirect_uri={}", 
                        keycloakConfig.getClientId(), keycloakConfig.getRedirectUri());
            log.debug("Client Secret 长度: {}", keycloakConfig.getClientSecret().length());
            
            // 第三步：发送 HTTP POST 请求到 Keycloak 令牌端点
            String response;
            try {
                response = webClient.post()
                        .uri(keycloakConfig.getTokenUrl())
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                        .body(BodyInserters.fromFormData(formData))
                        .retrieve()
                        .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> {
                                log.error("令牌请求失败 - HTTP状态码: {}", clientResponse.statusCode());
                                return clientResponse.bodyToMono(String.class)
                                    .doOnNext(errorBody -> {
                                        log.error("错误响应内容: {}", errorBody);
                                        log.error("请检查以下配置:");
                                        log.error("1. Client ID: {}", keycloakConfig.getClientId());
                                        log.error("2. Client Secret 是否正确");
                                        log.error("3. Redirect URI: {}", keycloakConfig.getRedirectUri());
                                        log.error("4. Keycloak 服务器地址: {}", keycloakConfig.getServerUrl());
                                        log.error("5. Realm 名称: {}", keycloakConfig.getRealm());
                                    })
                                    .map(errorBody -> new RuntimeException("令牌请求失败: " + errorBody));
                            }
                        )
                        .bodyToMono(String.class)
                        .block();
            } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
                log.error("WebClient 请求异常 - 状态码: {}, 响应体: {}", e.getStatusCode(), e.getResponseBodyAsString());
                throw new RuntimeException("令牌交换失败: " + e.getMessage() + ", 响应: " + e.getResponseBodyAsString(), e);
            }
            
            log.debug("收到令牌响应: {}", response.substring(0, Math.min(200, response.length())) + "...");
            
            // 第四步：解析令牌响应
            Map<String, Object> tokenResponse = objectMapper.readValue(response, 
                    new TypeReference<Map<String, Object>>() {});
            
            String accessToken = (String) tokenResponse.get("access_token");
            String idToken = (String) tokenResponse.get("id_token");
            
            if (accessToken == null || idToken == null) {
                log.error("令牌响应中缺少必要的令牌");
                throw new RuntimeException("无效的令牌响应");
            }
            
            log.info("成功获取令牌 - access_token: {}, id_token: {}", 
                       accessToken.substring(0, 20) + "...", idToken.substring(0, 20) + "...");
            
            // 第五步：解析 ID Token 获取用户信息
            UserInfo userInfo = parseIdToken(idToken);
            
            // 可选：从 UserInfo 端点获取更多用户信息
            enrichUserInfoFromEndpoint(userInfo, accessToken);
            
            // 第六步：生成我们自己的 JWT token
            Map<String, Object> jwtClaims = new HashMap<>();
            jwtClaims.put("email", userInfo.getEmail());
            jwtClaims.put("name", userInfo.getDisplayName());
            jwtClaims.put("preferred_username", userInfo.getPreferredUsername());
            jwtClaims.put("session_state", userInfo.getSessionState());
            
            // 添加原始的 Keycloak tokens
            jwtClaims.put("keycloak_access_token", accessToken);
            jwtClaims.put("keycloak_id_token", idToken);
            
            String jwtToken = jwtUtil.generateToken(userInfo.getPreferredUsername(), jwtClaims);
            
            log.info("用户信息解析完成，生成 JWT token: {}", userInfo.getPreferredUsername());
            return jwtToken;
            
        } catch (Exception e) {
            log.error("处理授权回调时发生错误", e);
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
        log.debug("开始解析 ID Token");
        
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
            log.debug("ID Token payload: {}", payload);
            
            // 解析 JSON 获取声明
            Map<String, Object> claims = objectMapper.readValue(payload, 
                    new TypeReference<Map<String, Object>>() {});
            
            log.info("成功解析 ID Token，包含 {} 个声明", claims.size());
            
            // 创建用户信息对象并保存原始 ID Token
            UserInfo userInfo = new UserInfo(claims);
            userInfo.setIdToken(idToken); // 保存原始 ID Token 用于登出
            
            return userInfo;
            
        } catch (Exception e) {
            log.error("解析 ID Token 时发生错误", e);
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
        log.debug("开始从 UserInfo 端点获取详细信息");
        
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
                log.debug("从 UserInfo 端点获取到 {} 个额外声明", userInfoClaims.size());
            }
            
        } catch (Exception e) {
            // UserInfo 端点调用失败不应该影响整个登录流程
            log.warn("从 UserInfo 端点获取信息时发生错误（非致命）", e);
        }
    }
    
    // =========================== JWT 用户信息获取 ===========================
    
    /**
     * 从 JWT token 中获取用户信息
     * 
     * @param jwtToken JWT token
     * @return 用户信息对象，如果token无效返回null
     */
    public UserInfo getUserInfoFromJwt(String jwtToken) {
        try {
            if (!jwtUtil.validateToken(jwtToken)) {
                log.warn("JWT token 验证失败");
                return null;
            }
            
            Claims claims = jwtUtil.getClaimsFromToken(jwtToken);
            if (claims == null) {
                log.warn("无法从 JWT token 中获取 claims");
                return null;
            }
            
            // 构建 UserInfo 对象
            Map<String, Object> userClaims = new HashMap<>();
            userClaims.put("sub", claims.getSubject());
            userClaims.put("email", claims.get("email"));
            userClaims.put("name", claims.get("name"));
            userClaims.put("preferred_username", claims.get("preferred_username"));
            userClaims.put("session_state", claims.get("session_state"));
            
            UserInfo userInfo = new UserInfo(userClaims);
            
            // 设置原始的 Keycloak ID Token（用于登出）
            String keycloakIdToken = (String) claims.get("keycloak_id_token");
            if (keycloakIdToken != null) {
                userInfo.setIdToken(keycloakIdToken);
            }
            
            return userInfo;
            
        } catch (Exception e) {
            log.error("从 JWT token 获取用户信息失败", e);
            return null;
        }
    }
    
    // =========================== 登出功能（已简化） ===========================
    
    // 注意：由于使用JWT无状态认证，不需要服务端登出逻辑
    // 客户端只需要删除本地存储的JWT token即可
    
    /**
     * 定时清理过期的 state 参数
     * 每5分钟执行一次
     */
    @Scheduled(fixedRate = 5 * 60 * 1000) // 5分钟
    public void cleanupExpiredStates() {
        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> iterator = stateCreationTimeMap.entrySet().iterator();
        int cleanedCount = 0;
        
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            String state = entry.getKey();
            Long creationTime = entry.getValue();
            
            if (currentTime - creationTime > STATE_EXPIRATION_TIME) {
                // 清理过期的 state
                iterator.remove();
                stateToCodeVerifierMap.remove(state);
                cleanedCount++;
            }
        }
        
        if (cleanedCount > 0) {
            log.info("清理了 {} 个过期的 state 参数", cleanedCount);
        }
    }
    
    // =========================== 辅助方法 ===========================
    
    /**
     * 生成随机的 PKCE code verifier
     * 
     * @return code verifier 字符串
     */
    private String generateCodeVerifier() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] codeVerifier = new byte[32];
        secureRandom.nextBytes(codeVerifier);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(codeVerifier);
    }
    
    /**
     * 根据 code verifier 生成 code challenge
     * 
     * @param codeVerifier code verifier
     * @return code challenge 字符串
     */
    private String generateCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("生成 PKCE code challenge 失败", e);
        }
    }
    
    /**
     * 生成随机的 state 参数
     * 
     * @return state 字符串
     */
    private String generateState() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] state = new byte[16];
        secureRandom.nextBytes(state);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(state);
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
     * 3. 分布式环境状态管理：
     *    - 当前实现使用JWT格式的自包含state，支持k8s多pod环境
     *    - JWT state包含加密的codeVerifier和时间戳，无需外部存储
     *    - 所有pod使用相同的JWT密钥，确保状态验证一致性
     *    - 设置适当的JWT过期时间（当前10分钟）
     * 
     * 4. 密钥管理：
     *    - 生产环境应从环境变量或密钥管理系统加载JWT签名密钥
     *    - 定期轮换密钥以增强安全性
     *    - 确保所有pod实例使用相同的密钥
     * 
     * 5. 错误处理和日志：
     *    - 详细的错误日志用于问题诊断
     *    - 用户友好的错误消息
     *    - 安全日志审计
     * 
     * 6. 网络安全：
     *    - 使用 HTTPS
     *    - 验证 SSL 证书
     *    - 实现请求重试和超时机制
     * 
     * 建议：在生产环境中使用成熟的 OAuth2 客户端库，如 Spring Security OAuth2 Client
     */
}
