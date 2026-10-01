package com.earlylearning.early_learning_server.common.idempotency;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;
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
import com.earlylearning.early_learning_server.common.web.ApiResponse;

import tools.jackson.databind.ObjectMapper;

/**
 * 含凭证的写接口的幂等（契约："激活码/凭证只允许短时内存重放，失效返回 SENSITIVE_RESULT_EXPIRED，不重复生成"）。
 *
 * <pre>
 *   首次：claim → 执行业务 → idempotency_record 存「脱敏包络」→ 提交后把完整响应体写入 Redis（短时）
 *   重放：Redis 命中 → 原样返回完整响应体（与首次逐字节相同）
 *         Redis 未命中 → 409 SENSITIVE_RESULT_EXPIRED（结果不重复生成）
 * </pre>
 *
 * <p>Redis 必须关闭持久化，否则 Token 与激活码明文会落到磁盘。
 * 本方法自己开启（或加入）事务：业务失败回滚后，占用的键一并消失，可以重试。
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

    /** 不需要定制过期详情与重放校验时的简写。 */
    public <T> StoredResponse execute(IdempotencyScope scope, String key, String fingerprint, int httpStatus,
                                      Supplier<T> action,
                                      Function<T, ApiResponse<?>> response,
                                      Function<T, ApiResponse<?>> redacted) {
        return execute(scope, key, fingerprint, httpStatus, action, response, redacted, snapshot -> null, body -> { });
    }

    /**
     * @param action         业务动作，在本事务内执行
     * @param response       完整响应（含明文），只进 Redis 与首次响应
     * @param redacted       脱敏包络，写入 idempotency_record；不得含明文凭证
     * @param expiredDetails 重放过期时，从脱敏包络 JSON 构造错误 details（如这批激活码的 id）；可返回 null
     * @param replayGuard    重放前的校验（例如刷新须再次确认账号仍可用）；校验失败直接抛异常
     */
    public <T> StoredResponse execute(IdempotencyScope scope, String key, String fingerprint, int httpStatus,
                                      Supplier<T> action,
                                      Function<T, ApiResponse<?>> response,
                                      Function<T, ApiResponse<?>> redacted,
                                      Function<String, ApiErrorDetails> expiredDetails,
                                      Consumer<String> replayGuard) {
        return transaction.execute(status -> {
            Optional<StoredResponse> replayed = idempotency.claim(scope, key, fingerprint);
            if (replayed.isPresent()) {
                StoredResponse snapshot = replayed.get();
                String full = readReplay(scope, key).orElseThrow(() -> new BusinessException(
                        ErrorCode.SENSITIVE_RESULT_EXPIRED, expiredDetails.apply(snapshot.body())));
                replayGuard.accept(full);
                return new StoredResponse(snapshot.httpStatus(), full);
            }
            T result = action.get();
            idempotency.record(scope, key, httpStatus, redacted.apply(result));
            String full = objectMapper.writeValueAsString(response.apply(result));
            AfterCommit.run("sensitive-replay", () -> writeReplay(scope, key, full));
            return new StoredResponse(httpStatus, full);
        });
    }

    private Optional<String> readReplay(IdempotencyScope scope, String key) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(redisKey(scope, key)));
        } catch (DataAccessException e) {
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "缓存暂不可用", e);
        }
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
