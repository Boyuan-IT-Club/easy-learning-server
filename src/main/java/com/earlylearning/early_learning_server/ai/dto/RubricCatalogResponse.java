package com.earlylearning.early_learning_server.ai.dto;

import java.util.List;

import java.util.List;

import com.earlylearning.early_learning_server.ai.model.rubric.RubricCatalog;
import com.earlylearning.early_learning_server.ai.model.rubric.RubricCatalogEntry;
import com.earlylearning.early_learning_server.ai.model.task.BusinessType;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 评分条目目录：供管理员编制官方内容、教师编制私人课程时查看统一评分条目。
 *
 * <p>契约要点：目录可缓存复用，正常评估、课堂和评分前都不需要调用；
 * 没有版本查询参数、不列历史版本、不提供编辑或自动更新。
 *
 * @param aiScoreSchemaVersion 故事评分结果的冻结结构版本，固定 2
 * @param businessTypes        支持的业务种类
 */
public record RubricCatalogResponse(

        @JsonProperty("rubric_version") String rubricVersion,
        @JsonProperty("ai_score_schema_version") Integer aiScoreSchemaVersion,
        @JsonProperty("business_types") List<BusinessType> businessTypes,
        @JsonProperty("items") List<RubricCatalogItem> items) {

    /** 领域目录 → 契约形状的唯1映射点。 */
    public static RubricCatalogResponse from(RubricCatalog catalog) {
        return new RubricCatalogResponse(catalog.rubricVersion(), catalog.aiScoreSchemaVersion(),
                catalog.businessTypes(),
                catalog.items().stream().map(RubricCatalogResponse::fromEntry).toList());
    }

    private static RubricCatalogItem fromEntry(RubricCatalogEntry entry) {
        return new RubricCatalogItem(entry.itemCode(), entry.itemName(), entry.taskKind(), entry.applicability());
    }
}
