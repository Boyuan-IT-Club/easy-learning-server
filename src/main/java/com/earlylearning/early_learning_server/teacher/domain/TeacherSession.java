package com.earlylearning.early_learning_server.teacher.domain;

import java.util.Map;

import com.earlylearning.early_learning_server.auth.domain.TokenPair;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 注册或恢复成功后得到的会话：账号信息 + 一对新凭证（明文）。
 *
 * <p>只在首次响应与 Redis 的短时重放缓存里存在；落库的幂等快照用 {@link #redacted()}。
 *
 * @param deviceRebound 恢复时是否把账号绑定到了新设备；注册时为 false
 */
public record TeacherSession(@JsonProperty("user_id") int userId,
                             @JsonProperty("username") String username,
                             @JsonProperty("device_rebound") boolean deviceRebound,
                             @JsonProperty("tokens") TokenPair tokens) {

    public Map<String, Object> redacted() {
        return Map.of("user_id", userId, "username", username, "device_rebound", deviceRebound);
    }

    @Override
    public String toString() {
        return "TeacherSession[userId=" + userId + ", tokens=REDACTED]";
    }
}
