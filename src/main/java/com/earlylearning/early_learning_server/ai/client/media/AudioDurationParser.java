package com.earlylearning.early_learning_server.ai.client.media;

import java.nio.charset.StandardCharsets;
import java.util.OptionalLong;

import org.springframework.stereotype.Component;

/**
 * 从内存中的音频字节读出时长（毫秒）。
 *
 * <p>转写接口要求音频只在内存处理、不写临时文件，所以不能用需要文件的解析库。
 * 这里只覆盖能可靠解析的容器；{@link #supports} 之外的格式不返回结果，由调用方拒收——
 * 宁可拒收，也不在没校验时长的情况下放行。
 */
@Component
public class AudioDurationParser {

    private static final int WAV_HEADER_BYTES = 12;
    private static final int BOX_HEADER_BYTES = 8;

    /** MPEG1 Layer III 的码率表（kbps）；下标 0 是 free、15 是非法。 */
    private static final int[] MPEG1_LAYER3_BITRATES =
            {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, -1};

    /** MPEG2 / MPEG2.5 Layer III 的码率表（kbps）。 */
    private static final int[] MPEG2_LAYER3_BITRATES =
            {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, -1};

    public boolean supports(String mimeType) {
        return switch (normalize(mimeType)) {
            case "audio/wav", "audio/x-wav", "audio/wave",
                 "audio/mp4", "audio/m4a", "audio/x-m4a",
                 "audio/mpeg", "audio/mp3" -> true;
            default -> false;
        };
    }

    /** @return 毫秒；格式不支持或结构无法解析时为 empty */
    public OptionalLong parse(byte[] audio, String mimeType) {
        String mime = normalize(mimeType);
        if (audio == null) {
            return OptionalLong.empty();
        }
        return switch (mime) {
            case "audio/wav", "audio/x-wav", "audio/wave" -> wavDuration(audio);
            case "audio/mp4", "audio/m4a", "audio/x-m4a" -> m4aDuration(audio);
            case "audio/mpeg", "audio/mp3" -> mp3Duration(audio);
            default -> OptionalLong.empty();
        };
    }

    /**
     * mp3：先跳过 ID3v2 标签找到第一个帧头。
     *
     * <p>若是 VBR（第一帧里带 Xing/Info 段），用帧数算；否则按首帧的码率对音频字节数做估算——
     * 估算对 CBR 足够准，对伪装成 CBR 的 VBR 会偏小，而这里的用途只是"是否超过上限"。
     */
    private OptionalLong mp3Duration(byte[] audio) {
        int start = skipId3v2(audio);
        int frame = findFrameSync(audio, start);
        if (frame < 0 || frame + 4 > audio.length) {
            return OptionalLong.empty();
        }
        int header = (int) readUint32(audio, frame);
        int versionBits = (header >> 19) & 0x3;
        int layerBits = (header >> 17) & 0x3;
        int bitrateIndex = (header >> 12) & 0xF;
        int sampleRateIndex = (header >> 10) & 0x3;
        int padding = (header >> 9) & 0x1;
        boolean hasCrc = ((header >> 16) & 0x1) == 0;

        // 只处理 Layer III；其它层不是录音的常见形态
        if (versionBits == 1 || layerBits != 1) {
            return OptionalLong.empty();
        }
        boolean mpeg1 = versionBits == 3;
        int bitrateKbps = mpeg1 ? MPEG1_LAYER3_BITRATES[bitrateIndex] : MPEG2_LAYER3_BITRATES[bitrateIndex];
        int sampleRate = sampleRateTable(versionBits, sampleRateIndex);
        if (bitrateKbps <= 0 || sampleRate <= 0) {
            return OptionalLong.empty();
        }
        int samplesPerFrame = mpeg1 ? 1152 : 576;

        OptionalLong fromXing = readXingFrameCount(audio, frame, samplesPerFrame, sampleRate,
                mpeg1, isMono(header), hasCrc);
        if (fromXing.isPresent()) {
            return fromXing;
        }

        long audioBytes = audio.length - start;
        if (audioBytes <= 0) {
            return OptionalLong.empty();
        }
        long frameBytes = mpeg1 ? 144L * bitrateKbps * 1000 / sampleRate + padding
                : 72L * bitrateKbps * 1000 / sampleRate + padding;
        if (frameBytes <= 0) {
            return OptionalLong.empty();
        }
        // 用"整段字节 / 单帧字节"推帧数，比直接用码率更贴近实际帧长
        long frames = Math.max(1, audioBytes / frameBytes);
        return OptionalLong.of(Math.round(frames * samplesPerFrame * 1000.0 / sampleRate));
    }

    /** VBR 的第一帧里会带 Xing/Info 段，其中的帧数是最可靠的时长依据。 */
    private OptionalLong readXingFrameCount(byte[] audio, int frame, int samplesPerFrame,
                                            int sampleRate, boolean mpeg1, boolean mono, boolean hasCrc) {
        int sideInfo = mpeg1 ? (mono ? 17 : 32) : (mono ? 9 : 17);
        int xing = frame + 4 + (hasCrc ? 2 : 0) + sideInfo;
        if (xing + 8 > audio.length) {
            return OptionalLong.empty();
        }
        boolean tagged = asciiAt(audio, xing, "Xing") || asciiAt(audio, xing, "Info");
        if (!tagged) {
            return OptionalLong.empty();
        }
        long flags = readUint32(audio, xing + 4);
        if ((flags & 0x1) == 0 || xing + 12 > audio.length) {
            return OptionalLong.empty();
        }
        long frames = readUint32(audio, xing + 8);
        if (frames <= 0) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Math.round(frames * samplesPerFrame * 1000.0 / sampleRate));
    }

    private int skipId3v2(byte[] audio) {
        if (audio.length < 10 || !asciiAt(audio, 0, "ID3")) {
            return 0;
        }
        // 标签长度是 4 个 synchsafe 字节（每字节只用低 7 位）
        int size = ((audio[6] & 0x7F) << 21) | ((audio[7] & 0x7F) << 14)
                | ((audio[8] & 0x7F) << 7) | (audio[9] & 0x7F);
        int total = 10 + size;
        return total > 0 && total < audio.length ? total : 0;
    }

    /** 找 11 位全 1 的帧同步，并确认随后的版本/层字段合法。 */
    private int findFrameSync(byte[] audio, int from) {
        for (int i = from; i + 4 <= audio.length; i++) {
            if ((audio[i] & 0xFF) != 0xFF || (audio[i + 1] & 0xE0) != 0xE0) {
                continue;
            }
            int versionBits = (audio[i + 1] >> 3) & 0x3;
            int layerBits = (audio[i + 1] >> 1) & 0x3;
            if (versionBits != 1 && layerBits != 0) {
                return i;
            }
        }
        return -1;
    }

    private boolean isMono(int header) {
        return ((header >> 6) & 0x3) == 3;
    }

    private int sampleRateTable(int versionBits, int index) {
        if (index == 3) {
            return -1;
        }
        int[] mpeg1 = {44100, 48000, 32000};
        int[] mpeg2 = {22050, 24000, 16000};
        int[] mpeg25 = {11025, 12000, 8000};
        return switch (versionBits) {
            case 3 -> mpeg1[index];
            case 2 -> mpeg2[index];
            case 0 -> mpeg25[index];
            default -> -1;
        };
    }

    /**
     * WAV：在 chunk 里找到 {@code fmt }（取 byteRate）与 {@code data}（取长度），二者相除。
     * 首个 chunk 不一定是 {@code fmt }，所以必须逐个走。
     *
     * <p>RIFF 是小端，与 m4a 的大端不同，所以这里用单独的小端读取。
     */
    private OptionalLong wavDuration(byte[] audio) {
        if (audio.length < WAV_HEADER_BYTES || !asciiAt(audio, 0, "RIFF") || !asciiAt(audio, 8, "WAVE")) {
            return OptionalLong.empty();
        }
        long byteRate = -1;
        long dataSize = -1;
        int offset = WAV_HEADER_BYTES;
        while (offset + BOX_HEADER_BYTES <= audio.length) {
            String chunkId = new String(audio, offset, 4, StandardCharsets.US_ASCII);
            long chunkSize = readUint32Le(audio, offset + 4);
            int body = offset + BOX_HEADER_BYTES;
            if ("fmt ".equals(chunkId) && body + 16 <= audio.length) {
                byteRate = readUint32Le(audio, body + 8);
            } else if ("data".equals(chunkId)) {
                // data 的长度可能写成 0 或超出实际字节数（流式写入），取两者较小值
                dataSize = Math.min(chunkSize, audio.length - body);
            }
            if (byteRate > 0 && dataSize >= 0) {
                return OptionalLong.of(Math.round(dataSize * 1000.0 / byteRate));
            }
            if (chunkSize > audio.length) {
                // 长度荒唐，视为结构损坏
                break;
            }
            // 长度为 0 的 chunk 是合法的（例如空的 LIST），跳过继续而不是放弃
            offset = body + (int) chunkSize + ((int) chunkSize % 2);
        }
        return OptionalLong.empty();
    }

    private long readUint32Le(byte[] audio, int offset) {
        if (offset + 4 > audio.length) {
            return -1;
        }
        return ((long) (audio[offset + 3] & 0xFF) << 24)
                | ((audio[offset + 2] & 0xFF) << 16)
                | ((audio[offset + 1] & 0xFF) << 8)
                | (audio[offset] & 0xFF);
    }

    /**
     * m4a：在 box 树里找 {@code moov → mvhd}，用 duration / timescale 换算。
     * 只走顶层与 moov 一层，够用且不必实现完整的 box 解析。
     */
    private OptionalLong m4aDuration(byte[] audio) {
        int moov = findBox(audio, 0, audio.length, "moov");
        if (moov < 0) {
            return OptionalLong.empty();
        }
        int moovBody = moov + BOX_HEADER_BYTES;
        int moovEnd = Math.min(audio.length, moovBody + (int) readUint32(audio, moov + 4));
        int mvhd = findBox(audio, moovBody, moovEnd, "mvhd");
        if (mvhd < 0) {
            return OptionalLong.empty();
        }
        int body = mvhd + BOX_HEADER_BYTES;
        int version = audio[body] & 0xFF;
        // version 0：creation(4) modification(4) timescale(4) duration(4)
        // version 1：creation(8) modification(8) timescale(4) duration(8)
        int timescaleOffset = version == 1 ? body + 4 + 16 : body + 4 + 8;
        long timescale = readUint32(audio, timescaleOffset);
        long duration = version == 1 ? readUint64(audio, timescaleOffset + 4) : readUint32(audio, timescaleOffset + 4);
        if (timescale <= 0 || duration <= 0) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Math.round(duration * 1000.0 / timescale));
    }

    /** @return box 起始下标；找不到为 -1 */
    private int findBox(byte[] audio, int from, int to, String boxType) {
        int offset = from;
        while (offset + BOX_HEADER_BYTES <= to) {
            long size = readUint32(audio, offset);
            if (asciiAt(audio, offset + 4, boxType)) {
                return offset;
            }
            if (size < BOX_HEADER_BYTES) {
                return -1;
            }
            offset += (int) size;
        }
        return -1;
    }

    private boolean asciiAt(byte[] audio, int offset, String expected) {
        if (offset + expected.length() > audio.length) {
            return false;
        }
        for (int i = 0; i < expected.length(); i++) {
            if (audio[offset + i] != expected.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private long readUint32(byte[] audio, int offset) {
        if (offset + 4 > audio.length) {
            return -1;
        }
        return ((long) (audio[offset] & 0xFF) << 24)
                | ((audio[offset + 1] & 0xFF) << 16)
                | ((audio[offset + 2] & 0xFF) << 8)
                | (audio[offset + 3] & 0xFF);
    }

    private long readUint64(byte[] audio, int offset) {
        long high = readUint32(audio, offset);
        long low = readUint32(audio, offset + 4);
        return high < 0 || low < 0 ? -1 : (high << 32) | low;
    }

    private String normalize(String mimeType) {
        return mimeType == null ? "" : mimeType.split(";")[0].trim().toLowerCase(java.util.Locale.ROOT);
    }
}
