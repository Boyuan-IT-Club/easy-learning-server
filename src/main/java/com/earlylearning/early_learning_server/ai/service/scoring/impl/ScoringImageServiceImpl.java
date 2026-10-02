package com.earlylearning.early_learning_server.ai.service.scoring.impl;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.springframework.stereotype.Component;

import com.earlylearning.early_learning_server.ai.model.scoring.ImageRef;
import com.earlylearning.early_learning_server.ai.model.scoring.ScoringImage;
import com.earlylearning.early_learning_server.ai.model.scoring.ScoringLimits;
import com.earlylearning.early_learning_server.ai.service.scoring.ScoringImageService;
import com.earlylearning.early_learning_server.common.enums.CloudFileKind;
import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.entity.CloudFile;
import com.earlylearning.early_learning_server.storage.model.ObjectStorageService;
import com.earlylearning.early_learning_server.storage.service.CloudFileQueryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** {@link ScoringImageService} 的实现。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScoringImageServiceImpl implements ScoringImageService {

    private final CloudFileQueryService cloudFileQueryService;
    private final ObjectStorageService objectStorageService;
    private final ScoringLimits scoringLimits;

    @Override
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
        if (base64.length() / 4L * 3 > scoringLimits.maxImageBytes()) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE,
                    ApiErrorDetails.ofLimit(ApiErrorDetails.LimitName.SIZE_BYTES, scoringLimits.maxImageBytes()));
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
        if (content.length > scoringLimits.maxImageBytes()) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE,
                    ApiErrorDetails.ofLimit(ApiErrorDetails.LimitName.SIZE_BYTES, scoringLimits.maxImageBytes()));
        }
        return new ScoringImage(image.fileCode(), image.mimeType(), content, null);
    }

    /** 按编号取回图片字节：先看状态与大小，再读内容。 */
    private ScoringImage fetch(String fileCode) {
        CloudFile file = cloudFileQueryService.requireReadable(fileCode);
        if (file.getFileKind() != CloudFileKind.IMAGE) {
            // 音频/PDF 喂给多模态模型没有意义，属于调用方用错了编号
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ApiErrorDetails.atFile(fileCode));
        }
        if (file.getSizeBytes() > scoringLimits.maxImageBytes()) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE,
                    ApiErrorDetails.ofLimit(ApiErrorDetails.LimitName.SIZE_BYTES, scoringLimits.maxImageBytes()));
        }
        byte[] content = objectStorageService.read(file.getObjectKey());
        log.info("为评分取回图片 fileCode={} mime={} bytes={}", fileCode, file.getMimeType(), content.length);
        return new ScoringImage(fileCode, file.getMimeType(), content, null);
    }
}
