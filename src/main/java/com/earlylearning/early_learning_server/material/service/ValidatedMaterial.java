package com.earlylearning.early_learning_server.material.service;

import com.earlylearning.early_learning_server.material.model.config.ActivityConfig;

/**
 * config.json 校验通过后的产物：材料的三列基本信息加冻结格式的活动配置。
 * 配置里的文件引用已由解析器把包内文件名换成 CF_ 编号。
 */
public record ValidatedMaterial(

        String officialMaterialCode,

        String contentVersion,

        String name,

        ActivityConfig activityConfig) {
}
