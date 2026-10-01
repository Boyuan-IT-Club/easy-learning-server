package com.earlylearning.early_learning_server.auth.domain;

import java.time.Instant;

import com.earlylearning.early_learning_server.teacher.domain.TeacherAccountSummary;

/**
 * 注册与刷新的结果（契约 {@code TokenPair}）：两枚 Token 明文、access 到期时间与账号快照。
 * refresh_token 不设到期时间。含凭证明文：只进首次结果、短时重放缓存与刷新宽限，不得写日志。
 */
public record TeacherTokenPair(String accessToken, String refreshToken, Instant accessExpiresAt,
                               TeacherAccountSummary user) {

    public static TeacherTokenPair of(TeacherTokens tokens, TeacherAccountSummary user) {
        return new TeacherTokenPair(tokens.access().value(), tokens.refresh().value(), tokens.access().expiresAt(), user);
    }

    @Override
    public String toString() {
        return "TeacherTokenPair[tokens=REDACTED, user=" + user + "]";
    }
}
