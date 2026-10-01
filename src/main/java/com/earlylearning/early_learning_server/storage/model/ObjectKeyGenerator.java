package com.earlylearning.early_learning_server.storage.model;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.earlylearning.early_learning_server.enums.CloudFileKind;

/**
 * 生成对象存储路径 object_key：{@code <类型>/<年>/<月>/<uuid>}。
 *
 * <p>只接受文件类型，不接受任何客户端输入。
 */
@Component
public class ObjectKeyGenerator {

    private final Clock clock;

    public ObjectKeyGenerator() {
        this(Clock.systemUTC());
    }

    ObjectKeyGenerator(Clock clock) {
        this.clock = clock;
    }

    public String next(CloudFileKind kind) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        return "%s/%04d/%02d/%s".formatted(
                kind.value().toLowerCase(), today.getYear(), today.getMonthValue(), UUID.randomUUID());
    }
}
