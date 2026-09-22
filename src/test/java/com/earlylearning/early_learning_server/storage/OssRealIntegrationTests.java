package com.earlylearning.early_learning_server.storage;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 显式开启后访问真实 Bucket；仅加载存储配置，无需启动数据库和 Web 应用。 */
@EnabledIfEnvironmentVariable(named = "OSS_REAL_TEST", matches = "true")
@SpringJUnitConfig({OssConfig.class, OssObjectStorageService.class})
@TestPropertySource(properties = {
        "aliyun.oss.endpoint=${OSS_ENDPOINT}",
        "aliyun.oss.region=${OSS_REGION}",
        "aliyun.oss.bucket-name=${OSS_BUCKET_NAME}",
        "aliyun.oss.access-key-id=${OSS_ACCESS_KEY_ID}",
        "aliyun.oss.access-key-secret=${OSS_ACCESS_KEY_SECRET}"
})
class OssRealIntegrationTests {

    private static final Logger log = LoggerFactory.getLogger(OssRealIntegrationTests.class);

    @Autowired
    private ObjectStorageService storage;

    @Test
    void uploadSignedDownloadAndDeleteAgainstRealBucket() throws Exception {
        String objectKey = "oss-smoke-test/" + UUID.randomUUID() + "/测试 + file.txt";
        byte[] content = "OSS real integration test / 上传下载校验".getBytes(StandardCharsets.UTF_8);
        log.info("[OSS真实测试] 开始，objectKey={}，文件大小={} 字节", objectKey, content.length);

        // 无论断言或网络请求是否失败，都尝试清理本次唯一对象；保留原始失败及清理异常。
        try (AutoCloseable cleanup = () -> {
            try {
                log.info("[OSS真实测试] 清理测试对象，objectKey={}", objectKey);
                storage.delete(objectKey);
                log.info("[OSS真实测试] 清理完成");
            } catch (RuntimeException failure) {
                log.error("[OSS真实测试] 清理失败，请检查测试对象，objectKey={}", objectKey);
                throw new IllegalStateException("Test object cleanup failed: " + objectKey, failure);
            }
        }; HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            log.info("[OSS真实测试] 正在上传文件");
            try (var input = new ByteArrayInputStream(content)) {
                storage.upload(objectKey, input, content.length, "text/plain; charset=UTF-8");
            }
            log.info("[OSS真实测试] 上传成功");

            log.info("[OSS真实测试] 正在生成签名地址并下载文件");
            var downloaded = download(http, storage.generateDownloadUrl(objectKey));
            log.info("[OSS真实测试] 下载响应：HTTP {}，收到 {} 字节", downloaded.statusCode(), downloaded.body().length);
            assertEquals(200, downloaded.statusCode(), "Signed download must succeed");
            assertArrayEquals(content, downloaded.body(), "Downloaded content must match uploaded content");
            log.info("[OSS真实测试] 文件内容校验通过");

            log.info("[OSS真实测试] 正在删除文件并验证删除结果");
            storage.delete(objectKey);
            var deleted = download(http, storage.generateDownloadUrl(objectKey));
            log.info("[OSS真实测试] 删除后下载响应：HTTP {}，预期 404", deleted.statusCode());
            assertEquals(404, deleted.statusCode(), "Deleted object must no longer be downloadable");
        }
        log.info("[OSS真实测试] 全部通过：上传、签名下载、内容校验、删除验证及清理完成");
    }

    private HttpResponse<byte[]> download(HttpClient client, URI uri) throws Exception {
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
                .header("Cache-Control", "no-cache").GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }
}
