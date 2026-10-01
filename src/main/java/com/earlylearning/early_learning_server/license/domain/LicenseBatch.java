package com.earlylearning.early_learning_server.license.domain;

import java.util.List;

/** 一次生成的整批激活码（含原码）。只进首次结果与短时重放缓存。 */
public record LicenseBatch(List<IssuedLicense> licenses) {

    /** 脱敏快照：只有这批码的 id，写入幂等表；重放过期时据此告诉管理员要撤销哪些。 */
    public LicenseBatchIds ids() {
        return new LicenseBatchIds(licenses.stream().map(IssuedLicense::id).toList());
    }
}
