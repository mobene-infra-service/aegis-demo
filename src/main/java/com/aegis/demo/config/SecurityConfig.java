package com.aegis.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 基础配置
 * 
 * 由于我们手动实现了 OAuth2 流程，不再需要 Spring Security OAuth2 Client 的自动配置。
 * 这里只保留基础的安全配置，主要用于：
 * 1. 控制哪些端点需要身份验证
 * 2. 配置 CSRF 保护
 * 3. 配置静态资源访问
 * 
 * 注意：
 * - 我们不再依赖 Spring Security 的自动登录/登出流程
 * - 身份验证完全由我们的 OAuth2Service 和控制器处理
 * - 这种方式让开发者能够完全控制认证流程的每一个步骤
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 配置安全过滤器链
     * 
     * 这个配置比使用 OAuth2 Client 时要简单得多，因为：
     * - 不需要配置 OAuth2 客户端注册信息
     * - 不需要配置自动登录/登出处理器
     * - 不需要处理令牌存储和刷新
     * 
     * 所有这些功能都在我们的自定义服务中实现
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 配置请求授权规则
            .authorizeHttpRequests(authz -> authz
                // 允许匿名访问的端点
                .requestMatchers("/", "/login", "/auth/callback").permitAll()
                // 用户页面 - 由于我们手动管理认证，也需要允许匿名访问
                // 实际的认证检查在控制器中进行
                .requestMatchers("/user", "/logout").permitAll()
                // 静态资源（CSS、JS、图片等）
                .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                // Keycloak 会话下线端点（如果需要的话）
                .requestMatchers("/k_logout").permitAll()
                // 其他请求允许访问（因为我们手动管理认证）
                .anyRequest().permitAll()
            )
            
            // 禁用默认的登录页面，因为我们有自己的登录流程
            .formLogin(form -> form.disable())
            
            // 禁用默认的登出处理，因为我们有自己的登出流程
            .logout(logout -> logout.disable())
            
            // 配置 CSRF 保护
            .csrf(csrf -> csrf
                // 对于 Keycloak 回调端点，可能需要禁用 CSRF（取决于具体配置）
                .ignoringRequestMatchers("/k_logout")
                // 注意：在生产环境中，应该仔细评估 CSRF 保护的配置
            );
        
        return http.build();
    }
}
