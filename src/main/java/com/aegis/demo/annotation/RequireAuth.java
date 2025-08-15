package com.aegis.demo.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义认证注解
 * 
 * 用于标记需要身份验证的方法或类
 * 
 * 使用方式：
 * 1. 在方法上使用：@RequireAuth
 * 2. 在类上使用：@RequireAuth（类中所有public方法都需要认证）
 * 
 * 示例：
 * @RequireAuth
 * public String getUserInfo() {
 *     // 需要认证才能访问的方法
 * }
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAuth {
    
    /**
     * 可选的角色要求
     * 默认为空，表示只需要登录即可
     * 
     * @return 需要的角色数组
     */
    String[] roles() default {};
    
    /**
     * 是否允许匿名访问
     * 默认为false，设置为true时此注解将被忽略
     * 
     * @return 是否允许匿名访问
     */
    boolean allowAnonymous() default false;
}
