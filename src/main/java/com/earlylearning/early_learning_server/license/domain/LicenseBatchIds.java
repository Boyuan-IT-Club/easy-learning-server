package com.earlylearning.early_learning_server.license.domain;

import java.util.List;

/** {@link LicenseBatch} 的脱敏快照：只有 id，不含原码与哈希。 */
public record LicenseBatchIds(List<Integer> ids) {
}
