package com.earlylearning.early_learning_server.admin.domain;

import java.time.Instant;

/** 管理员账号的只读快照：不含密码哈希。对外返回与幂等快照都用它，实体不离开应用层。 */
public record AdminAccountSummary(int id, String username, AdminStatus status, Instant createdAt, Instant updatedAt) {

    public static AdminAccountSummary of(AdminAccount account) {
        return new AdminAccountSummary(account.getId(), account.getUsername(), account.getStatus(),
                account.getCreatedAt(), account.getUpdatedAt());
    }
}
