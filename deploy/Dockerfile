# syntax=docker/dockerfile:1
###############################
# 第一阶段：使用 Maven 构建项目  #
###############################
FROM maven:3.9.6-eclipse-temurin-17 AS build

# 设置工作目录
WORKDIR /app



# 复制项目全部源码
COPY . .

# 打包，跳过测试
RUN mvn -B clean package -Dmaven.test.skip=true

####################################
# 第二阶段：仅保留运行所需的 JAR 包 #
####################################
FROM openjdk:17-jdk-bullseye

# 时区设置，避免容器时间不一致
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 指定端口
EXPOSE 80

# 拷贝构建产物
COPY --from=build /app/target/*.jar /app.jar

# 运行
ENTRYPOINT ["java", "-jar", "/app.jar"]
