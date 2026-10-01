package com.earlylearning.early_learning_server.common.ratelimit;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.earlylearning.early_learning_server.common.secret.Tokens;

/**
 * 滑动日志限流：ZSET 里记录窗口内每一次请求的时间戳，任意时刻回看"过去 N 毫秒"的真实次数。
 *
 * <p>三步（清掉窗口外的记录、计数、记下本次）放在一个 Lua 脚本里原子执行；
 * 被拒绝的请求不计入，所以攻击者打得越凶，窗口也不会被无限延长。
 *
 * <p>subject（IP、用户名等）先做 SHA-256 再进键名：键名不会被注入分隔符，Redis 里也不留原始用户名。
 */
@Component
public class SlidingWindowRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(SlidingWindowRateLimiter.class);

    static final String KEY_PREFIX = "el:rl:";

    private static final RedisScript<Long> SCRIPT = new DefaultRedisScript<>("""
            redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, tonumber(ARGV[1]) - tonumber(ARGV[2]))
            if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[3]) then
              return 0
            end
            redis.call('ZADD', KEYS[1], ARGV[1], ARGV[4])
            redis.call('PEXPIRE', KEYS[1], ARGV[2])
            return 1
            """, Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final Clock clock;

    public SlidingWindowRateLimiter(StringRedisTemplate stringRedisTemplate, Clock clock) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.clock = clock;
    }

    /**
     * 尝试占用一次配额。
     *
     * @return 放行为 true；超限为 false。Redis 故障时放行（见包说明）
     */
    public boolean tryAcquire(RateLimitRule rule, String subject) {
        try {
            Long allowed = stringRedisTemplate.execute(SCRIPT, List.of(key(rule, subject)),
                    Long.toString(clock.millis()),
                    Long.toString(rule.window().toMillis()),
                    Integer.toString(rule.limit()),
                    UUID.randomUUID().toString());
            return allowed == null || allowed == 1L;
        } catch (DataAccessException e) {
            log.warn("限流不可用，本次放行 rule={} cause={}", rule.name(), e.getClass().getSimpleName());
            return true;
        }
    }

    /** @return 当前窗口内已记录的次数；Redis 故障时为 0 */
    public long countInWindow(RateLimitRule rule, String subject) {
        try {
            Long count = stringRedisTemplate.opsForZSet().count(key(rule, subject),
                    clock.millis() - rule.window().toMillis(), Double.POSITIVE_INFINITY);
            return count == null ? 0 : count;
        } catch (DataAccessException e) {
            log.warn("限流计数不可读 rule={} cause={}", rule.name(), e.getClass().getSimpleName());
            return 0;
        }
    }

    /** 清空某个主体在该规则下的记录，例如登录成功后清零失败计数。 */
    public void reset(RateLimitRule rule, String subject) {
        try {
            stringRedisTemplate.delete(key(rule, subject));
        } catch (DataAccessException e) {
            log.warn("限流记录清理失败 rule={} cause={}", rule.name(), e.getClass().getSimpleName());
        }
    }

    static String key(RateLimitRule rule, String subject) {
        return KEY_PREFIX + rule.name() + ":" + Tokens.sha256Hex(subject == null ? "" : subject);
    }
}
