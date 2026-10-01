package com.earlylearning.early_learning_server.material.model;

import java.time.LocalDateTime;

import com.earlylearning.early_learning_server.material.entity.AssessmentMaterial;
import com.earlylearning.early_learning_server.material.entity.ContentStatus;

/**
 * 管理端列表的版本概要。不含活动配置；完整配置走指定编号加版本的下载接口。
 */
public record MaterialSummary(

        Integer id,

        String officialMaterialCode,

        String contentVersion,

        String name,

        ContentStatus status,

        LocalDateTime createdAt,

        LocalDateTime updatedAt) {

    public static MaterialSummary from(AssessmentMaterial material) {
        return new MaterialSummary(
                material.getId(),
                material.getOfficialMaterialCode(),
                material.getContentVersion(),
                material.getName(),
                material.getStatus(),
                material.getCreatedAt(),
                material.getUpdatedAt());
    }
}
