package com.earlylearning.early_learning_server.ai.service.scoring;
import com.earlylearning.early_learning_server.ai.model.scoring.ScoringLimits;
import com.earlylearning.early_learning_server.ai.model.scoring.ScoringImage;
import com.earlylearning.early_learning_server.ai.model.scoring.ImageKind;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.earlylearning.early_learning_server.ai.model.scoring.ImageKind;
import com.earlylearning.early_learning_server.ai.model.scoring.ImageRef;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.storage.entity.CloudFile;
import com.earlylearning.early_learning_server.storage.entity.CloudFileKind;
import com.earlylearning.early_learning_server.storage.service.CloudFileQueryService;
import com.earlylearning.early_learning_server.storage.entity.CloudFileStatus;
import com.earlylearning.early_learning_server.storage.model.ObjectStorageService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 图片解析：把请求里的 {@code SERVER_FETCH} 变成真字节。
 *
 * <p>这是本次需求新增的能力——服务端按 {@code file_code} 取回图片内容交给模型。
 * 重点是失败语义跟签发/元数据接口保持一致，别出现"下载拿不到、评分却能拿到"。
 */
class ScoringImageResolverTests {

    private final CloudFileQueryService files = mock(CloudFileQueryService.class);
    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final ScoringLimits limits = new ScoringLimits(20000, 20, 4096);
    private final ScoringImageResolver resolver = new ScoringImageResolver(files, storage, limits);

    @Test
    void serverFetchReadsTheObjectByFileCode() {
        CloudFile file = imageFile(1024);
        when(files.requireReadable("CF_IMG_1")).thenReturn(file);
        when(storage.read("image/2026/09/abc.png")).thenReturn("图片字节".getBytes(StandardCharsets.UTF_8));

        List<ScoringImage> resolved = resolver.resolve(
                List.of(serverFetch("CF_IMG_1")));

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0).fileCode()).isEqualTo("CF_IMG_1");
        assertThat(resolved.get(0).mimeType()).isEqualTo("image/png");
        assertThat(resolved.get(0).hasBytes()).isTrue();
        assertThat(resolved.get(0).content()).isEqualTo("图片字节".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void inlineImageIsDecodedAndNeedsNoStorageAtAll() {
        // 内联图片此前会被静默丢掉（只能识别取回与说明两种形态）：模型收不到图，分数却照样出
        List<ScoringImage> resolved = resolver.resolve(List.of(new ImageRef(
                ImageKind.INLINE_IMAGE, "CF_IMG_8", "image/png", "5Zu+54mH", null, null)));

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0).mimeType()).isEqualTo("image/png");
        assertThat(resolved.get(0).content()).isEqualTo("图片".getBytes(StandardCharsets.UTF_8));
        verify(files, never()).requireReadable(anyString());
        verify(storage, never()).read(anyString());
    }

    @Test
    void inlineImageThatDecodesToNothingIsRejectedAsARequestError() {
        // 空 base64 解出 0 字节：它不是图片，也不会被当成"有字节"，
        // 放过去最后会变成提示词里的一句 "CF_IMG_8：null"
        assertThatThrownBy(() -> resolver.resolve(List.of(new ImageRef(
                ImageKind.INLINE_IMAGE, "CF_IMG_8", "image/png", "", null, null))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    void malformedInlineBase64IsRejectedAsARequestError() {
        // 客户端传的内容不合法 → 提交时就该 400，而不是落成"模型输出不合法"的任务失败
        assertThatThrownBy(() -> resolver.resolve(List.of(new ImageRef(
                ImageKind.INLINE_IMAGE, "CF_IMG_8", "image/png", "这不是 base64!!", null, null))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    void oversizedInlineImageIsRejectedBeforeDecoding() {
        // 上限 4096 字节：base64 长度先挡一道，避免先解出一份大数组再被拒
        String oversized = "A".repeat(8192);

        assertThatThrownBy(() -> resolver.resolve(List.of(new ImageRef(
                ImageKind.INLINE_IMAGE, "CF_IMG_8", "image/png", oversized, null, null))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE));
    }

    @Test
    void confirmedDescriptionNeedsNoStorageAtAll() {
        List<ScoringImage> resolved = resolver.resolve(List.of(new ImageRef(
                ImageKind.CONFIRMED_DESCRIPTION, "CF_IMG_9", null, null, "图里有一只小狗", true)));

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0).hasBytes()).isFalse();
        assertThat(resolved.get(0).description()).isEqualTo("图里有一只小狗");
        verify(files, never()).requireReadable(anyString());
        verify(storage, never()).read(anyString());
    }

    @Test
    void aNonImageKindIsRejectedWithUnsupportedMediaType() {
        when(files.requireReadable("CF_AUDIO")).thenReturn(file(CloudFileKind.AUDIO, 1024, "audio/wav"));

        assertThatThrownBy(() -> resolver.resolve(List.of(serverFetch("CF_AUDIO"))))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void anOversizedImageIsRejectedWithTheSizeLimitBeforeReading() {
        when(files.requireReadable("CF_BIG")).thenReturn(imageFile(8192));

        assertThatThrownBy(() -> resolver.resolve(List.of(serverFetch("CF_BIG"))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException failure = (BusinessException) ex;
                    assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE);
                    assertThat(failure.getDetails().limit().maximum()).isEqualTo(4096L);
                });
        // 超限时不该真的去下载
        verify(storage, never()).read(anyString());
    }

    @Test
    void storageErrorsPropagateWithTheirOwnSemantics() {
        // 已删除 → 410，由存储侧抛出，解析器不吞不改
        when(files.requireReadable("CF_GONE"))
                .thenThrow(new BusinessException(ErrorCode.FILE_DELETED));

        assertThatThrownBy(() -> resolver.resolve(List.of(serverFetch("CF_GONE"))))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FILE_DELETED);
    }

    @Test
    void emptyOrAbsentImagesResolveToEmptyList() {
        assertThat(resolver.resolve(null)).isEmpty();
        assertThat(resolver.resolve(List.of())).isEmpty();
    }

    private ImageRef serverFetch(String fileCode) {
        return new ImageRef(ImageKind.SERVER_FETCH, fileCode, null, null, null, null);
    }

    private CloudFile imageFile(long sizeBytes) {
        return file(CloudFileKind.IMAGE, sizeBytes, "image/png");
    }

    private CloudFile file(CloudFileKind kind, long sizeBytes, String mimeType) {
        CloudFile file = new CloudFile();
        file.setFileCode("CF_X");
        file.setFileKind(kind);
        file.setStatus(CloudFileStatus.READY);
        file.setSizeBytes(sizeBytes);
        file.setMimeType(mimeType);
        file.setObjectKey("image/2026/09/abc.png");
        return file;
    }
}
