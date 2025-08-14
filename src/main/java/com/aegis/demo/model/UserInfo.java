package com.aegis.demo.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 用户信息数据传输对象 (DTO)
 * 
 * 这个类用于存储从 Keycloak 获取的用户信息和令牌数据。
 * 包含了 OIDC 标准字段和 Keycloak 特定的用户属性。
 * 
 * 主要用途：
 * 1. 存储 ID Token 中解析出的用户声明 (Claims)
 * 2. 存储从 UserInfo 端点获取的用户详细信息
 * 3. 提供统一的用户信息访问接口
 */
public class UserInfo {
    
    // =========================== OIDC 标准字段 ===========================
    
    /**
     * 用户唯一标识符 (Subject)
     * OIDC 标准字段，在整个系统中唯一标识用户
     */
    @JsonProperty("sub")
    private String subject;
    
    /**
     * 用户首选用户名
     * 通常是用户的登录名
     */
    @JsonProperty("preferred_username")
    private String preferredUsername;
    
    /**
     * 用户邮箱地址
     */
    @JsonProperty("email")
    private String email;
    
    /**
     * 邮箱是否已验证
     */
    @JsonProperty("email_verified")
    private Boolean emailVerified;
    
    /**
     * 用户姓名
     */
    @JsonProperty("name")
    private String name;
    
    /**
     * 用户名字
     */
    @JsonProperty("given_name")
    private String givenName;
    
    /**
     * 用户姓氏
     */
    @JsonProperty("family_name")
    private String familyName;
    
    // =========================== 令牌相关信息 ===========================
    
    /**
     * 令牌颁发者
     */
    @JsonProperty("iss")
    private String issuer;
    
    /**
     * 令牌受众 (通常是客户端 ID)
     */
    @JsonProperty("aud")
    private String audience;
    
    /**
     * 令牌颁发时间
     */
    @JsonProperty("iat")
    private Long issuedAt;
    
    /**
     * 令牌过期时间
     */
    @JsonProperty("exp")
    private Long expiresAt;
    
    /**
     * 会话 ID
     * Keycloak 特定字段，用于会话管理
     */
    @JsonProperty("session_state")
    private String sessionState;
    
    // =========================== 权限和角色信息 ===========================
    
    /**
     * 用户角色列表
     * Keycloak 中的角色信息
     */
    private List<String> roles;
    
    /**
     * 用户权限列表
     */
    private List<String> permissions;
    
    /**
     * 资源访问权限
     * 包含不同资源（客户端）的角色映射
     */
    @JsonProperty("resource_access")
    private Map<String, Object> resourceAccess;
    
    /**
     * Realm 访问权限
     * 包含 Realm 级别的角色
     */
    @JsonProperty("realm_access")
    private Map<String, Object> realmAccess;
    
    // =========================== 扩展信息 ===========================
    
    /**
     * 所有原始声明数据
     * 存储从令牌中解析出的所有声明，便于调试和扩展
     */
    private Map<String, Object> allClaims;
    
    // =========================== 构造方法 ===========================
    
    /**
     * 默认构造方法
     */
    public UserInfo() {
    }
    
    /**
     * 从声明 Map 构造用户信息对象
     * 
     * @param claims 从 JWT 令牌或 UserInfo 端点获取的声明
     */
    public UserInfo(Map<String, Object> claims) {
        this.allClaims = claims;
        
        // 解析标准 OIDC 字段
        this.subject = (String) claims.get("sub");
        this.preferredUsername = (String) claims.get("preferred_username");
        this.email = (String) claims.get("email");
        this.emailVerified = (Boolean) claims.get("email_verified");
        this.name = (String) claims.get("name");
        this.givenName = (String) claims.get("given_name");
        this.familyName = (String) claims.get("family_name");
        
        // 解析令牌相关字段
        this.issuer = (String) claims.get("iss");
        this.audience = (String) claims.get("aud");
        this.sessionState = (String) claims.get("session_state");
        
        // 处理时间戳
        if (claims.get("iat") instanceof Number) {
            this.issuedAt = ((Number) claims.get("iat")).longValue();
        }
        if (claims.get("exp") instanceof Number) {
            this.expiresAt = ((Number) claims.get("exp")).longValue();
        }
        
        // 解析权限相关字段
        this.resourceAccess = (Map<String, Object>) claims.get("resource_access");
        this.realmAccess = (Map<String, Object>) claims.get("realm_access");
    }
    
    // =========================== 便利方法 ===========================
    
    /**
     * 获取显示名称
     * 优先使用 name，然后是 preferredUsername，最后是 subject
     * 
     * @return 用户显示名称
     */
    public String getDisplayName() {
        if (name != null && !name.trim().isEmpty()) {
            return name;
        }
        if (preferredUsername != null && !preferredUsername.trim().isEmpty()) {
            return preferredUsername;
        }
        return subject;
    }
    
    /**
     * 检查令牌是否已过期
     * 
     * @return true 如果令牌已过期
     */
    public boolean isTokenExpired() {
        if (expiresAt == null) {
            return false;
        }
        return Instant.now().getEpochSecond() >= expiresAt;
    }
    
    /**
     * 获取令牌剩余有效时间（秒）
     * 
     * @return 剩余有效时间，如果已过期返回 0
     */
    public long getTokenRemainingTime() {
        if (expiresAt == null) {
            return 0;
        }
        long remaining = expiresAt - Instant.now().getEpochSecond();
        return Math.max(0, remaining);
    }
    
    // =========================== Getter/Setter 方法 ===========================
    
    public String getSubject() {
        return subject;
    }
    
    public void setSubject(String subject) {
        this.subject = subject;
    }
    
    public String getPreferredUsername() {
        return preferredUsername;
    }
    
    public void setPreferredUsername(String preferredUsername) {
        this.preferredUsername = preferredUsername;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public Boolean getEmailVerified() {
        return emailVerified;
    }
    
    public void setEmailVerified(Boolean emailVerified) {
        this.emailVerified = emailVerified;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getGivenName() {
        return givenName;
    }
    
    public void setGivenName(String givenName) {
        this.givenName = givenName;
    }
    
    public String getFamilyName() {
        return familyName;
    }
    
    public void setFamilyName(String familyName) {
        this.familyName = familyName;
    }
    
    public String getIssuer() {
        return issuer;
    }
    
    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
    
    public String getAudience() {
        return audience;
    }
    
    public void setAudience(String audience) {
        this.audience = audience;
    }
    
    public Long getIssuedAt() {
        return issuedAt;
    }
    
    public void setIssuedAt(Long issuedAt) {
        this.issuedAt = issuedAt;
    }
    
    public Long getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }
    
    public String getSessionState() {
        return sessionState;
    }
    
    public void setSessionState(String sessionState) {
        this.sessionState = sessionState;
    }
    
    public List<String> getRoles() {
        return roles;
    }
    
    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
    
    public List<String> getPermissions() {
        return permissions;
    }
    
    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }
    
    public Map<String, Object> getResourceAccess() {
        return resourceAccess;
    }
    
    public void setResourceAccess(Map<String, Object> resourceAccess) {
        this.resourceAccess = resourceAccess;
    }
    
    public Map<String, Object> getRealmAccess() {
        return realmAccess;
    }
    
    public void setRealmAccess(Map<String, Object> realmAccess) {
        this.realmAccess = realmAccess;
    }
    
    public Map<String, Object> getAllClaims() {
        return allClaims;
    }
    
    public void setAllClaims(Map<String, Object> allClaims) {
        this.allClaims = allClaims;
    }
    
    @Override
    public String toString() {
        return "UserInfo{" +
                "subject='" + subject + '\'' +
                ", preferredUsername='" + preferredUsername + '\'' +
                ", email='" + email + '\'' +
                ", name='" + name + '\'' +
                ", isTokenExpired=" + isTokenExpired() +
                '}';
    }
}
