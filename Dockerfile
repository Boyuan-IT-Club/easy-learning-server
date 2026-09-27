# 应用镜像：构建与运行分阶段，服务器因此不需要预装 JDK 与 Maven。
#
# 本地开发一般用不到它——本地是「IDE 跑应用 + compose 起 MySQL」，见 docs/08-部署方式.md。
# 需要验证镜像时的用法：docker compose up -d --build

FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build
# 一次性复制再构建：不做依赖分层缓存，是为了少一个失败点（go-offline 对插件解析并不完全可靠）。
# 本地开发不走镜像构建，服务器构建也不频繁，这点缓存不值得换来一类难查的构建失败。
COPY pom.xml .
COPY src ./src
# 不跑测试：测试需要数据库与 OSS 凭证，属于本地/CI 的职责，不该成为镜像构建的前置条件。
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
EXPOSE 8080
# 只用 exec 形式：容器要能收到 SIGTERM 才会优雅停机。
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
