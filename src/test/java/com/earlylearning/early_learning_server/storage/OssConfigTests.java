package com.earlylearning.early_learning_server.storage;

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
                    var uri = context.getBean(ObjectStorageService.class).generateDownloadUrl("图片/a + b.png");
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
