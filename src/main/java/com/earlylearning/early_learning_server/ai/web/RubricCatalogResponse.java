package com.earlylearning.early_learning_server.ai.web;

import java.util.List;

import com.earlylearning.early_learning_server.ai.task.BusinessType;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 评分条目目录：供管理员编制官方内容、教师编制私人课程时查看统一评分条目。
 *
 * <p>契约要点：目录**可缓存复用**，正常评估、课堂和评分前都**不需要**调用；
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
}
