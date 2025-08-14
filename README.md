# Aegis Demo - 手动实现 OAuth2/OIDC 协议教学项目

这是一个**手动实现 OAuth2/OpenID Connect 协议**的 Keycloak 集成教学演示项目。

## 📚 项目特色

**本项目的独特之处在于完全手动实现了 OAuth2 授权码流程**，而非使用 Spring Security OAuth2 Client 的黑盒式自动化配置。

### 🎯 教学价值
- ✅ **完整流程展示**：从授权请求到令牌交换的每个步骤都有详细实现
- ✅ **代码透明性**：所有 OAuth2 相关代码都可以直接查看和理解
- ✅ **安全机制演示**：包含 PKCE、state 参数等安全措施的具体实现
- ✅ **详细注释说明**：每个关键步骤都有详细的中文注释
- ✅ **错误处理展示**：展示各种异常情况的处理方式

### 🔧 技术实现
- ✅ **手动构建授权请求 URL**：展示 OAuth2 参数的构建过程
- ✅ **手动令牌交换**：使用 HTTP 客户端直接调用 Keycloak Token 端点
- ✅ **手动 JWT 解析**：展示如何解析 ID Token 获取用户信息
- ✅ **会话管理**：简单的内存会话管理实现
- ✅ **单点登出**：实现与 Keycloak 的单点登出集成

## 🛠 技术栈

- **JDK 17** - 基础运行环境
- **Spring Boot 3.1.5** - 应用框架
- **Spring Security 6** - 基础安全配置（非 OAuth2 自动配置）
- **Spring WebFlux** - HTTP 客户端用于调用 Keycloak API
- **Thymeleaf** - 模板引擎
- **Bootstrap 5** - 前端UI框架
- **JJWT** - JWT 解析库
- **Jackson** - JSON 处理
- **Keycloak** - 身份提供者
- **Maven** - 构建工具

## ⚠️ 重要说明

### 教学目的 vs 生产使用

本项目主要用于**教学和学习目的**，帮助开发者理解 OAuth2/OIDC 协议的工作原理。

**生产环境强烈推荐使用 Spring Security OAuth2 Client**，因为：
- 提供完整的 JWT 签名验证
- 自动令牌刷新和生命周期管理
- 完善的错误处理和重试机制
- 符合安全最佳实践
- 活跃的社区支持和持续更新

### 项目优势
- **学习价值高**：清楚展示每个 OAuth2 步骤的实现
- **代码可控**：完全可定制的认证流程
- **理解深入**：有助于理解 OAuth2 协议的底层机制

### 项目限制
- **简化实现**：跳过了 JWT 签名验证等安全检查
- **内存会话**：未实现分布式会话管理
- **错误处理**：错误处理相对简单
- **维护成本**：需要手动维护安全更新

## 📋 前置要求

1. **JDK 17** - 确保已安装并配置 JAVA_HOME
2. **Maven 3.6+** - 用于构建项目
3. **Docker** (可选) - 用于快速启动 Keycloak

## 🏃‍♂️ 快速开始

### 1. 启动 Keycloak 服务器

使用 Docker 快速启动 Keycloak（推荐）：

```bash
docker run -p 8090:8080 -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin quay.io/keycloak/keycloak:22.0.5 start-dev
```

### 2. 配置 Keycloak

详细的 Keycloak 配置步骤请参考：[KEYCLOAK-SETUP.md](./KEYCLOAK-SETUP.md)

### 3. 配置应用程序

编辑 `src/main/resources/application.yml` 文件，更新以下配置：

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          keycloak:
            client-id: aegis-demo
            client-secret: YOUR_CLIENT_SECRET_HERE  # 从 Keycloak 获取
        provider:
          keycloak:
            issuer-uri: http://localhost:8090/realms/your-realm-name  # 替换为你的 realm
```

### 4. 编译和运行应用程序

```bash
# 编译项目
mvn clean compile

# 运行应用程序
mvn spring-boot:run
```

或者构建 JAR 包后运行：

```bash
# 构建 JAR 包
mvn clean package

# 运行 JAR 包
java -jar target/aegis-demo-1.0.0.jar
```

### 5. 访问应用程序

打开浏览器访问：[http://127.0.0.1:8282](http://127.0.0.1:8282)

## 📱 应用程序功能展示

### 登录页面
- 展示手动实现的 OAuth2 登录入口
- 点击"开始 OAuth2 登录流程"按钮发起授权请求
- 包含教学说明和项目特点介绍

### 用户信息页面
- 显示从 JWT Token 解析的用户信息
- 展示完整的令牌 Claims 数据
- 显示会话信息和令牌剩余时间
- 详细的 OAuth2 流程实现说明
- 对比手动实现 vs 自动化实现的优缺点

## 🔧 项目结构

```
aegis-demo/
├── src/main/java/com/aegis/demo/
│   ├── AegisDemoApplication.java          # Spring Boot 主应用程序
│   ├── config/
│   │   ├── KeycloakConfig.java           # Keycloak 配置管理（新增）
│   │   └── SecurityConfig.java           # Spring Security 基础配置
│   ├── model/
│   │   └── UserInfo.java                 # 用户信息数据模型（新增）
│   ├── service/
│   │   └── OAuth2Service.java            # OAuth2 手动实现服务（核心）
│   └── controller/
│       └── HomeController.java           # 完整的登录流程控制器
├── src/main/resources/
│   ├── templates/
│   │   ├── index.html                    # 登录页面（已更新）
│   │   └── user.html                     # 用户信息页面（已更新）
│   ├── static/css/
│   │   └── style.css                     # 自定义样式
│   └── application.yml                   # 应用程序配置（已更新）
├── pom.xml                               # Maven 依赖配置（已更新）
├── KEYCLOAK-SETUP.md                     # Keycloak 配置指南（全新）
├── start.sh / start.bat                  # 启动脚本
└── README.md                             # 项目说明文档
```

### 核心文件说明

#### OAuth2Service.java - 核心实现
```java
// 手动实现的 OAuth2 流程包含：
- generateAuthorizationUrl()     // 生成授权请求 URL
- handleCallback()               // 处理授权回调
- parseIdToken()                // 解析 JWT Token
- generateLogoutUrl()           // 生成登出 URL
```

#### KeycloakConfig.java - 配置管理
```java
// 集中管理所有 Keycloak 配置：
- 服务器地址和 Realm 配置
- OAuth2 端点 URL 生成
- 客户端认证信息
- 重定向 URI 管理
```

#### UserInfo.java - 数据模型
```java
// 用户信息和令牌数据：
- OIDC 标准字段解析
- 令牌生命周期管理
- 会话状态跟踪
- 权限和角色信息
```

## 🔐 安全特性

1. **OAuth2 授权码流程**：使用标准的 OAuth2 Authorization Code Grant
2. **CSRF 保护**：默认启用 CSRF 保护（会话下线端点除外）
3. **会话管理**：安全的会话创建和销毁
4. **令牌验证**：自动验证 JWT 令牌的有效性
5. **权限控制**：基于角色的访问控制支持

## 🎯 测试步骤

1. 确保 Keycloak 服务器运行在 `http://localhost:8090`
2. 按照 [KEYCLOAK-SETUP.md](./KEYCLOAK-SETUP.md) 配置 Keycloak
3. 启动应用程序：`mvn spring-boot:run`
4. 访问 `http://127.0.0.1:8282`
5. 点击"开始 OAuth2 登录流程"
6. 使用测试用户登录（如：testuser/password123）
7. 验证用户信息页面正确显示
8. 测试登出功能

## 🐛 故障排除

### 常见问题

1. **端口占用**：确保 8282 和 8090 端口未被其他程序占用
2. **重定向错误**：检查 Keycloak Client 配置中的重定向 URI
3. **CORS 错误**：确保 Keycloak Client 配置中设置了正确的 Web origins
4. **连接超时**：检查 Keycloak 服务器是否正常运行

### 日志调试

应用程序配置了详细的调试日志，可以查看控制台输出来诊断 OAuth2 流程中的问题：

- 授权 URL 生成过程
- HTTP 请求和响应详情
- JWT 令牌解析过程
- 错误和异常信息

## 📖 相关文档

- [Keycloak 配置指南](KEYCLOAK-SETUP.md) - 详细的 Keycloak 服务器配置步骤
- [实现方式对比](IMPLEMENTATION-COMPARISON.md) - 手动实现 vs Spring OAuth2 Client 的详细对比
- [OAuth2 授权码流程详解](src/main/java/com/aegis/demo/service/OAuth2Service.java) - 查看代码中的详细注释

## 🎓 学习建议

1. **先理解理论**：阅读 OAuth2 和 OpenID Connect 规范
2. **运行项目**：按照配置指南设置 Keycloak 并运行项目
3. **阅读代码**：仔细阅读 `OAuth2Service` 中的实现和注释
4. **观察日志**：启动应用程序时观察详细的调试日志
5. **对比实现**：阅读对比文档了解两种实现方式的差异
6. **生产实践**：在实际项目中使用 Spring Security OAuth2 Client

## 📞 技术支持

如果遇到问题，请检查：
1. Java 版本是否为 JDK 17
2. Keycloak 服务器是否正确启动和配置
3. Keycloak Client 配置是否正确
4. 应用程序配置文件中的参数是否正确
5. 网络连接是否正常

## 📄 许可证

本项目仅用于演示和学习目的。

---

**⚠️ 重要提醒**：这是一个**教学演示项目**，主要用于理解 OAuth2 协议原理。生产环境强烈建议使用 Spring Security OAuth2 Client 以确保安全性和可维护性。

