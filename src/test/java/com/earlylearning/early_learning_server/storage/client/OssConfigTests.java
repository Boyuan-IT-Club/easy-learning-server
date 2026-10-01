package com.earlylearning.early_learning_server.storage.client;
import com.earlylearning.early_learning_server.storage.model.ObjectStorageService;

import java.util.Arrays;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OssConfigTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(OssConfig.class, OssObjectStorageService.class)
            .withPropertyValues(
                    "aliyun.oss.endpoint=https://oss-cn-hangzhou.aliyuncs.com",
                    "aliyun.oss.region=cn-hangzhou",
                    "aliyun.oss.bucket-name=test-bucket",
                    "aliyun.oss.access-key-id=test-id",
                    "aliyun.oss.access-key-secret=test-secret");

    @Test
    void springCreatesOneClientAndSignsWithV4() {
        runner.run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(OSS.class)
                            .hasSingleBean(ObjectStorageService.class);
                    assertThat(context.getBean(OssProperties.class).downloadUrlTtlSeconds()).isEqualTo(900);
                    assertThat(context.getBean(OssProperties.class).toString()).doesNotContain("test-secret", "test-id");
                    var signed = context.getBean(ObjectStorageService.class).generateDownloadUrl("图片/a + b.png");
                    var uri = signed.url();
                    assertThat(uri.getHost()).isEqualTo("test-bucket.oss-cn-hangzhou.aliyuncs.com");
                    assertThat(uri.getPath()).isEqualTo("/图片/a + b.png");
                    assertThat(uri.getQuery()).contains("x-oss-signature-version=OSS4-HMAC-SHA256",
                            "x-oss-signature=");
                    // SDK 按签名时刻计算剩余整秒数，生成耗时会扣除部分有效期。
                    long remainingSeconds = Arrays.stream(uri.getQuery().split("&"))
                            .filter(parameter -> parameter.startsWith("x-oss-expires="))
                            .mapToLong(parameter -> Long.parseLong(parameter.substring("x-oss-expires=".length())))
                            .findFirst().orElseThrow();
                    assertThat(remainingSeconds).isBetween(1L, 900L);
                    // 回报的到期时刻必须与地址自身的有效期一致——这正是"签发方一处计算"要防的漂移。
                    long reportedValidity =
                            java.time.Duration.between(java.time.Instant.now(), signed.expiresAt()).toSeconds();
                    assertThat(reportedValidity).isBetween(remainingSeconds - 5L, remainingSeconds + 5L);
                });
    }

    @Test
    void springShutsDownTheClientCreatedByTheSdkBuilder() {
        OSS client = mock(OSS.class);
        var builder = mock(OSSClientBuilder.OSSClientBuilderImpl.class, RETURNS_SELF);
        when(builder.build()).thenReturn(client);
        try (var sdk = mockStatic(OSSClientBuilder.class)) {
            sdk.when(OSSClientBuilder::create).thenReturn(builder);
            runner.run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(OSS.class)).isSameAs(client);
            });
            verify(client).shutdown();
        }
    }

    @Test
    void clientConfigurationCarriesTimeoutsPoolAndRetry() {
        runner.run(context -> {
            // getClientConfiguration 只在实现类 OSSClient 上，OSS 接口没有暴露它。
            var applied = ((com.aliyun.oss.OSSClient) context.getBean(OSS.class)).getClientConfiguration();
            // 断言的是「我们配的值」，而它们都与 SDK 默认值不同（默认：连接 50000、取连接 -1、
            // 池 1024、重试 3）。因此这个测试能证伪「配了但没接上」——接不上就会读到默认值。
            assertThat(applied.getConnectionTimeout()).isEqualTo(5000);
            assertThat(applied.getSocketTimeout()).isEqualTo(30000);
            assertThat(applied.getConnectionRequestTimeout()).isEqualTo(5000);
            assertThat(applied.getMaxConnections()).isEqualTo(64);
            assertThat(applied.getMaxErrorRetry()).isEqualTo(2);
            // 总时长不设上限：否则慢网下正常传输的大文件会被误杀。
            assertThat(applied.isRequestTimeoutEnabled()).isFalse();
        });
    }

    @Test
    void outOfRangeTimeoutOrRetryPreventsStartup() {
        for (String property : new String[]{
                "aliyun.oss.connection-timeout-ms=999",
                "aliyun.oss.socket-timeout-ms=0",
                "aliyun.oss.connection-request-timeout-ms=50",
                "aliyun.oss.max-connections=0",
                "aliyun.oss.max-error-retry=6"}) {
            runner.withPropertyValues(property).run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void missingRequiredConfigurationPreventsStartup() {
        runner.withPropertyValues("aliyun.oss.region=").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void invalidUrlLifetimePreventsStartup() {
        for (long value : new long[]{0, -1, 604801}) {
            runner.withPropertyValues("aliyun.oss.download-url-ttl-seconds=" + value)
                    .run(context -> assertThat(context).hasFailed());
        }
    }
}
