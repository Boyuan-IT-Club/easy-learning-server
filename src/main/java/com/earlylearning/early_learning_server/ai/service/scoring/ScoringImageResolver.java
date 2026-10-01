package com.earlylearning.early_learning_server.ai.service.scoring;
import com.earlylearning.early_learning_server.ai.model.scoring.ScoringLimits;
import com.earlylearning.early_learning_server.ai.model.scoring.ScoringImage;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.earlylearning.early_learning_server.ai.model.scoring.ImageRef;
import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.storage.entity.CloudFile;
import com.earlylearning.early_learning_server.storage.entity.CloudFileKind;
import com.earlylearning.early_learning_server.storage.service.CloudFileQueryService;
import com.earlylearning.early_learning_server.storage.model.ObjectStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 把提交里的图片引用解析成模型能直接使用的内容,供评分输入组装。
 *
 * <ul>
 *   <li>{@code SERVER_FETCH}:按 {@code file_code} 取回字节;先校验文件状态与大小,
 *       语义与签发/元数据接口一致。</li>
 *   <li>{@code INLINE_IMAGE}:解码请求携带的 base64 并校验字节上限。</li>
 *   <li>{@code CONFIRMED_DESCRIPTION}:教师确认过的说明,直接作为文字交给模型。</li>
 * </ul>
 *
 * <p>取回的字节只在内存中存在,交给模型后即丢弃;不落盘、不写日志。
 */
@Component
public class ScoringImageResolver {

    private static final Logger log = LoggerFactory.getLogger(ScoringImageResolver.class);

    private final CloudFileQueryService files;
    private final ObjectStorageService storage;
    private final ScoringLimits limits;

    public ScoringImageResolver(CloudFileQueryService files, ObjectStorageService storage, ScoringLimits limits) {
        this.files = files;
        this.storage = storage;
        this.limits = limits;
    }

    public List<ScoringImage> resolve(List<ImageRef> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        List<ScoringImage> resolved = new ArrayList<>(images.size());
        for (ImageRef image : images) {
            resolved.add(switch (image.kind()) {
                case SERVER_FETCH -> fetch(image.fileCode());
                case INLINE_IMAGE -> decodeInline(image);
                case CONFIRMED_DESCRIPTION -> new ScoringImage(image.fileCode(), null, null, image.description());
            });
        }
        return List.copyOf(resolved);
    }

    /**
     * 内联图片：请求里直接带 base64。校验边界就在这里——解不出来、超过字节上限都是请求的问题，
     * 该在提交时以 400 / 413 回给客户端。
     *
     * <p>先按 base64 长度估算再用上限挡一道：这样超限的请求不会先解出一份大数组再被拒。
     */
    private ScoringImage decodeInline(ImageRef image) {
        String base64 = image.contentBase64();
        // 4 个 base64 字符 = 3 字节，末尾可能有填充
        if (base64.length() / 4L * 3 > limits.maxImageBytes()) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE,
                    ApiErrorDetails.ofLimit(ApiErrorDetails.LimitName.SIZE_BYTES, limits.maxImageBytes()));
        }
        byte[] content;
        try {
            content = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "content_base64 不是合法的 Base64",
                    ApiErrorDetails.atField("/images"));
        }
        if (content.length == 0) {
            // 解出来是空的：既不是图片，也不会被当成"有字节"，最后会变成提示词里的一句 "CF_X：null"
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "content_base64 解出来是空图片",
                    ApiErrorDetails.atField("/images"));
        }
        if (content.length > limits.maxImageBytes()) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE,
                    ApiErrorDetails.ofLimit(ApiErrorDetails.LimitName.SIZE_BYTES, limits.maxImageBytes()));
        }
        return new ScoringImage(image.fileCode(), image.mimeType(), content, null);
    }

    /** 按编号取回图片字节：先看状态与大小，再读内容。 */
    private ScoringImage fetch(String fileCode) {
        CloudFile file = files.requireReadable(fileCode);
        if (file.getFileKind() != CloudFileKind.IMAGE) {
            // 音频/PDF 喂给多模态模型没有意义，属于调用方用错了编号
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ApiErrorDetails.atFile(fileCode));
        }
        if (file.getSizeBytes() > limits.maxImageBytes()) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE,
                    ApiErrorDetails.ofLimit(ApiErrorDetails.LimitName.SIZE_BYTES, limits.maxImageBytes()));
        }
        byte[] content = storage.read(file.getObjectKey());
        log.info("为评分取回图片 fileCode={} mime={} bytes={}", fileCode, file.getMimeType(), content.length);
        return new ScoringImage(fileCode, file.getMimeType(), content, null);
    }
}
