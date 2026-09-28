package com.earlylearning.early_learning_server.ai.score;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 把模型**照抄的原文片段**定位到确认文本里，得到带偏移的证据。故事评分与单题评分共用。
 *
 * <p>为什么不采信模型给的偏移：实测同一 prompt 连打 5 次，有 3 次把 11 个 UTF-16 code unit
 * 的句子标成 {@code [0,12)}——模型数不准偏移。让模型只负责"照抄哪句话"、偏移由服务端算，
 * 这条链路才稳定；也顺带兑现了契约的「不得编造引文」：原文里定位不到的片段**丢弃**，
 * 绝不替模型编一条证据出来。
 */
final class EvidenceLocator {

    private static final Logger log = LoggerFactory.getLogger(EvidenceLocator.class);

    private EvidenceLocator() {
    }

    /**
     * @param snippets      模型照抄的片段，可为 null 或空
     * @param confirmedText 教师确认的原话；定位的基准
     * @return 能在 {@code confirmedText} 里逐字找到的片段，按原顺序；找不到的一律丢弃
     */
    static List<Evidence> locate(List<String> snippets, String confirmedText) {
        if (snippets == null || snippets.isEmpty() || confirmedText == null) {
            return List.of();
        }
        List<Evidence> located = new ArrayList<>(snippets.size());
        int dropped = 0;
        for (String snippet : snippets) {
            if (snippet == null || snippet.isBlank()) {
                dropped++;
                continue;
            }
            int start = confirmedText.indexOf(snippet);
            if (start < 0) {
                // 原文里没有这句话：视为改写或编造，不放进结果
                dropped++;
                continue;
            }
            located.add(Evidence.transcript(snippet, start, start + snippet.length()));
        }
        if (dropped > 0) {
            // 只记条数，不记内容——片段就是儿童原话，不能进日志
            log.info("模型给出的 {} 条证据里有 {} 条无法在原文中定位，已丢弃", snippets.size(), dropped);
        }
        return located;
    }
}
