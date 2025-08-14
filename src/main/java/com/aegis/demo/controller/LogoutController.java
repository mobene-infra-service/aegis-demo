package com.aegis.demo.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/k_logout")
public class LogoutController {

    private static final Logger logger = LoggerFactory.getLogger(LogoutController.class);

    /**
     * 处理来自 Keycloak 的会话下线通知
     * 当管理员在 Keycloak 中主动下线用户会话时，会调用此端点
     */
    @PostMapping
    public String keycloakLogout(HttpServletRequest request) {
        logger.info("收到来自 Keycloak 的会话下线通知");
        
        try {
            HttpSession session = request.getSession(false);
            if (session != null) {
                logger.info("正在使会话无效: {}", session.getId());
                session.invalidate();
            }
            
            // 记录请求信息（用于调试）
            logger.debug("Keycloak logout request headers:");
            request.getHeaderNames().asIterator().forEachRemaining(headerName -> {
                logger.debug("{}: {}", headerName, request.getHeader(headerName));
            });
            
            return "OK";
            
        } catch (Exception e) {
            logger.error("处理 Keycloak 会话下线时发生错误", e);
            return "ERROR";
        }
    }
}
