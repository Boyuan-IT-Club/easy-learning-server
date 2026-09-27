package com.earlylearning.early_learning_server.ai.web;

import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 单张图片上下文的校验规则：两种形态各自需要哪些字段。
 *
 * <p>故事评分与单题评分共用——两边对「真实图片」与「确认说明」的要求完全一致。
 * 不同的是**哪些图片必须出现**：故事评分要求覆盖全部分组，单题评分允许为空
 * （「纯文本故事依据足够时可空」），那部分留在各自的请求校验器里。
 */
@Component
public class ImageContextValidator {

    private static final String INLINE_IMAGE_MIME_PREFIX = "image/";

    /**
     * @param image 单张图片，两种形态之一
     * @param path  出错时返回给客户端的字段路径前缀，如 {@code images/0}
     * @throws BusinessException 字段缺失或形态与内容不符
     */
    public void validate(ImageContext image, String path) {
        requireNotNull(image, path);
        requireText(image.fileCode(), path + "/file_code");
        if (image.kind() == null) {
            throw invalid(path + "/kind");
        }
        switch (image.kind()) {
            case INLINE_IMAGE -> {
                requireText(image.mimeType(), path + "/mime_type");
                if (!image.mimeType().startsWith(INLINE_IMAGE_MIME_PREFIX)) {
                    throw invalid(path + "/mime_type");
                }
                requireText(image.contentBase64(), path + "/content_base64");
            }
            case CONFIRMED_DESCRIPTION -> {
                requireText(image.description(), path + "/description");
                if (!Boolean.TRUE.equals(image.confirmed())) {
                    throw invalid(path + "/confirmed");
                }
            }
        }
    }

    private BusinessException invalid(String field) {
        return new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField("/" + field));
    }

    private void requireNotNull(Object value, String field) {
        if (value == null) {
            throw invalid(field);
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalid(field);
        }
    }
}
