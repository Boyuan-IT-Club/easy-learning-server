package com.earlylearning.early_learning_server.material.model;

import java.time.LocalDateTime;

import com.earlylearning.early_learning_server.entity.ContentStatus;

/**
 * 语法条目在下载清单里的当前定义。语法要素模块尚未提供管理接口，
 * 本模块按只读方式直接查表，供评估材料的依赖展开使用。
 */
public record GrammarDefinition(

        String grammarCode,

        String name,

        int version,

        String iconFileCode,

        ContentStatus status,

        LocalDateTime createdAt,

        LocalDateTime updatedAt) {
}
