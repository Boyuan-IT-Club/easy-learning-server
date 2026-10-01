package com.earlylearning.early_learning_server.common.ratelimit;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.earlylearning.early_learning_server.common.secret.Tokens;

/**
 * 失败锁定：窗口内失败达到上限后锁定一段时间，锁定期间直接拒绝。
 *
 * <p>失败次数复用 {@link SlidingWindowRateLimiter} 的滑动窗口：窗口内失败达到上限即上锁。
 * 只在失败时计数，所以正常用户输对一次就不受影响。
 */
@Component
public class FailureLockout {

    private static final Logger log = LoggerFactory.getLogger(FailureLockout.class);

    private final SlidingWindowRateLimiter slidingWindowRateLimiter;
    private final StringRedisTemplate stringRedisTemplate;

    public FailureLockout(SlidingWindowRateLimiter slidingWindowRateLimiter, StringRedisTemplate stringRedisTemplate) {
        this.slidingWindowRateLimiter = slidingWindowRateLimiter;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /** @return 仍在锁定中时给出剩余时长；未锁定或 Redis 故障时为空 */
    public Optional<Duration> lockedFor(RateLimitRule rule, String subject) {
        try {
            Long millis = stringRedisTemplate.getExpire(lockKey(rule, subject), TimeUnit.MILLISECONDS);
            return millis != null && millis > 0 ? Optional.of(Duration.ofMillis(millis)) : Optional.empty();
        } catch (DataAccessException e) {
            log.warn("锁定状态不可读，本次放行 rule={} cause={}", rule.name(), e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * 记一次失败。
     *
     * @return 本次失败后是否进入锁定
     */
    public boolean recordFailure(RateLimitRule rule, String subject, Duration lockDuration) {
        slidingWindowRateLimiter.tryAcquire(rule, subject);
        // 第 limit 次失败即上锁（"失败 5 次锁定"指第 5 次之后，而不是第 6 次）
        if (slidingWindowRateLimiter.countInWindow(rule, subject) < rule.limit()) {
            return false;
        }
        try {
            stringRedisTemplate.opsForValue().set(lockKey(rule, subject), "1", lockDuration);
            slidingWindowRateLimiter.reset(rule, subject);
        } catch (DataAccessException e) {
            log.warn("锁定写入失败 rule={} cause={}", rule.name(), e.getClass().getSimpleName());
        }
        return true;
    }

    /** 成功后清零失败计数。 */
    public void recordSuccess(RateLimitRule rule, String subject) {
        slidingWindowRateLimiter.reset(rule, subject);
    }

    private static String lockKey(RateLimitRule rule, String subject) {
        return SlidingWindowRateLimiter.KEY_PREFIX + "lock:" + rule.name() + ":"
                + Tokens.sha256Hex(subject == null ? "" : subject);
    }
}
