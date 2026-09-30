package com.earlylearning.early_learning_server.license.domain;

import java.util.List;
import java.util.Map;

/**
 * 激活码分页结果。
 *
 * @param counts 各状态的总数（不受状态筛选影响），供后台顶部的状态标签使用
 */
public record LicensePage(List<LicenseSummary> items,
                          long total,
                          int page,
                          int size,
                          Map<LicenseStatus, Long> counts) {
}
