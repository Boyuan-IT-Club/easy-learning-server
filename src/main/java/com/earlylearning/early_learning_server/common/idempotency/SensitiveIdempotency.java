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

import tools.jackson.databind.ObjectMapper;

/**
 * 含凭证的写操作的幂等（契约："激活码/凭证只允许短时内存重放，失效返回 SENSITIVE_RESULT_EXPIRED，不重复生成"）。
 *
 * <pre>
 *   首次：claim → 执行业务 → idempotency_record 存「脱敏快照」→ 提交后把完整结果写入 Redis（短时）
 *   重放：Redis 命中 → 还原完整结果（与首次相同）
 *         Redis 未命中 → 409 SENSITIVE_RESULT_EXPIRED（结果不重复生成）
 * </pre>
 *
 * <p>与 {@link IdempotencyService} 一样，结果与快照都是调用方的领域对象，不假定 HTTP 形状；
 * 转成响应是调用方 interfaces 层的事，重放时按同样方式映射即可。
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

    /**
     * @param httpStatus     首次成功的状态码，随快照落库（与 {@link IdempotencyService#record} 一致）
     * @param resultType     完整结果的类型；完整结果只进 Redis 与首次返回
     * @param action         业务动作，在本事务内执行
     * @param snapshotType   脱敏快照的类型
     * @param snapshot       从完整结果得到脱敏快照，写入 idempotency_record；不得含明文凭证
     * @param expiredDetails 重放过期时，从脱敏快照构造错误 details（如这批激活码的 id）；可返回 null
     * @param replayGuard    重放前的校验（例如刷新须再次确认账号仍可用）；校验失败直接抛异常
     */
    public <T, S> T execute(IdempotencyScope scope, String key, String fingerprint, int httpStatus,
                            Class<T> resultType, Supplier<T> action,
                            Class<S> snapshotType, Function<T, S> snapshot,
                            Function<S, ApiErrorDetails> expiredDetails,
                            Consumer<T> replayGuard) {
        return transaction.execute(status -> {
            Optional<StoredResponse> replayed = idempotency.claim(scope, key, fingerprint);
            if (replayed.isPresent()) {
                T full = readReplay(scope, key, resultType).orElseThrow(() -> new BusinessException(
                        ErrorCode.SENSITIVE_RESULT_EXPIRED,
                        expiredDetails.apply(objectMapper.readValue(replayed.get().body(), snapshotType))));
                replayGuard.accept(full);
                return full;
            }
            T result = action.get();
            idempotency.record(scope, key, httpStatus, snapshot.apply(result));
            String json = objectMapper.writeValueAsString(result);
            AfterCommit.run("sensitive-replay", () -> writeReplay(scope, key, json));
            return result;
        });
    }

    /** 不需要定制过期详情与重放校验时的简写。 */
    public <T, S> T execute(IdempotencyScope scope, String key, String fingerprint, int httpStatus,
                            Class<T> resultType, Supplier<T> action,
                            Class<S> snapshotType, Function<T, S> snapshot) {
        return execute(scope, key, fingerprint, httpStatus, resultType, action, snapshotType, snapshot,
                s -> null, result -> { });
    }

    private <T> Optional<T> readReplay(IdempotencyScope scope, String key, Class<T> type) {
        String json;
        try {
            json = redis.opsForValue().get(redisKey(scope, key));
        } catch (DataAccessException e) {
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "缓存暂不可用", e);
        }
        return Optional.ofNullable(json).map(value -> objectMapper.readValue(value, type));
    }

    private void writeReplay(IdempotencyScope scope, String key, String json) {
        try {
            redis.opsForValue().set(redisKey(scope, key), json, replayTtl);
        } catch (DataAccessException e) {
            // 首次结果照常返回；只是之后的重放会得到 SENSITIVE_RESULT_EXPIRED
            log.error("敏感结果重放缓存写入失败 scope={} cause={}", scope.value(), e.getClass().getSimpleName());
        }
    }

    private static String redisKey(IdempotencyScope scope, String key) {
        return KEY_PREFIX + scope.value() + ":" + InputFingerprint.sha256Hex(key);
    }
}
