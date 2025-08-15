package com.aegis.demo.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.security.Key;
import java.util.Date;
import java.util.Map;

/**
 * JWT 工具类
 * 
 * 负责 JWT token 的生成、验证和解析功能
 * 
 * 主要功能：
 * 1. 生成 JWT token
 * 2. 验证 JWT token 有效性
 * 3. 从 token 中提取用户信息
 * 4. 检查 token 是否过期
 */
@Component
@Slf4j
public class JwtUtil {

    @Value("${jwt.secret:aegis-demo-jwt-secret-key-for-development-only-change-in-production}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400}") // 默认24小时
    private Long jwtExpiration;

    private Key key;

    @PostConstruct
    public void init() {
        // 使用 HMAC-SHA 算法的密钥
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        log.info("JWT 工具类初始化完成，token 有效期: {} 秒", jwtExpiration);
    }

    /**
     * 生成 JWT token
     * 
     * @param subject 主题（通常是用户ID或用户名）
     * @param claims 自定义声明
     * @return JWT token 字符串
     */
    public String generateToken(String subject, Map<String, Object> claims) {
        Date now = new Date();
        Date expirationDate = new Date(now.getTime() + jwtExpiration * 1000);

        JwtBuilder builder = Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(expirationDate)
                .signWith(key, SignatureAlgorithm.HS256);

        // 添加自定义声明
        if (claims != null && !claims.isEmpty()) {
            builder.addClaims(claims);
        }

        String token = builder.compact();
        log.debug("为用户 {} 生成 JWT token，过期时间: {}", subject, expirationDate);
        
        return token;
    }

    /**
     * 验证 JWT token 有效性
     * 
     * @param token JWT token
     * @return 如果token有效返回true，否则返回false
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (SecurityException e) {
            log.error("JWT 签名验证失败: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.error("JWT 格式无效: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("JWT 已过期: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("不支持的 JWT: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT 参数为空: {}", e.getMessage());
        } catch (Exception e) {
            log.error("JWT 验证失败: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 从 JWT token 中获取用户名
     * 
     * @param token JWT token
     * @return 用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null ? claims.getSubject() : null;
    }

    /**
     * 从 JWT token 中获取所有声明
     * 
     * @param token JWT token
     * @return Claims 对象
     */
    public Claims getClaimsFromToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            log.error("解析 JWT token 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 检查 token 是否即将过期（30分钟内）
     * 
     * @param token JWT token
     * @return 如果即将过期返回true
     */
    public boolean isTokenExpiringSoon(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            if (claims == null) {
                return true;
            }
            Date expiration = claims.getExpiration();
            Date now = new Date();
            long timeUntilExpiration = expiration.getTime() - now.getTime();
            return timeUntilExpiration < 30 * 60 * 1000; // 30分钟
        } catch (Exception e) {
            log.error("检查 token 过期状态失败: {}", e.getMessage());
            return true;
        }
    }

    /**
     * 获取 token 剩余有效时间（秒）
     * 
     * @param token JWT token
     * @return 剩余时间（秒），如果token无效返回0
     */
    public long getTokenRemainingTime(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            if (claims == null) {
                return 0;
            }
            Date expiration = claims.getExpiration();
            Date now = new Date();
            long remaining = (expiration.getTime() - now.getTime()) / 1000;
            return Math.max(0, remaining);
        } catch (Exception e) {
            log.error("获取 token 剩余时间失败: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 从请求头中提取 Bearer token
     * 
     * @param authHeader Authorization 头的值
     * @return 提取的 token，如果格式不正确返回null
     */
    public String extractTokenFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
