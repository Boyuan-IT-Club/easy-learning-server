package com.earlylearning.early_learning_server.material.domain.config;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 问题问答活动。grammar 是题目挂的语法编号数组，指向语法要素的当前定义；
 * 发布时校验编号存在，下载时随依赖展开语法条目与图标。
 */
public record QuestioningActivity(

        @JsonProperty("activity_id") String activityId,

        @JsonProperty("type") String type,

        Config config) implements Activity {

    public static final String TYPE = "QUESTION_ANSWERING";

    public record Config(

            List<Question> questions) {

        public record Question(

                @JsonProperty("question_id") String questionId,

                String text,

                String hint,

                List<String> grammar) {
        }
    }
}
