package com.earlylearning.early_learning_server.storage.model;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudFileCodeGeneratorTests {

    /** 契约 CloudFileCode 的 pattern，另加迁移里 VARCHAR(64) 的长度上限。 */
    private static final Pattern CONTRACT_PATTERN = Pattern.compile("^CF_[A-Za-z0-9_-]+$");

    private final CloudFileCodeGenerator generator = new CloudFileCodeGenerator();

    @Test
    void codeMatchesContractPatternAndFitsColumn() {
        for (int i = 0; i < 50; i++) {
            String code = generator.next();
            assertTrue(CONTRACT_PATTERN.matcher(code).matches(), code);
            assertTrue(code.length() <= 64, code);
        }
    }

    @Test
    void codesAreDistinct() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            assertTrue(seen.add(generator.next()), "编号重复");
        }
        assertEquals(1000, seen.size());
    }

    @Test
    void cloudNamespaceAcceptsCfAndRejectsLocalNamespace() {
        assertTrue(CloudFileCodeGenerator.isCloudFileCode(generator.next()));
        // LF_ 是平板本地自产文件的命名空间，签约接口会直接拒绝；云端编号绝不能长这样。
        assertFalse(CloudFileCodeGenerator.isCloudFileCode("LF_20260927_ABCDEF"));
        assertFalse(CloudFileCodeGenerator.isCloudFileCode(null));
    }
}
