package com.earlylearning.early_learning_server.ai.model.scoring;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 把模型照抄的原文片段定位到确认文本里,得到带偏移的证据。故事评分与单题评分共用。
 *
 * <p>模型只负责「照抄哪句话」,偏移由本类在服务端计算;片段在确认文本里逐字找不到时丢弃,
 * 不补造证据。
 *
 * <p>public:两个模型适配器(infrastructure)都使用,属共享的领域规则。
 */
public final class EvidenceLocator {

    private static final Logger log = LoggerFactory.getLogger(EvidenceLocator.class);

    private EvidenceLocator() {
    }

    /**
     * @param snippets      模型照抄的片段，可为 null 或空
     * @param confirmedText 教师确认的原话；定位的基准
     * @return 能在 {@code confirmedText} 里逐字找到的片段，按原顺序；找不到的一律丢弃
     */
    public static List<Evidence> locate(List<String> snippets, String confirmedText) {
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
