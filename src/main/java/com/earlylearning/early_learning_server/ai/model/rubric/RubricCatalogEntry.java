package com.earlylearning.early_learning_server.ai.model.rubric;

import com.earlylearning.early_learning_server.ai.model.task.TaskKind;

/**
 * 评分条目目录里的一项。
 *
 * @param itemCode      条目编号；评分结果里的 {@code item_code} 就是它
 * @param itemName      条目显示名称
 * @param taskKind      这一项属于哪种任务
 * @param applicability 适用范围说明；写给编制内容的人看，不是给程序判断的
 */
public record RubricCatalogEntry(String itemCode,
                                 String itemName,
                                 TaskKind taskKind,
                                 String applicability) {
}
