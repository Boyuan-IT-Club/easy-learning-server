package com.earlylearning.early_learning_server.ai.model.scoring;

/**
 * 一次提交里对一张图片的引用：还没解析的请求形态。
 *
 * <p>与 {@link ScoringImage} 的区别：这个是提交方给的东西（可能是编号、可能是 base64、可能是说明），
 * 那个是解析结果——要么有真字节，要么有教师确认过的文字说明。解析发生在 service 层
 * （提交的同步路径上），domain 与模型适配器只见解析结果。
 *
 * @param fileCode       来源编号，出错时用于定位
 * @param kind           提交形态
 * @param mimeType       INLINE_IMAGE 时必填
 * @param contentBase64  INLINE_IMAGE 时的图片内容
 * @param description    CONFIRMED_DESCRIPTION 时的教师确认说明
 * @param confirmed      CONFIRMED_DESCRIPTION 时必须为 true
 */
public record ImageRef(ImageKind kind,
                       String fileCode,
                       String mimeType,
                       String contentBase64,
                       String description,
                       Boolean confirmed) {
}
