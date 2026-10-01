package com.earlylearning.early_learning_server.storage.client;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.SignVersion;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OSS 客户端装配：把 {@link OssProperties} 里的超时、连接池与重试设进 SDK。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OssProperties.class)
public class OssConfig {

    @Bean(destroyMethod = "shutdown")
    public OSS ossClient(OssProperties properties) {
        var configuration = new ClientBuilderConfiguration();
        configuration.setSignatureVersion(SignVersion.V4);
        configuration.setConnectionTimeout(properties.connectionTimeoutMs());
        configuration.setSocketTimeout(properties.socketTimeoutMs());
        configuration.setConnectionRequestTimeout(properties.connectionRequestTimeoutMs());
        configuration.setMaxConnections(properties.maxConnections());
        configuration.setMaxErrorRetry(properties.maxErrorRetry());
        // 不设「整请求总时长」上限。上传最大 500MB，慢网下正常耗时可能超过任何合理的总时长，
        // 设了只会误杀正在正常传输的上传；而「卡住」已由 socketTimeout 覆盖——
        // 它关心的是「多久没有数据」，不是「总共花了多久」。
        configuration.setRequestTimeoutEnabled(false);
        return OSSClientBuilder.create()
                .endpoint(properties.endpoint())
                .region(properties.region())
                .credentialsProvider(new DefaultCredentialProvider(
                        properties.accessKeyId(), properties.accessKeySecret()))
                .clientConfiguration(configuration)
                .build();
    }
}
