package com.earlylearning.early_learning_server.material.domain.config;

/**
 * 活动配置的三种形态。type 字段是契约的判别值，随记录一起序列化。
 */
public sealed interface Activity permits SortingActivity, QuestioningActivity, NarrationActivity {

    String activityId();

    String type();
}
