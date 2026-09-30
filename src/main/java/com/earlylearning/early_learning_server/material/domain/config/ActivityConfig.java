package com.earlylearning.early_learning_server.material.domain.config;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 冻结的 ActivityConfig v2。schema_version 固定为 2，结构变化时新增结构版本而不是改这里。
 * 校验规则：每类活动最多一个、activity_id 唯一、恰好一项故事叙述——由发布校验器保证，
 * 落库后的 JSON 不再二次校验。
 */
public record ActivityConfig(

        @JsonProperty("schema_version") int schemaVersion,

        @JsonProperty("story_context") String storyContext,

        List<Activity> activities) {

    public static final int SCHEMA_VERSION = 2;
}
