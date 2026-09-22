# early-learning-server

早期学习项目的 Java 服务端，负责云端账号、课程、评估、字典及资源存储等能力。业务规则与数据协议以 [技术方案](docs/技术方案.md) 和 [功能细则入口](docs/参考资料.md) 为准。

当前已包含云端数据库迁移、公共 Code 校验和 OSS 存储服务；其余业务包主要为模块占位，尚未提供完整业务接口或正式鉴权流程。

## 环境准备

| 工具 / 服务 | 要求 |
| --- | --- |
| JDK | 项目编译目标为 Java 21，开发可使用 JDK 21；配置 `JAVA_HOME` |
| Maven | 使用仓库自带 Maven Wrapper，无需单独安装；首次运行需要联网下载 |
| MySQL | 使用 MySQL 8，需支持迁移中的 `utf8mb4_0900_bin` 排序规则和 `CHECK` 约束 |
| 阿里云 OSS | 启动应用需填写 OSS 配置；资源联调需已有 Bucket 及具备上传、读取、删除权限的凭证 |

主要依赖：Spring Boot 4.1.1、MyBatis-Plus 3.5.17、Flyway、阿里云 OSS SDK。具体版本见 [pom.xml](pom.xml)。

## 快速启动

以下命令均在项目根目录执行。

### 1. 创建开发数据库

使用 MySQL 客户端登录，例如 `mysql -u root -p`，然后执行：

```sql
CREATE DATABASE IF NOT EXISTS early_learning
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
```

配置的数据库账号需要具备该库的建表、索引、外键及数据读写权限。应用启动时由 Flyway 执行 `src/main/resources/db/migration/` 下的迁移，不需要手动执行 SQL 文件。迁移只建表，不创建初始管理员账号。

### 2. 填写本地配置

Windows PowerShell：

```powershell
Copy-Item .env.example .env
```

macOS / Linux：

```bash
cp .env.example .env
```

编辑 `.env`，填写 `DB_PASSWORD`、`OSS_BUCKET_NAME`、`OSS_ACCESS_KEY_ID` 和 `OSS_ACCESS_KEY_SECRET`，并核对数据库账号、地址和 OSS 地域。示例中的空值需要补齐。已有 `.env` 时直接编辑，避免重新复制覆盖。

| 配置项 | 说明 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | 默认 `dev`，加载 `application-dev.yml` |
| `SERVER_PORT` | 默认 `8080` |
| `SPRING_DATASOURCE_URL` | 覆盖完整 JDBC URL，适用于修改数据库主机、端口或库名 |
| `DB_USERNAME` / `DB_PASSWORD` | `dev` 配置使用的数据库账号和密码；用户名默认 `root` |
| `OSS_ENDPOINT` / `OSS_REGION` | Bucket 的 HTTPS Endpoint 和签名地域，必须匹配 |
| `OSS_BUCKET_NAME` | 已创建的 Bucket 名称 |
| `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET` | OSS 访问凭证 |
| `OSS_DOWNLOAD_URL_TTL_SECONDS` | 签名下载地址有效秒数，默认 `900`，范围 `1～604800` |
| `OSS_REAL_TEST` | 默认 `false`；仅显式设为 `true` 时执行真实 Bucket 测试 |

`.env` 已加入 Git 忽略规则，真实凭证只保存在本地或运行环境中。OSS 配置细节见 [OSS 接入说明](docs/OSS接入.md)。

### 3. 加载环境变量并启动

**项目不会自动读取 `.env`。** 使用下面的命令将其加载到当前终端，再启动应用。文件格式为每行 `KEY=VALUE`，值不加引号；以 `#` 开头的整行为注释，不支持行尾注释、多行值或变量插值。加载时保留值中的 `=`、`&`、`$` 等字符，不执行文件内容。

Windows PowerShell：

```powershell
Get-Content -Encoding UTF8 .env | ForEach-Object {
    if ($_ -match '^\s*(#|$)') { return }
    if ($_ -notmatch '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        throw '无效的 .env 格式，请使用 KEY=VALUE'
    }
    [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
}
.\mvnw.cmd spring-boot:run
```

macOS / Linux（Bash）：

```bash
load_env() {
    local line
    while IFS= read -r line || [ -n "$line" ]; do
        line=${line%$'\r'}
        [[ "$line" =~ ^[[:space:]]*(#|$) ]] && continue
        if [[ ! "$line" =~ ^[A-Za-z_][A-Za-z0-9_]*= ]]; then
            echo '无效的 .env 格式，请使用 KEY=VALUE' >&2
            return 1
        fi
        export "$line"
    done < .env
}
load_env && bash ./mvnw spring-boot:run
```

环境变量只影响当前终端及其子进程，新开终端后需重新加载。在 IDE 中运行时，将相同变量配置到运行配置的 Environment variables，主类为 `com.earlylearning.early_learning_server.EarlyLearningServerApplication`，工作目录设为项目根目录。

默认服务地址为 `http://localhost:8080`。以日志出现 `Started EarlyLearningServerApplication` 判断启动成功；当前没有首页、Swagger 或 Actuator 健康检查接口。Spring Security 尚使用自动配置，访问可能触发登录页或返回 401；生成的默认用户不属于业务管理员账号。

## 编译与测试

无需 MySQL 或真实 OSS 凭证即可运行的测试：

```powershell
.\mvnw.cmd -B test '-Dtest=OssObjectStorageServiceTests,OssConfigTests,OssSdkIntegrationTests,CodeValidationTests'
```

这些测试覆盖存储行为、配置校验、SDK 本地 HTTP 集成和 Code 校验。`OssSdkIntegrationTests` 仅连接本机 HTTP 服务。

完整测试需要先按上文加载环境变量并准备开发数据库：

```powershell
.\mvnw.cmd -B test
```

`EarlyLearningServerApplicationTests` 和 `DatabaseMigrationTests` 会启动 Spring 上下文并连接配置的 MySQL；迁移测试会建表并校验约束，数据写入测试使用事务回滚。请使用专用开发 / 测试数据库。`OSS_REAL_TEST=false` 时跳过真实 OSS 测试；开启后的上传、下载、删除联调见 [OSS 接入说明](docs/OSS接入.md#真实-bucket-测试)。

编译、打包和运行 JAR：

```powershell
.\mvnw.cmd -B compile
.\mvnw.cmd -B package
java -jar target/early-learning-server-0.0.1-SNAPSHOT.jar
```

`package` 会执行测试，因此需要完整测试的环境。仅生成包可使用 `.\mvnw.cmd -B package -DskipTests`；运行 JAR 仍需加载环境变量。macOS / Linux 将命令中的 `.\mvnw.cmd` 替换为 `bash ./mvnw`。

## 目录与开发约定

```text
docs/                              技术方案、功能细则入口、OSS 接入说明
src/main/java/com/earlylearning/early_learning_server/
  admin/ auth/ license/ teacher/    账号与鉴权业务模块
  course/ assessment/ dictionary/  课程、评估与字典业务模块
  grammar/ ai/                     语法与 AI 业务模块
  storage/                         ObjectStorageService 及 OSS 实现
  common/code/                     跨端稳定 Code 类型与校验
src/main/resources/
  application.yaml                 公共配置
  application-dev.yml              开发数据库配置
  db/migration/                    Flyway 迁移
src/test/java/                     单元与集成测试
```

开发前阅读 [AGENTS.md](AGENTS.md) 和对应 `docs/` 文档。业务逻辑归所属模块，Controller 保持薄；资源导入先完整校验，再写 OSS，最后以事务写 MySQL，部分失败通过补偿清理处理。数据库变更使用新的 Flyway Migration，不修改已执行的脚本；JSON、Code 和跨端协议沿用既有契约。

## 常见问题

| 现象 | 排查方向 |
| --- | --- |
| Java 版本错误或找不到 Java | 检查 `java -version`、`JAVA_HOME` 及 IDE 的项目 / Maven JDK |
| 数据库连接失败或找不到 `DB_PASSWORD` | 确认已加载 `.env`，检查 MySQL、数据库名、账号权限及 JDBC URL |
| OSS 配置校验失败 | 必填项不能为空；检查 Endpoint、Region、Bucket 和下载地址有效期 |
| Flyway 迁移失败 | 检查 MySQL 版本、DDL 权限和已有表状态；不要通过修改已执行迁移或清空数据库绕过问题 |
| 修改 Flyway 配置没有效果 | 当前 `application-dev.yml` 中的配置位于 `aliyun.flyway`，不属于 Spring Boot 的 Flyway 配置前缀；当前迁移依赖自动配置默认值，后续调整应使用 `spring.flyway` |
| 端口被占用 | 修改 `.env` 中的 `SERVER_PORT` 并重新加载 |
