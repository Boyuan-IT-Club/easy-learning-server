package com.earlylearning.early_learning_server.teacher.domain;

import java.time.Instant;

/** 教师账号的只读快照：不含 refresh 哈希。对外返回与幂等快照都用它，实体不离开应用层。 */
public record TeacherAccountSummary(int id, String username, TeacherStatus status, Instant createdAt) {

    public static TeacherAccountSummary of(TeacherAccount account) {
        return new TeacherAccountSummary(account.getId(), account.getUsername(), account.getStatus(),
                account.getCreatedAt());
    }
}
