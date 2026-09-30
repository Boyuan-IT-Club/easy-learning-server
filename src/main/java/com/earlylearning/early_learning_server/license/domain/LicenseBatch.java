package com.earlylearning.early_learning_server.license.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 一次批量生成的结果，含激活码明文。
 *
 * <p>只在生成接口的首次响应（以及 Redis 里 10 分钟的重放缓存）中存在；落库的幂等快照用 {@link #redacted()}。
 */
public record LicenseBatch(@JsonProperty("licenses") List<Issued> licenses) {

    /** 脱敏快照：只有 id，不含明文，过期重放时用它告诉管理员是哪一批。 */
    public Map<String, Object> redacted() {
        return Map.of("license_ids", licenses.stream().map(Issued::id).toList());
    }

    public record Issued(@JsonProperty("id") int id,
                         @JsonProperty("activation_code") String activationCode,
                         @JsonProperty("code_hint") String codeHint,
                         @JsonProperty("status") LicenseStatus status,
                         @JsonProperty("remark") String remark,
                         @JsonProperty("created_at") Instant createdAt) {

        @Override
        public String toString() {
            return "Issued[id=" + id + ", codeHint=" + codeHint + "]";
        }
    }
}
