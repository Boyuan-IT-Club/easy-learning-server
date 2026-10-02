package com.earlylearning.early_learning_server.identity.model;

import java.time.Duration;

/**
 * 刷新宽限：上一枚 refresh_token 在轮换后 30 秒内可取回同一组新凭证（契约 refreshTeacherToken）。
 *
 * <p>只有"记录里的新哈希仍等于数据库当前值"时才有效：新凭证若又轮换过一次，更早的凭证立即失效；
 * 轮换事务回滚时，这条记录也永远对不上。
 *
 * @param newRefreshHash 本次轮换得到的新 refresh 哈希
 * @param pairJson       本次轮换返回的凭证对（JSON，含明文），宽限内原样返回；不得写日志
 */
public record RefreshGrace(int userId, String newRefreshHash, String pairJson) {

    /** 契约固定 30 秒，从轮换起算，重试不延长。 */
    public static final Duration WINDOW = Duration.ofSeconds(30);

    public boolean stillCurrent(String currentRefreshHash) {
        return newRefreshHash.equals(currentRefreshHash);
    }

    @Override
    public String toString() {
        return "RefreshGrace[userId=" + userId + ", pair=REDACTED]";
    }
}
