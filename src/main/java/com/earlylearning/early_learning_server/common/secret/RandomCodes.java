package com.earlylearning.early_learning_server.common.secret;

import java.security.SecureRandom;

/**
 * 人工抄写用的随机码：Crockford Base32 大写字符集（去掉 I、L、O、U，抄错率低），无分隔符。
 *
 * <p>契约要求激活码"原样校验"，服务端不做任何规范化；把用户输入转成大写、去空白与连字符是客户端的事。
 */
public final class RandomCodes {

    static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    private static final SecureRandom RANDOM = new SecureRandom();

    private RandomCodes() {
    }

    public static String generate(int length) {
        if (length < 1) {
            throw new IllegalArgumentException("码长至少为 1");
        }
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
