package com.aegis.demo.aspect;

import com.aegis.demo.annotation.RequireAuth;
import com.aegis.demo.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;

/**
 * 认证切面
 * 
 * 使用 AOP 拦截标记了 @RequireAuth 注解的方法，
 * 验证 JWT token 并进行权限控制
 * 
 * 主要功能：
 * 1. 拦截带有 @RequireAuth 注解的方法
 * 2. 从请求头中提取 JWT token
 * 3. 验证 token 有效性
 * 4. 检查用户角色权限（如果需要）
 * 5. 根据验证结果决定是否允许访问
 */
@Aspect
@Component
@Slf4j
public class AuthAspect {

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 环绕通知：拦截标记了 @RequireAuth 注解的方法
     */
    @Around("@annotation(requireAuth) || @within(requireAuth)")
    public Object authenticate(ProceedingJoinPoint joinPoint, RequireAuth requireAuth) throws Throwable {
        
        // 获取当前请求
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            log.error("无法获取当前请求上下文");
            throw new RuntimeException("认证失败：无法获取请求上下文");
        }

        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = attributes.getResponse();

        // 如果允许匿名访问，直接通过
        if (requireAuth != null && requireAuth.allowAnonymous()) {
            log.debug("方法 {} 允许匿名访问，跳过认证", joinPoint.getSignature().toShortString());
            return joinPoint.proceed();
        }

        try {
            // 从请求头中提取 JWT token
            String authHeader = request.getHeader("Authorization");
            String token = jwtUtil.extractTokenFromHeader(authHeader);

            if (token == null) {
                // 如果请求头中没有token，检查查询参数
                token = request.getParameter("token");
            }

            if (token == null) {
                log.warn("访问受保护资源但未提供认证token: {}", request.getRequestURI());
                throw new RuntimeException("认证失败：缺少认证token");
            }

            // 验证 token 有效性
            if (!jwtUtil.validateToken(token)) {
                log.warn("无效的认证token访问受保护资源: {}", request.getRequestURI());
                throw new RuntimeException("认证失败：无效的认证token");
            }

            // 从 token 中获取用户信息
            Claims claims = jwtUtil.getClaimsFromToken(token);
            if (claims == null) {
                log.error("无法从token中解析用户信息");
                throw new RuntimeException("认证失败：token格式错误");
            }

            String username = claims.getSubject();
            log.debug("用户 {} 通过JWT认证访问: {}", username, joinPoint.getSignature().toShortString());

            // 检查角色权限（如果需要）
            if (requireAuth != null && requireAuth.roles().length > 0) {
                List<String> userRoles = getUserRoles(claims);
                boolean hasRequiredRole = Arrays.stream(requireAuth.roles())
                        .anyMatch(userRoles::contains);

                if (!hasRequiredRole) {
                    log.warn("用户 {} 缺少必要的角色权限: 需要 {}, 拥有 {}", 
                            username, Arrays.toString(requireAuth.roles()), userRoles);
                    throw new RuntimeException("权限不足：缺少必要的角色权限");
                }
                
                log.debug("用户 {} 角色权限验证通过", username);
            }

            // 将用户信息设置到请求属性中，供后续使用
            request.setAttribute("currentUser", username);
            request.setAttribute("userClaims", claims);

            // 执行目标方法
            return joinPoint.proceed();

        } catch (RuntimeException e) {
            // 认证失败时的处理
            log.error("认证失败: {}", e.getMessage());
            
            // 设置响应状态码和错误信息
            if (response != null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                try {
                    response.getWriter().write("{\"error\":\"" + e.getMessage() + "\",\"code\":401}");
                } catch (Exception ioException) {
                    log.error("写入错误响应失败", ioException);
                }
            }
            
            throw e;
        } catch (Exception e) {
            log.error("认证过程中发生未知错误", e);
            throw new RuntimeException("认证失败：系统错误", e);
        }
    }

    /**
     * 从 JWT claims 中提取用户角色
     * 
     * @param claims JWT claims
     * @return 用户角色列表
     */
    @SuppressWarnings("unchecked")
    private List<String> getUserRoles(Claims claims) {
        Object rolesObj = claims.get("roles");
        if (rolesObj instanceof List) {
            return (List<String>) rolesObj;
        } else if (rolesObj instanceof String) {
            return Arrays.asList(((String) rolesObj).split(","));
        }
        return Arrays.asList(); // 默认返回空角色列表
    }
}
