package com.aegis.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Keycloak 配置管理类
 * 
 * 这个类集中管理所有与 Keycloak 相关的配置信息，包括：
 * - 服务器地址和 realm 信息
 * - 客户端认证信息  
 * - OAuth2 端点 URL
 * - 重定向 URI 配置
 * 
 * 在生产环境中，建议将敏感信息（如 client_secret）存储在环境变量或加密配置中
 */
@Configuration
public class KeycloakConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(KeycloakConfig.class);

    // =========================== 基础配置 ===========================
    
    /**
     * Keycloak 服务器基础 URL
     * 例如: https://sso.example.com 或 http://localhost:8090
     */
    @Value("${keycloak.server.url:http://localhost:8090}")
    private String serverUrl;
    
    /**
     * Keycloak Realm 名称
     * Realm 是 Keycloak 中的顶级管理单元，包含用户、角色、客户端等
     */
    @Value("${keycloak.realm}")
    private String realm;
    
    /**
     * OAuth2 客户端 ID
     * 在 Keycloak 中注册的客户端标识符
     */
    @Value("${keycloak.client.id:aegis-demo}")
    private String clientId;
    
    /**
     * OAuth2 客户端密钥
     * 用于客户端身份验证，确保只有授权的应用可以交换授权码
     */
    @Value("${keycloak.client.secret}")
    private String clientSecret;
    
    /**
     * 应用程序基础 URL
     * 用于构建重定向 URI
     */
    @Value("${app.base.url:http://localhost:8282}")
    private String appBaseUrl;
    
    // =========================== OAuth2 端点 URLs ===========================
    
    /**
     * 获取 OAuth2 授权端点 URL
     * 用户将被重定向到此 URL 进行身份验证
     * 
     * @return 完整的授权端点 URL
     */
    public String getAuthorizationUrl() {
        return String.format("%s/realms/%s/protocol/openid-connect/auth", serverUrl, realm);
    }
    
    /**
     * 获取 OAuth2 令牌端点 URL  
     * 用于交换授权码获取访问令牌
     * 
     * @return 完整的令牌端点 URL
     */
    public String getTokenUrl() {
        return String.format("%s/realms/%s/protocol/openid-connect/token", serverUrl, realm);
    }
    
    /**
     * 获取用户信息端点 URL
     * 用于获取已认证用户的详细信息
     * 
     * @return 完整的用户信息端点 URL
     */
    public String getUserInfoUrl() {
        return String.format("%s/realms/%s/protocol/openid-connect/userinfo", serverUrl, realm);
    }
    
    /**
     * 获取 OIDC 配置端点 URL (Well-known 端点)
     * 包含 OIDC 提供者的所有配置信息，如端点 URL、支持的算法等
     * 
     * @return OIDC 配置端点 URL
     */
    public String getOidcConfigUrl() {
        return String.format("%s/realms/%s/.well-known/openid_configuration", serverUrl, realm);
    }
    
    /**
     * 获取登出端点 URL
     * 用于单点登出功能
     * 
     * @return 登出端点 URL
     */
    public String getLogoutUrl() {
        return String.format("%s/realms/%s/protocol/openid-connect/logout", serverUrl, realm);
    }
    
    /**
     * 获取 JWKS (JSON Web Key Set) 端点 URL
     * 用于获取验证 JWT 令牌所需的公钥
     * 
     * @return JWKS 端点 URL
     */
    public String getJwksUrl() {
        return String.format("%s/realms/%s/protocol/openid-connect/certs", serverUrl, realm);
    }
    
    // =========================== 重定向 URI 配置 ===========================
    
    /**
     * 获取 OAuth2 回调重定向 URI
     * Keycloak 在用户授权后将重定向到此 URI
     * 
     * @return 完整的回调重定向 URI
     */
    public String getRedirectUri() {
        String redirectUri = appBaseUrl + "/auth/callback";
        logger.debug("生成的重定向 URI: {}", redirectUri);
        return redirectUri;
    }
    
    /**
     * 获取登出后重定向 URI
     * 用户登出后将重定向到此 URI
     * 
     * @return 登出后重定向 URI
     */
    public String getPostLogoutRedirectUri() {
        return appBaseUrl;
    }
    
    // =========================== Getter 方法 ===========================
    
    public String getServerUrl() {
        return serverUrl;
    }
    
    public String getRealm() {
        return realm;
    }
    
    public String getClientId() {
        return clientId;
    }
    
    public String getClientSecret() {
        return clientSecret;
    }
    
    public String getAppBaseUrl() {
        return appBaseUrl;
    }
    
    // =========================== 辅助方法 ===========================
    
    /**
     * 检查配置是否完整
     * 
     * @return true 如果所有必需配置都已设置
     */
    public boolean isConfigurationComplete() {
        return serverUrl != null && !serverUrl.isEmpty() &&
               realm != null && !realm.isEmpty() &&
               clientId != null && !clientId.isEmpty() &&
               clientSecret != null && !clientSecret.isEmpty();
    }
    
    @Override
    public String toString() {
        return "KeycloakConfig{" +
                "serverUrl='" + serverUrl + '\'' +
                ", realm='" + realm + '\'' +
                ", clientId='" + clientId + '\'' +
                ", clientSecret='***'" +  // 不输出敏感信息
                ", appBaseUrl='" + appBaseUrl + '\'' +
                '}';
    }
}
