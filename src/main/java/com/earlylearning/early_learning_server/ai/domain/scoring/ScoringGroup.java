package com.earlylearning.early_learning_server.ai.domain.scoring;

import java.util.List;

/**
 * 故事里的一个图片分组。
 *
 * @param contentItemId  叙事活动内唯一的分组编号
 * @param imageFileCodes 该分组的实际图片编号，顺序即展示顺序
 * @param rubricItemCode 统一评分依据中的图片评分条目编号；所有故事共用，靠它把本故事的图片分组映射到统一规则
 */
public record ScoringGroup(String contentItemId,
                           List<String> imageFileCodes,
                           String rubricItemCode) {
}
