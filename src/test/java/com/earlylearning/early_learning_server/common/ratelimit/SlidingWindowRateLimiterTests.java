package com.earlylearning.early_learning_server.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 真实 Redis 上的滑动窗口：用可拨动的时钟验证"最早那次滑出窗口后恢复"。
 * 不注入 Spring 的时钟 Bean，直接用自己的实例，避免影响其他测试。
 */
@SpringBootTest
class SlidingWindowRateLimiterTests {

    @Autowired
    private StringRedisTemplate redis;

    private MutableClock clock;
    private SlidingWindowRateLimiter limiter;
    private String subject;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-09-30T00:00:00Z"));
        limiter = new SlidingWindowRateLimiter(redis, clock);
        subject = UUID.randomUUID().toString();
    }

    @Test
    void A28_窗口内超过上限被拒_最早一次滑出后恢复() {
        RateLimitRule rule = new RateLimitRule("test-window", Duration.ofSeconds(60), 10);
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryAcquire(rule, subject)).isTrue();
            clock.advance(Duration.ofSeconds(5));
        }
        // 现在是第 50 秒：过去 60 秒内已有 10 次
        assertThat(limiter.tryAcquire(rule, subject)).isFalse();
        // 第 61 秒：第 0 秒那次已滑出窗口
        clock.advance(Duration.ofSeconds(11));
        assertThat(limiter.tryAcquire(rule, subject)).isTrue();
        assertThat(limiter.tryAcquire(rule, subject)).isFalse();
    }

    @Test
    void 被拒绝的请求不计入() {
        RateLimitRule rule = new RateLimitRule("test-rejected", Duration.ofSeconds(10), 1);
        assertThat(limiter.tryAcquire(rule, subject)).isTrue();
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire(rule, subject)).isFalse();
        }
        clock.advance(Duration.ofSeconds(11));
        assertThat(limiter.tryAcquire(rule, subject)).isTrue();
    }

    @Test
    void 不同主体互不影响() {
        RateLimitRule rule = new RateLimitRule("test-subjects", Duration.ofSeconds(10), 1);
        assertThat(limiter.tryAcquire(rule, subject)).isTrue();
        assertThat(limiter.tryAcquire(rule, subject + "-other")).isTrue();
    }

    @Test
    void 键名不含原始主体() {
        RateLimitRule rule = new RateLimitRule("test-key", Duration.ofSeconds(10), 1);
        assertThat(SlidingWindowRateLimiter.key(rule, "zhang_li")).doesNotContain("zhang_li");
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
