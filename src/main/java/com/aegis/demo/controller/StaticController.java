package com.aegis.demo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 静态页面控制器
 * 
 * 负责提供单页面应用的HTML页面
 * 对于SPA应用，所有前端路由都重定向到index.html
 */
@Controller
public class StaticController {

    /**
     * 处理前端路由
     * 将所有非API路径重定向到index.html，让前端JavaScript处理路由
     */
    @GetMapping({"/", "/login", "/user", "/logout", "/callback", "/success","/auth/callback"})
    public String spa() {
        return "forward:/index.html";
    }
}
