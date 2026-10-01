package com.earlylearning.early_learning_server.material.model;

import java.time.LocalDateTime;

import com.earlylearning.early_learning_server.entity.AssessmentMaterial;
import com.earlylearning.early_learning_server.entity.ContentStatus;

/**
 * 平板端版本目录条目：含 DISABLED 的全部保留版本。
 * 客户端按编号加版本自行比对，服务端不接收客户端版本映射。
 */
public record MaterialVersionSummary(

        String officialMaterialCode,

        String contentVersion,

        String name,

        ContentStatus status,

        LocalDateTime updatedAt) {

    public static MaterialVersionSummary from(AssessmentMaterial material) {
        return new MaterialVersionSummary(
                material.getOfficialMaterialCode(),
                material.getContentVersion(),
                material.getName(),
                material.getStatus(),
                material.getUpdatedAt());
    }
}
