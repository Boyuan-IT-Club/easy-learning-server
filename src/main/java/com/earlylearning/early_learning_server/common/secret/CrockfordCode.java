package com.earlylearning.early_learning_server.common.secret;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Optional;

/**
 * 人工抄写用的随机码：Crockford Base32 字符集，末位为校验位。
 *
 * <p>字符集去掉了 I、L、O、U，抄错率低；输入时把 O 当 0、I 和 L 当 1，忽略连字符与空白。
 * 校验位是按位加权和对 32 取模：能拦下单字符抄错和绝大多数相邻字符对调，
 * 让客户端在发请求前就发现输错，而不是把每次手误都变成一次服务端查询。
 */
public final class CrockfordCode {

    static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    private static final SecureRandom RANDOM = new SecureRandom();

    private CrockfordCode() {
    }

    /**
     * 生成随机码。
     *
     * @param length 含校验位的总长度
     * @return 规范形式（大写、无分隔符）
     */
    public static String generate(int length) {
        if (length < 2) {
            throw new IllegalArgumentException("码长至少为 2（含校验位）");
        }
        StringBuilder payload = new StringBuilder(length);
        for (int i = 0; i < length - 1; i++) {
            payload.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return payload.append(checkChar(payload)).toString();
    }

    /**
     * 把用户输入还原成规范形式，并校验长度与校验位。
     *
     * @return 规范形式；字符非法、长度不符或校验位不对时为空
     */
    public static Optional<String> parse(String input, int length) {
        if (input == null) {
            return Optional.empty();
        }
        StringBuilder normalized = new StringBuilder(length);
        for (char raw : input.toUpperCase(Locale.ROOT).toCharArray()) {
            if (raw == '-' || Character.isWhitespace(raw)) {
                continue;
            }
            char c = switch (raw) {
                case 'O' -> '0';
                case 'I', 'L' -> '1';
                default -> raw;
            };
            if (ALPHABET.indexOf(c) < 0 || normalized.length() == length) {
                return Optional.empty();
            }
            normalized.append(c);
        }
        if (normalized.length() != length) {
            return Optional.empty();
        }
        CharSequence payload = normalized.subSequence(0, length - 1);
        return normalized.charAt(length - 1) == checkChar(payload)
                ? Optional.of(normalized.toString())
                : Optional.empty();
    }

    /** 每 4 位插一个连字符，便于抄写：{@code 7K2QM9XD} → {@code 7K2Q-M9XD}。 */
    public static String format(String normalized) {
        StringBuilder formatted = new StringBuilder(normalized.length() + normalized.length() / 4);
        for (int i = 0; i < normalized.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                formatted.append('-');
            }
            formatted.append(normalized.charAt(i));
        }
        return formatted.toString();
    }

    static char checkChar(CharSequence payload) {
        int sum = 0;
        for (int i = 0; i < payload.length(); i++) {
            sum += (i + 1) * ALPHABET.indexOf(payload.charAt(i));
        }
        return ALPHABET.charAt(sum % ALPHABET.length());
    }
}
