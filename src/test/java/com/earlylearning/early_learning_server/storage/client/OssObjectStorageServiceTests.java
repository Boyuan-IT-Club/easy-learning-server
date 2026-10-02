package com.earlylearning.early_learning_server.storage.client;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.storage.model.ObjectStorageService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OssObjectStorageServiceTests {

    private final OSS client = mock(OSS.class);
    private final ObjectStorageService service = new OssObjectStorageService(client,
            new OssProperties("https://oss-cn-hangzhou.aliyuncs.com", "cn-hangzhou", "test-bucket",
                    "test-id", "test-secret", 900, 5000, 30000, 5000, 64, 2));

    @Test
    void uploadPreservesContentAndMetadataAndLeavesInputOpen() throws Exception {
        var input = spy(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        when(client.putObject(eq("test-bucket"), eq("图片/a + b.png"), any(InputStream.class),
                any(ObjectMetadata.class))).thenAnswer(invocation -> {
                    InputStream uploaded = invocation.getArgument(2);
                    assertArrayEquals(new byte[]{1, 2, 3}, uploaded.readAllBytes());
                    ObjectMetadata metadata = invocation.getArgument(3);
                    assertEquals(3, metadata.getContentLength());
                    assertEquals("image/png", metadata.getContentType());
                    uploaded.close();
                    return null;
                });

        service.upload("图片/a + b.png", input, 3, "image/png");

        verify(input, never()).close();
        verify(client).putObject(eq("test-bucket"), eq("图片/a + b.png"), any(InputStream.class),
                any(ObjectMetadata.class));
    }

    @Test
    void uploadFailureRetainsCauseAndLeavesInputOpen() throws Exception {
        var input = spy(new ByteArrayInputStream(new byte[]{1}));
        var cause = new ClientException("connection failed");
        when(client.putObject(anyString(), anyString(), any(InputStream.class), any(ObjectMetadata.class)))
                .thenAnswer(invocation -> {
                    invocation.<InputStream>getArgument(2).close();
                    throw cause;
                });

        var failure = assertThrows(BusinessException.class,
                () -> service.upload("image.png", input, 1, "image/png"));

        assertSame(cause, failure.getCause());
        verify(input, never()).close();
    }

    @Test
    void invalidArgumentsFailBeforeCallingOss() {
        var input = new ByteArrayInputStream(new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> service.upload(" ", input, 0, "image/png"));
        assertThrows(IllegalArgumentException.class, () -> service.upload("key", null, 0, "image/png"));
        assertThrows(IllegalArgumentException.class, () -> service.upload("key", input, -1, "image/png"));
        assertThrows(IllegalArgumentException.class, () -> service.upload("key", input, 0, ""));
        assertThrows(IllegalArgumentException.class, () -> service.delete(""));
        assertThrows(IllegalArgumentException.class, () -> service.generateDownloadUrl(null));
        verifyNoInteractions(client);
    }

    @Test
    void deletionIsRepeatableAndDoesNotSwallowOssFailures() {
        service.delete("missing.png");
        service.delete("missing.png");
        verify(client, times(2)).deleteObject("test-bucket", "missing.png");

        var cause = new OSSException("access denied");
        doThrow(cause).when(client).deleteObject("test-bucket", "protected.png");
        assertSame(cause, assertThrows(BusinessException.class,
                () -> service.delete("protected.png")).getCause());
    }

    @Test
    void downloadUrlPreservesSignatureAndUsesConfiguredLifetime() throws Exception {
        URI signed = URI.create("https://test-bucket.example.com/a%20%2B.png?signature=test%2Bvalue");
        when(client.generatePresignedUrl(eq("test-bucket"), eq("a +.png"), any(Date.class)))
                .thenReturn(signed.toURL());
        Instant earliest = Instant.now().plusSeconds(900);

        ObjectStorageService.DownloadUrl result = service.generateDownloadUrl("a +.png");
        assertEquals(signed, result.url());

        Instant latest = Instant.now().plusSeconds(900);
        var expiration = ArgumentCaptor.forClass(Date.class);
        verify(client).generatePresignedUrl(eq("test-bucket"), eq("a +.png"), expiration.capture());
        assertTrue(expiration.getValue().getTime() >= earliest.toEpochMilli());
        assertTrue(expiration.getValue().getTime() <= latest.toEpochMilli());
        // 回报的到期时刻必须就是交给 SDK 的那一个，而不是调用方另算一遍的结果。
        assertEquals(expiration.getValue().toInstant(), result.expiresAt());
    }

    @Test
    void signingFailureRetainsCause() {
        var cause = new ClientException("signing failed");
        when(client.generatePresignedUrl(anyString(), anyString(), any(Date.class))).thenThrow(cause);
        assertSame(cause, assertThrows(BusinessException.class,
                () -> service.generateDownloadUrl("key")).getCause());
    }

    @Test
    void readReturnsObjectBytes() throws Exception {
        byte[] content = {1, 2, 3, 4};
        OSSObject object = mock(OSSObject.class);
        when(object.getObjectContent()).thenReturn(new ByteArrayInputStream(content));
        when(client.getObject("test-bucket", "图片/a + b.png")).thenReturn(object);

        assertArrayEquals(content, service.read("图片/a + b.png"));
    }

    @Test
    void readFailureBecomesDependencyUnavailable() {
        OSSException cause = new OSSException("boom");
        when(client.getObject(anyString(), anyString())).thenThrow(cause);

        BusinessException failure = assertThrows(BusinessException.class, () -> service.read("missing.png"));

        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, failure.getErrorCode());
    }
}
