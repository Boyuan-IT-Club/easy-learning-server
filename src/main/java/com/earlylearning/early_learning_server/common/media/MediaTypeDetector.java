package com.earlylearning.early_learning_server.common.media;

import org.springframework.stereotype.Component;

/**
 * 按文件开头的魔数识别实际 MIME 类型。
 *
 * <p>只做嗅探，不解析整个文件；识别不出时返回 null。
 */
@Component
public class MediaTypeDetector {

    /** 需要读取的最少字节数——RIFF/WebP 与 {@code ftyp} 都要看到第 12 个字节。 */
    public static final int HEAD_BYTES = 12;

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] WEBM = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3};

    /**
     * @param head 文件开头的若干字节（建议至少 {@link #HEAD_BYTES} 个）
     * @return 识别出的 MIME；<b>无法识别时返回 null</b>，调用方据此判定为不支持的格式
     */
    public String detect(byte[] head) {
        if (head == null || head.length < 4) {
            return null;
        }
        if (startsWithAscii(head, 0, "%PDF-")) {
            return "application/pdf";
        }
        if (startsWith(head, 0, PNG)) {
            return "image/png";
        }
        if (startsWith(head, 0, JPEG)) {
            return "image/jpeg";
        }
        if (startsWithAscii(head, 0, "GIF87a") || startsWithAscii(head, 0, "GIF89a")) {
            return "image/gif";
        }
        // RIFF 容器有多个子类型，靠第 8 字节起的形式标识区分。
        if (startsWithAscii(head, 0, "RIFF")) {
            if (startsWithAscii(head, 8, "WEBP")) {
                return "image/webp";
            }
            if (startsWithAscii(head, 8, "WAVE")) {
                return "audio/wav";
            }
            return null;
        }
        if (startsWithAscii(head, 0, "OggS")) {
            return "audio/ogg";
        }
        if (startsWithAscii(head, 0, "fLaC")) {
            return "audio/flac";
        }
        // mp3：要么带 ID3 标签，要么直接是帧同步（11 位全 1）。
        if (startsWithAscii(head, 0, "ID3")) {
            return "audio/mpeg";
        }
        if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xE0) == 0xE0) {
            return "audio/mpeg";
        }
        if (startsWith(head, 0, WEBM)) {
            return "audio/webm";
        }
        // mp4 容器：brand 决定它是 m4a 音频还是视频。只接受音频品牌，避免视频被当成录音收下。
        if (startsWithAscii(head, 4, "ftyp") && head.length >= 12) {
            String brand = new String(head, 8, 4, java.nio.charset.StandardCharsets.US_ASCII);
            return switch (brand) {
                case "M4A ", "mp41", "mp42", "isom", "iso2" -> "audio/mp4";
                default -> null;
            };
        }
        return null;
    }

    private boolean startsWith(byte[] data, int offset, byte[] pattern) {
        if (data.length < offset + pattern.length) {
            return false;
        }
        for (int i = 0; i < pattern.length; i++) {
            if (data[offset + i] != pattern[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean startsWithAscii(byte[] data, int offset, String pattern) {
        return startsWith(data, offset, pattern.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }
}
