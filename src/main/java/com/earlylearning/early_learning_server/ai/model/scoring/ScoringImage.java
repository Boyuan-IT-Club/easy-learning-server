package com.earlylearning.early_learning_server.ai.model.scoring;

/**
 * 一张已经解析好、可以直接交给模型的图片。
 *
 * <p>与 web 层的 {@code ImageContext} 的区别：那个是请求形状（可能是编号、可能是说明），
 * 这个是解析结果——要么有真字节，要么有教师确认过的文字说明。
 *
 * <p>为什么要有这一层：评分适配器不该认识 HTTP 请求模型（那样端口就反向依赖 web 包了），
 * 也不该自己去找存储。
 *
 * @param fileCode    来源编号，出错时用于定位
 * @param mimeType    真实 MIME；只有说明时为 null
 * @param content     图片字节；只有说明时为 null
 * @param description 教师确认过的说明；有字节时为 null
 */
public record ScoringImage(String fileCode, String mimeType, byte[] content, String description) {

    /** 有字节的是"真图"，只有说明的是"确认说明"。 */
    public boolean hasBytes() {
        return content != null && content.length > 0;
    }
}
