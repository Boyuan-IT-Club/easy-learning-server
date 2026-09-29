package com.earlylearning.early_learning_server.ai.domain.scoring;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 评分证据。
 *
 * @param source      证据来源；契约目前只允许 {@code TRANSCRIPT}
 * @param text        原文片段，必须与按偏移截取的内容完全一致
 * @param startOffset UTF-16 code unit 起点，左闭
 * @param endOffset   UTF-16 code unit 终点，右开，必须大于起点且不超过文本长度
 */
public record Evidence(

        @JsonProperty("source") String source,
        @JsonProperty("text") String text,
        @JsonProperty("start_offset") Integer startOffset,
        @JsonProperty("end_offset") Integer endOffset) {

    public static final String SOURCE_TRANSCRIPT = "TRANSCRIPT";

    public static Evidence transcript(String text, int startOffset, int endOffset) {
        return new Evidence(SOURCE_TRANSCRIPT, text, startOffset, endOffset);
    }
}
