# Keycloak 集成配置指南

本文档说明如何配置 Keycloak 以支持 Aegis Demo 应用程序的 OAuth2/OpenID Connect 集成。

## 📖 项目特色

**本项目采用手动实现 OAuth2/OIDC 协议**，而非使用 Spring Security OAuth2 Client 的自动化配置。这种实现方式具有以下特点：

### ✅ 教学优势
- **透明化流程**：每个 OAuth2 步骤都有详细的代码实现和注释
- **原理展示**：清楚展示授权请求、令牌交换、用户信息获取等过程
- **安全理解**：包含 PKCE、state 参数等安全机制的具体实现
- **可定制性**：完全可控的认证流程，便于根据需求调整

### ⚠️ 生产环境建议
- **推荐使用 Spring Security OAuth2 Client**：提供完整的安全功能、错误处理和维护支持
- **本项目主要用于学习和理解 OAuth2 协议原理**
- **如需生产使用，请参考文档末尾的 Spring OAuth2 Client 配置示例**

## 1. Keycloak 服务器设置

### 启动 Keycloak 服务器

使用 Docker 启动 Keycloak（推荐方式）：

```bash
docker run -p 8090:8080 -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin quay.io/keycloak/keycloak:22.0.5 start-dev
```

或者下载并启动独立版本：
1. 从 [Keycloak 官网](https://www.keycloak.org/downloads) 下载
2. 解压并运行：`bin/kc.sh start-dev`

## 2. 访问 Keycloak 管理控制台

1. 打开浏览器访问：`http://localhost:8090`
2. 使用管理员账户登录（admin/admin）

## 3. 创建 Realm

1. 点击左上角的 Realm 下拉菜单
2. 点击 "Create Realm"
3. 输入 Realm 名称：`aegis-realm`（或其他名称）
4. 点击 "Create"

## 4. 创建 Client

1. 在左侧菜单中点击 "Clients"
2. 点击 "Create client"
3. 填写以下信息：
   - **Client ID**: `aegis-demo`
   - **Name**: `Aegis Demo Application`
   - **Description**: `Spring Boot Demo Application`
4. 点击 "Next"
5. 配置 Client 设置：
   - **Client authentication**: 开启 (ON)
   - **Authorization**: 关闭 (OFF)
   - **Authentication flow**: 
     - ✅ Standard flow
     - ✅ Direct access grants
6. 点击 "Next"
7. 配置登录设置：
   - **Root URL**: `http://127.0.0.1:8282`
   - **Home URL**: `http://127.0.0.1:8282`
   - **Valid redirect URIs**: `http://127.0.0.1:8282/auth/callback`
   - **Valid post logout redirect URIs**: `http://127.0.0.1:8282`
   - **Web origins**: `http://127.0.0.1:8282`
   - **Admin URL**: `http://127.0.0.1:8282/k_logout` （可选）
8. 点击 "Save"

## 5. 获取 Client Secret

1. 进入刚创建的 Client 详情页面
2. 点击 "Credentials" 标签
3. 复制 "Client secret" 的值

## 6. 创建测试用户

1. 在左侧菜单中点击 "Users"
2. 点击 "Add user"
3. 填写用户信息：
   - **Username**: `testuser`
   - **Email**: `test@example.com`
   - **First name**: `Test`
   - **Last name**: `User`
   - **Email verified**: 开启 (ON)
4. 点击 "Create"
5. 设置用户密码：
   - 点击 "Credentials" 标签
   - 点击 "Set password"
   - 输入密码：`password123`
   - **Temporary**: 关闭 (OFF)
   - 点击 "Save"

## 7. 配置应用程序

更新 `src/main/resources/application.yml` 文件中的 Keycloak 配置：

```yaml
# 自定义 Keycloak 配置
keycloak:
  server:
    url: http://localhost:8090          # Keycloak 服务器地址
  realm: aegis-realm                    # Realm 名称
  client:
    id: aegis-demo                      # Client ID
    secret: YOUR_CLIENT_SECRET_HERE     # 步骤5中获取的 Client Secret

# 应用程序基础配置
app:
  base:
    url: http://localhost:8282          # 应用程序地址
```

## 8. 测试配置

1. 启动 Aegis Demo 应用程序：
   ```bash
   mvn spring-boot:run
   ```

2. 打开浏览器访问：`http://127.0.0.1:8282`

3. 点击 "开始 OAuth2 登录流程" 按钮

4. 使用创建的测试用户登录（testuser/password123）

5. 验证是否成功跳转到用户信息页面

## 9. OAuth2 流程观察

启动应用程序后，你可以通过日志观察完整的 OAuth2 流程：

### 步骤 1：生成授权请求
```
生成授权 URL: https://keycloak-server/realms/aegis-realm/protocol/openid-connect/auth?
  response_type=code&
  client_id=aegis-demo&
  redirect_uri=http://localhost:8282/auth/callback&
  scope=openid+profile+email&
  state=RANDOM_STATE&
  code_challenge=PKCE_CHALLENGE&
  code_challenge_method=S256
```

### 步骤 2：授权码交换
```
POST https://keycloak-server/realms/aegis-realm/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code&
client_id=aegis-demo&
client_secret=CLIENT_SECRET&
code=AUTHORIZATION_CODE&
redirect_uri=http://localhost:8282/auth/callback&
code_verifier=PKCE_VERIFIER
```

### 步骤 3：解析令牌
```
ID Token (JWT): eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9...
解析后的用户信息: {sub, preferred_username, email, name, ...}
```

## 10. 常见问题

### 1. 重定向 URI 不匹配错误
确保 Keycloak Client 配置中的 "Valid redirect URIs" 包含：
`http://127.0.0.1:8282/auth/callback`

### 2. CORS 错误
在 Keycloak Client 配置中设置 "Web origins" 为：`http://127.0.0.1:8282`

### 3. Client Secret 错误
检查应用程序配置文件中的 client secret 是否与 Keycloak 中的一致

### 4. 端口冲突
如果端口 8090 或 8282 被占用，请修改相应的端口配置

### 5. JWT 解析错误
检查日志中的详细错误信息，确保令牌格式正确

## 11. 生产环境注意事项

### 安全性要求
1. **JWT 签名验证**：生产环境必须验证 JWT 签名
2. **HTTPS 协议**：所有通信必须使用 HTTPS
3. **令牌生命周期管理**：实现令牌刷新和撤销机制
4. **状态持久化**：使用 Redis 等存储 state 和 PKCE 参数

### 推荐实现方式
```yaml
# Spring Security OAuth2 Client 配置示例（生产环境推荐）
spring:
  security:
    oauth2:
      client:
        registration:
          keycloak:
            client-id: aegis-demo
            client-secret: ${KEYCLOAK_CLIENT_SECRET}
            scope: openid,profile,email
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
        provider:
          keycloak:
            issuer-uri: https://your-keycloak-server/realms/your-realm
            user-name-attribute: preferred_username
```

### 使用 Spring OAuth2 Client 的优势
- **自动令牌管理**：自动处理令牌刷新和存储
- **完整的安全验证**：包括 JWT 签名验证、CSRF 保护等
- **错误处理**：完善的错误处理和重试机制
- **标准化实现**：符合 OAuth2 和 OIDC 标准
- **社区支持**：活跃的社区和丰富的文档

## 12. 项目对比

| 特性 | 手动实现（本项目） | Spring OAuth2 Client |
|------|------------------|----------------------|
| **学习价值** | ⭐⭐⭐⭐⭐ | ⭐⭐ |
| **代码透明度** | ⭐⭐⭐⭐⭐ | ⭐⭐ |
| **生产可用性** | ⭐⭐ | ⭐⭐⭐⭐⭐ |
| **安全性** | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **维护成本** | ⭐⭐ | ⭐⭐⭐⭐⭐ |
| **开发速度** | ⭐⭐ | ⭐⭐⭐⭐⭐ |

## 13. 总结

本项目通过手动实现 OAuth2/OIDC 协议，帮助开发者深入理解：
- OAuth2 授权码流程的每个步骤
- PKCE 和 state 参数的安全作用
- JWT 令牌的结构和内容
- 客户端认证和授权的机制

对于学习和理解 OAuth2 协议原理，这是一个很好的参考实现。但在实际生产环境中，强烈建议使用成熟的 OAuth2 客户端库，如 Spring Security OAuth2 Client，以确保安全性和可维护性。
