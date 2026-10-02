package com.earlylearning.early_learning_server.identity.model;

import com.earlylearning.early_learning_server.common.secret.RandomCodes;

/**
 * 激活码的形状：16 位 Crockford Base32（PRD 2.2-1），约 80 bit，不加校验位。
 * 服务端原样核对，规范化（大小写、连字符、易混字符）由客户端负责。
 */
public final class ActivationCodes {

    public static final int LENGTH = 16;

    private ActivationCodes() {
    }

    public static String generate() {
        return RandomCodes.generate(LENGTH);
    }
}
