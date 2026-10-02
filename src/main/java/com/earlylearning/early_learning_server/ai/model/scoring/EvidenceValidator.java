package com.earlylearning.early_learning_server.ai.model.scoring;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * 证据的运行时校验：「不能编造引文」的可执行版本。
 *
 * <p>规则来自契约：{@code evidence} 可为空数组，但每一条都必须真的是确认文本里的片段。
 * 只校验「字段非空」不够——模型完全可以编一句听起来合理、原文里没有的话。
 * 按偏移截取再与 {@code text} 逐字比对，才能把这种情况挡下来。
 *
 * <p>故事评分与单题评分共用：两边对证据的要求完全一致。
 */
@Component
public class EvidenceValidator {

    /**
     * @param evidence      待校验的证据，可为 null 或空
     * @param where         出错时的定位说明（如「微观维度 XX」）
     * @param confirmedText 教师确认过的原文，证据必须取自其中
     * @throws InvalidModelOutputException 任一条证据与原文不符
     */
    public void validate(List<Evidence> evidence, String where, String confirmedText) {
        if (evidence == null) {
            return;
        }
        for (Evidence item : evidence) {
            require(item != null, where + " 的证据条目为空");
            require(Evidence.SOURCE_TRANSCRIPT.equals(item.source()),
                    where + " 的证据来源不是 " + Evidence.SOURCE_TRANSCRIPT);
            require(item.startOffset() != null && item.endOffset() != null,
                    where + " 的证据缺少偏移");
            int start = item.startOffset();
            int end = item.endOffset();
            require(start >= 0 && end > start && end <= confirmedText.length(),
                    where + " 的证据偏移越界：[" + start + "," + end + ") 文本长度 " + confirmedText.length());
            String slice = confirmedText.substring(start, end);
            require(slice.equals(item.text()),
                    where + " 的证据与原文不一致：偏移处为「" + slice + "」，证据为「" + item.text() + "」");
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidModelOutputException(message);
        }
    }
}
