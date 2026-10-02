package com.earlylearning.early_learning_server.storage.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.Date;

import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StreamUtils;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.storage.model.ObjectStorageService;

import lombok.RequiredArgsConstructor;

/** OSS 适配实现。失败抛 {@link BusinessException}（DEPENDENCY_UNAVAILABLE），由全局异常处理翻译成 503。 */
@Service
@RequiredArgsConstructor
public class OssObjectStorageService implements ObjectStorageService {

    private final OSS client;
    private final OssProperties ossProperties;

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
            client.putObject(ossProperties.bucketName(), objectKey, StreamUtils.nonClosing(input), metadata);
        } catch (OSSException | ClientException ex) {
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "Object upload failed", ex);
        }
    }

    @Override
    public void delete(String objectKey) {
        Assert.hasText(objectKey, "objectKey must not be blank");
        try {
            client.deleteObject(ossProperties.bucketName(), objectKey);
        } catch (OSSException | ClientException ex) {
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "Object deletion failed", ex);
        }
    }

    @Override
    public byte[] read(String objectKey) {
        Assert.hasText(objectKey, "objectKey must not be blank");
        try (OSSObject object = client.getObject(ossProperties.bucketName(), objectKey)) {
            return StreamUtils.copyToByteArray(object.getObjectContent());
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "Object read failed", ex);
        } catch (OSSException | ClientException ex) {
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "Object read failed", ex);
        }
    }

    @Override
    public DownloadUrl generateDownloadUrl(String objectKey) {
        Assert.hasText(objectKey, "objectKey must not be blank");
        // 从同一个 Date 取回报的时刻：Date 只到毫秒，若另用 Instant.now() 计算，
        // 回报值会与真正生效的到期时刻差一个亚毫秒量级——虽然很小，但没必要存在。
        Date expiration = Date.from(Instant.now().plusSeconds(ossProperties.downloadUrlTtlSeconds()));
        try {
            URI url = URI.create(client.generatePresignedUrl(
                    ossProperties.bucketName(), objectKey, expiration).toExternalForm());
            return new DownloadUrl(url, expiration.toInstant());
        } catch (OSSException | ClientException ex) {
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "Download URL generation failed", ex);
        }
    }
}
