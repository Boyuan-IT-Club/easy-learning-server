package com.earlylearning.early_learning_server.ai.web;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 一张图片的上下文：契约用 {@code kind} 区分两种形态。
 *
 * <ul>
 *   <li>{@code INLINE_IMAGE} —— 真实图片：{@code mime_type} 与 {@code content_base64} 必须有值</li>
 *   <li>{@code CONFIRMED_DESCRIPTION} —— 确认说明：{@code description} 必须有值且 {@code confirmed} 为 true</li>
 *   <li>{@code SERVER_FETCH} —— 只给 {@code file_code}，服务端按编号取回图片内容再交给模型</li>
 * </ul>
 *
 * <p>共用一个类型而不是两个多态子类：字段少，且"哪些字段该有值"需要按形态逐条校验，
 * 放在一处反而更容易看清。
 */
public record ImageContext(

        @JsonProperty("kind") ImageKind kind,
        @JsonProperty("file_code") String fileCode,
        @JsonProperty("mime_type") String mimeType,
        @JsonProperty("content_base64") String contentBase64,
        @JsonProperty("description") String description,
        @JsonProperty("confirmed") Boolean confirmed) {

    public enum ImageKind {
        INLINE_IMAGE,
        CONFIRMED_DESCRIPTION,
        /** 只给编号，由**服务端**取回图片内容——本项目的主用形态（平板侧图片已在对象存储里）。 */
        SERVER_FETCH
    }
}
