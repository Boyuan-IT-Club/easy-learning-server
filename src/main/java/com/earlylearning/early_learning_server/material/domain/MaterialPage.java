package com.earlylearning.early_learning_server.material.domain;

import java.util.List;

/**
 * 管理端版本列表页。total 是过滤后的总条数，页码从 1 开始。
 */
public record MaterialPage(

        List<MaterialSummary> items,

        int page,

        int pageSize,

        long total) {
}
