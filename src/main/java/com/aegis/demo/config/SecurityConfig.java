package com.aegis.demo.config;

import org.springframework.context.annotation.Configuration;

/**
 * 配置类 - 移除了 Spring Security 依赖
 * 
 * 改为使用 AOP + JWT 的认证方式：
 * 1. 使用自定义注解标记需要认证的接口
 * 2. 通过 AOP 切面拦截并验证 JWT token
 * 3. 不再依赖 Spring Security 的过滤器链
 * 
 * 优势：
 * - 更轻量级，减少依赖
 * - 更灵活的认证控制
 * - 便于理解和维护
 */
@Configuration
public class SecurityConfig {
    // 已移除 Spring Security 配置，改为 AOP + JWT 方式
}
