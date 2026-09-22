# OSS 底层服务

基于《技术方案》的“大文件存储”设计，`storage` 包通过 `ObjectStorageService` 提供上传、删除和临时下载地址。现有 `storage_cloud_file` 表保持不变。

## 配置

公共配置位于 `src/main/resources/application.yaml`，沿用 `aliyun.oss` 前缀。启动应用前，在运行环境或 IDE 运行配置中设置：

| 环境变量 | 含义 |
| --- | --- |
| `OSS_ENDPOINT` | Bucket 所在地域的 HTTPS Endpoint，例如 `https://oss-cn-hangzhou.aliyuncs.com`；使用下载客户端也能访问的域名 |
| `OSS_REGION` | Bucket 所在地域，例如 `cn-hangzhou` |
| `OSS_BUCKET_NAME` | 已创建的 Bucket 名称 |
| `OSS_ACCESS_KEY_ID` | 运行身份的 AccessKey ID |
| `OSS_ACCESS_KEY_SECRET` | 对应的 AccessKey Secret |
| `OSS_DOWNLOAD_URL_TTL_SECONDS` | 可选，临时下载地址有效秒数，默认 900，范围 1～604800 |

必要配置为空或有效期越界时，启动校验失败。凭证只通过运行环境注入，不写入代码或提交到仓库。运行完整应用仍需原有的数据库配置。

`OssConfig` 创建单例 OSS Client，使用 V4 签名，并通过 Bean 的 `destroyMethod` 在 Spring 容器关闭时调用 `shutdown()`。参考：[阿里云 Java SDK 官方说明](https://www.alibabacloud.com/help/en/oss/developer-reference/oss-java-sdk/)。

Endpoint 是服务连接地址，Region 是 V4 签名使用的地域标识。虽然标准 Endpoint 含地域信息，当前 SDK 仍要求显式传入 Region；本实现不从域名推导。例如上海的配置为 `OSS_ENDPOINT=https://oss-cn-shanghai.aliyuncs.com` 和 `OSS_REGION=cn-shanghai`。

## 上层调用

通过构造函数注入 `ObjectStorageService`，不依赖 OSS SDK：

```java
try (InputStream input = Files.newInputStream(path)) {
    objectStorageService.upload(objectKey, input, Files.size(path), mimeType);
}
URI downloadUrl = objectStorageService.generateDownloadUrl(objectKey);
objectStorageService.delete(objectKey);
```

- `objectKey` 由上层提供，底层不修改路径。同一路径上传会覆盖已有对象；新文件应使用独立路径，补偿时只删除本次上传的对象。
- 上传采用输入流，长度必须准确；原始输入流由调用方关闭。
- 下载地址使用配置的有效期，签发本身不检查对象存在性。上层先完成权限校验，避免记录完整签名地址。
- 删除不存在的对象视为成功；删除前的业务引用检查由上层负责。
- SDK 操作失败统一抛出 `IllegalStateException`，保留原始异常原因；不在底层吞掉上传或补偿删除失败。

元数据、FileCode、SHA256 校验、文件状态和 MySQL 事务由上层文件管理及业务流程负责。导入遵循“完整校验 → 写 OSS → 事务写 MySQL”；数据库失败时，通过接口删除本次上传对象进行补偿，补偿失败需由上层记录并处理。

## 验证

```powershell
.\mvnw.cmd -B test '-Dtest=OssObjectStorageServiceTests,OssConfigTests,OssSdkIntegrationTests,CodeValidationTests'
```

测试包含配置校验、Client 生命周期、流所有权、临时地址签发和 SDK 失败处理。SDK 集成测试仅连接本地 HTTP 服务，使用虚拟凭证；真实 Bucket 的权限和连通性需要在配置运行凭证后联调。

## 真实 Bucket 测试

`OssRealIntegrationTests` 默认跳过，设置 `OSS_REAL_TEST=true` 后才执行。它只加载存储模块的 Spring 配置，无需 MySQL 或启动完整应用。

在项目目录的 PowerShell 中运行，凭证通过交互输入，不写入命令历史：

```powershell
$env:OSS_ENDPOINT = 'https://oss-cn-shanghai.aliyuncs.com'
$env:OSS_REGION = 'cn-shanghai'
$env:OSS_BUCKET_NAME = Read-Host 'Bucket 名称'
$ossCredential = Get-Credential -Message '用户名填 AccessKey ID，密码填 AccessKey Secret'
$env:OSS_ACCESS_KEY_ID = $ossCredential.UserName
$env:OSS_ACCESS_KEY_SECRET = $ossCredential.GetNetworkCredential().Password
$env:OSS_REAL_TEST = 'true'

try {
    .\mvnw.cmd -B test '-Dtest=OssRealIntegrationTests'
} finally {
    Remove-Item Env:OSS_REAL_TEST, Env:OSS_ACCESS_KEY_ID, Env:OSS_ACCESS_KEY_SECRET -ErrorAction SilentlyContinue
    Remove-Variable ossCredential
}
```

也可在 IDE 的该测试运行配置中设置上述六个环境变量，然后运行测试类。

测试会在 `oss-smoke-test/<随机 UUID>/` 下上传一个小文件，通过签名地址下载并逐字节比对，然后删除并确认返回 404。正常完成后自动清理；中途失败也会尝试清理，清理失败会报告对象路径。若 Bucket 开启版本控制，删除可能保留历史版本，需要按 Bucket 的版本管理规则清理。

执行身份需要对该测试路径拥有上传、读取和删除对象的权限。通过标志为 `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`；若 `Skipped: 1`，说明没有开启测试开关。
