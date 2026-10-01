package com.earlylearning.early_learning_server.storage.domain;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * 生成文件编号 file_code：{@code CF_<UTC日期>_<12 位随机>}。
 *
 * <p>前缀必须是 CF_：LF_ 是平板本地文件的命名空间，云端使用它会被签名接口拒绝。
 */
@Component
public class CloudFileCodeGenerator {

    private static final String PREFIX = "CF_";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int RANDOM_LENGTH = 12;

    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public CloudFileCodeGenerator() {
        this(Clock.systemUTC());
    }

    CloudFileCodeGenerator(Clock clock) {
        this.clock = clock;
    }

    public String next() {
        StringBuilder code = new StringBuilder(PREFIX)
                .append(LocalDate.now(clock.withZone(ZoneOffset.UTC)).format(DATE))
                .append('_');
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            code.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }

    /** 校验一个编号是否属于云端命名空间；测试与数据校对用。 */
    public static boolean isCloudFileCode(String fileCode) {
        return fileCode != null
                && fileCode.length() <= 64
                && fileCode.toUpperCase(Locale.ROOT).startsWith(PREFIX);
    }
}
