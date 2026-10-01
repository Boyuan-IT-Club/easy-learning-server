package com.earlylearning.early_learning_server.storage.domain;

import java.util.List;

/** 官方文件的分页结果（读模型）。 */
public record FilePage(List<FileSummary> items, int page, int pageSize, long total) {
}
