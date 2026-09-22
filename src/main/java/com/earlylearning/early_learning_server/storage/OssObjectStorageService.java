package com.earlylearning.early_learning_server.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.Date;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ObjectMetadata;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StreamUtils;

@Service
public class OssObjectStorageService implements ObjectStorageService {

    private final OSS client;
    private final OssProperties properties;

    public OssObjectStorageService(OSS client, OssProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void upload(String objectKey, InputStream input, long contentLength, String contentType) {
        Assert.hasText(objectKey, "objectKey must not be blank");
        Assert.notNull(input, "input must not be null");
        Assert.isTrue(contentLength >= 0, "contentLength must not be negative");
        Assert.hasText(contentType, "contentType must not be blank");

        var metadata = new ObjectMetadata();
        metadata.setContentLength(contentLength);
        metadata.setContentType(contentType);
        try {
            // SDK 可以关闭包装流，但原始流始终由调用方管理。
            client.putObject(properties.bucketName(), objectKey, StreamUtils.nonClosing(input), metadata);
        } catch (OSSException | ClientException ex) {
            throw new IllegalStateException("Object upload failed", ex);
        }
    }

    @Override
    public void delete(String objectKey) {
        Assert.hasText(objectKey, "objectKey must not be blank");
        try {
            client.deleteObject(properties.bucketName(), objectKey);
        } catch (OSSException | ClientException ex) {
            throw new IllegalStateException("Object deletion failed", ex);
        }
    }

    @Override
    public URI generateDownloadUrl(String objectKey) {
        Assert.hasText(objectKey, "objectKey must not be blank");
        Date expiration = Date.from(Instant.now().plusSeconds(properties.downloadUrlTtlSeconds()));
        try {
            return URI.create(client.generatePresignedUrl(properties.bucketName(), objectKey, expiration)
                    .toExternalForm());
        } catch (OSSException | ClientException ex) {
            throw new IllegalStateException("Download URL generation failed", ex);
        }
    }
}
