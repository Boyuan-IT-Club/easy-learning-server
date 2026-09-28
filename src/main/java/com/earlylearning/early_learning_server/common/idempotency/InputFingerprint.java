package com.earlylearning.early_learning_server.common.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 输入指纹：把构成「同一次请求」的输入压成稳定的 SHA256，用于判定同键是否同输入。
 *
 * <p>调用方给出语义上区分请求的输入即可，顺序固定，<b>不要放入时间戳等每次都会变的值</b>——
 * 那会让每次重试都判成「输入已改变」。上传接口应直接复用已经算好的文件 SHA256。
 */
public final class InputFingerprint {

    private InputFingerprint() {
    }

    /**
     * 拼接各段后取 SHA256。
     *
     * <p>每段带长度前缀分隔，避免 {@code ("ab", "c")} 与 {@code ("a", "bc")} 撞成同一指纹。
     */
    public static String of(String... parts) {
        StringBuilder joined = new StringBuilder();
        for (String part : parts) {
            int length = part == null ? -1 : part.length();
            joined.append(length).append(':').append(part).append('\u0000');
        }
        return sha256Hex(joined.toString());
    }

    /** 复用上传流程里已经算过的文件哈希，避免为了指纹再读一遍文件。 */
    public static String sha256Hex(byte[] bytes) {
        return HexFormat.of().formatHex(digest().digest(bytes));
    }

    public static String sha256Hex(String text) {
        return sha256Hex(text.getBytes(StandardCharsets.UTF_8));
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("运行环境缺少 SHA-256", e);
        }
    }
}
