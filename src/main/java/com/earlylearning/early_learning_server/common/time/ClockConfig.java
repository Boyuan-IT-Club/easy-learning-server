package com.earlylearning.early_learning_server.common.time;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 默认 UTC 系统时钟；测试里声明自己的 {@link Clock} Bean 即可覆盖。 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }
}
