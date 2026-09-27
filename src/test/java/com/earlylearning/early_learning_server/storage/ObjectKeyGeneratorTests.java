package com.earlylearning.early_learning_server.storage;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectKeyGeneratorTests {

    private static final Pattern KEY_PATTERN = Pattern.compile("^(audio|pdf|image)/\\d{4}/\\d{2}/[0-9a-f-]{36}$");

    private final ObjectKeyGenerator generator = new ObjectKeyGenerator();

    @Test
    void keyFollowsKindYearMonthUuidShape() {
        for (CloudFileKind kind : CloudFileKind.values()) {
            String key = generator.next(kind);
            assertTrue(KEY_PATTERN.matcher(key).matches(), key);
            assertTrue(key.startsWith(kind.value().toLowerCase() + "/"), key);
        }
    }

    @Test
    void keysAreDistinct() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            assertTrue(seen.add(generator.next(CloudFileKind.IMAGE)), "对象路径重复");
        }
    }
}
