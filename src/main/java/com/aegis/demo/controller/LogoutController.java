package com.aegis.demo.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/k_logout")
@Slf4j
public class LogoutController {


    /**
     * 处理来自 Keycloak 的会话下线通知
     * 当管理员在 Keycloak 中主动下线用户会话时，会调用此端点
     */
    @PostMapping
    public String keycloakLogout(HttpServletRequest request) {
        log.info("收到来自 Keycloak 的会话下线通知");
        
        try {
            HttpSession session = request.getSession(false);
            if (session != null) {
                log.info("正在使会话无效: {}", session.getId());
                session.invalidate();
            }
            
            // 记录请求信息（用于调试）
            log.debug("Keycloak logout request headers:");
            request.getHeaderNames().asIterator().forEachRemaining(headerName -> {
                log.debug("{}: {}", headerName, request.getHeader(headerName));
            });
            
            return "OK";
            
        } catch (Exception e) {
            log.error("处理 Keycloak 会话下线时发生错误", e);
            return "ERROR";
        }
    }
}
