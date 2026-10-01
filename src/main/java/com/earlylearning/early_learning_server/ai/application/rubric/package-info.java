/**
 * 统一评分目录的应用服务。作为命名接口暴露：评估材料发布时用它校验
 * 图片分组映射的评分条目是否在统一规则中。目录本身无状态，只读配置。
 */
@org.springframework.modulith.NamedInterface("rubric")
package com.earlylearning.early_learning_server.ai.application.rubric;
