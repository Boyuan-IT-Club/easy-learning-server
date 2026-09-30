package com.earlylearning.early_learning_server.common.idempotency;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.tx.AfterCommit;

import tools.jackson.databind.ObjectMapper;

/**
 * 含凭证的写接口的幂等：结果里有 Token、激活码等明文，不能整份落库。
 *
 * <pre>
 *   首次：claim → 执行业务 → idempotency_record 存「脱敏快照」→ 提交后把完整结果写入 Redis（短时）
 *   重放：Redis 命中 → 原样返回完整结果
 *         Redis 未命中 → 409 SENSITIVE_RESULT_EXPIRED（结果不重复生成）
 * </pre>
 *
 * <p>契约原文是"激活码/凭证只允许短时内存重放"。Redis 必须关闭持久化，否则明文会落到磁盘。
 *
 * <p>本方法自己开启（或加入）事务：业务动作在同一事务里执行，失败回滚后占用的键一并消失，可以重试。
 */
@Component
public class SensitiveIdempotency {

    private static final Logger log = LoggerFactory.getLogger(SensitiveIdempotency.class);

    static final String KEY_PREFIX = "el:idem:sensitive:";

    private final IdempotencyService idempotency;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;
    private final Duration replayTtl;

    public SensitiveIdempotency(IdempotencyService idempotency,
                                StringRedisTemplate redis,
                                ObjectMapper objectMapper,
                                PlatformTransactionManager transactionManager,
                                SensitiveIdempotencyProperties properties) {
        this.idempotency = idempotency;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.transaction = new TransactionTemplate(transactionManager);
        this.replayTtl = properties.sensitiveReplayTtl();
    }

    /**
     * @param httpStatus 首次成功的状态码，写入幂等记录
     * @param type       结果类型，重放时按它反序列化
     * @param action     业务动作，在本事务内执行
     * @param redact     把结果变成可以落库的脱敏快照（不得含明文凭证）
     */
    public <T> T execute(IdempotencyScope scope, String key, String fingerprint, int httpStatus,
                         Class<T> type, Supplier<T> action, Function<T, Object> redact) {
        return execute(scope, key, fingerprint, httpStatus, type, action, redact, snapshot -> null);
    }

    /**
     * @param expiredDetails 重放过期时，从脱敏快照 JSON 构造错误 details（如给出这批激活码的 id）；可返回 null
     */
    public <T> T execute(IdempotencyScope scope, String key, String fingerprint, int httpStatus,
                         Class<T> type, Supplier<T> action, Function<T, Object> redact,
                         Function<String, ApiErrorDetails> expiredDetails) {
        return transaction.execute(status -> {
            Optional<StoredResponse> replayed = idempotency.claim(scope, key, fingerprint);
            if (replayed.isPresent()) {
                return readReplay(scope, key, type).orElseThrow(() -> new BusinessException(
                        ErrorCode.SENSITIVE_RESULT_EXPIRED,
                        expiredDetails.apply(replayed.get().body())));
            }
            T result = action.get();
            idempotency.record(scope, key, httpStatus, redact.apply(result));
            String full = objectMapper.writeValueAsString(result);
            AfterCommit.run("sensitive-replay", () -> writeReplay(scope, key, full));
            return result;
        });
    }

    private <T> Optional<T> readReplay(IdempotencyScope scope, String key, Class<T> type) {
        String body;
        try {
            body = redis.opsForValue().get(redisKey(scope, key));
        } catch (DataAccessException e) {
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "缓存暂不可用", e);
        }
        return body == null ? Optional.empty() : Optional.of(objectMapper.readValue(body, type));
    }

    private void writeReplay(IdempotencyScope scope, String key, String full) {
        try {
            redis.opsForValue().set(redisKey(scope, key), full, replayTtl);
        } catch (DataAccessException e) {
            // 首次响应照常返回；只是之后的重放会得到 SENSITIVE_RESULT_EXPIRED
            log.error("敏感结果重放缓存写入失败 scope={} cause={}", scope.value(), e.getClass().getSimpleName());
        }
    }

    private static String redisKey(IdempotencyScope scope, String key) {
        return KEY_PREFIX + scope.value() + ":" + InputFingerprint.sha256Hex(key);
    }
}
