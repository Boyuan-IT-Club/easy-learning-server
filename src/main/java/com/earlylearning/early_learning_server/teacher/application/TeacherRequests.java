package com.earlylearning.early_learning_server.teacher.application;

import java.time.Duration;

import com.earlylearning.early_learning_server.auth.domain.DeviceId;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.logging.RequestOrigin;
import com.earlylearning.early_learning_server.common.ratelimit.RateLimitRule;
import com.earlylearning.early_learning_server.common.ratelimit.SlidingWindowRateLimiter;

/** 教师端各入口共用的小工具：设备号解析与限流规则。 */
final class TeacherRequests {

    static final RateLimitRule LICENSE_VERIFY = new RateLimitRule("license-verify", Duration.ofMinutes(1), 10);
    static final RateLimitRule REGISTER = new RateLimitRule("register", Duration.ofMinutes(1), 5);
    static final RateLimitRule RECOVER = new RateLimitRule("recover", Duration.ofMinutes(1), 5);
    static final RateLimitRule REFRESH = new RateLimitRule("refresh", Duration.ofMinutes(1), 30);

    private TeacherRequests() {
    }

    /** 注册、恢复、刷新必须带合法的设备号；缺失或格式错按请求不合法处理。 */
    static DeviceId requireDevice(String header) {
        return DeviceId.parse(header).orElseThrow(() ->
                new BusinessException(ErrorCode.INVALID_REQUEST, "缺少或非法的 " + DeviceId.HEADER));
    }

    static void limitByIp(SlidingWindowRateLimiter limiter, RateLimitRule rule) {
        if (!limiter.tryAcquire(rule, RequestOrigin.clientIp())) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
    }
}
