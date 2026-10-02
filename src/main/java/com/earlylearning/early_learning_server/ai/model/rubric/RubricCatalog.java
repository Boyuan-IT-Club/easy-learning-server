package com.earlylearning.early_learning_server.ai.model.rubric;

import java.util.List;

import com.earlylearning.early_learning_server.ai.model.task.BusinessType;

/**
 * 评分条目目录：服务端统一持有的那套规则的可读版本。
 *
 * <p>条目编号与评分结果里的 {@code item_code} 一一对应，顺序就是报告里雷达图的轴顺序。
 * ：目录可缓存复用，没有版本查询参数、不列历史版本、不提供编辑或自动更新。
 */
public record RubricCatalog(String rubricVersion,
                            int aiScoreSchemaVersion,
                            List<BusinessType> businessTypes,
                            List<RubricCatalogEntry> items) {
}
