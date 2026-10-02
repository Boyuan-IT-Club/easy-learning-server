package com.earlylearning.early_learning_server.ai.client.media;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.earlylearning.early_learning_server.ai.client.media.AudioDurationParser;

import static org.assertj.core.api.Assertions.assertThat;

class AudioDurationParserTests {

    private final AudioDurationParser parser = new AudioDurationParser();

    @Test
    void readsWavDurationFromHeader() {
        // 8000Hz 16bit 单声道 → byteRate 16000；2 秒 → data 32000 字节
        byte[] wav = wav(32000, 16000);

        assertThat(parser.supports("audio/wav")).isTrue();
        assertThat(parser.parse(wav, "audio/wav")).hasValue(2000L);
    }

    @Test
    void readsWavWithLeadingChunkBeforeFormatChunk() {
        // fmt 不一定是第一个 chunk：前面先放一个 LIST
        byte[] wav = wavWithLeadingChunk();

        assertThat(parser.parse(wav, "audio/wav")).hasValue(1000L);
    }

    @Test
    void readsM4aDurationFromMvhd() {
        // timescale 1000，duration 90000 → 90 秒
        byte[] m4a = m4a(1000, 90000, 0);

        assertThat(parser.supports("audio/mp4")).isTrue();
        assertThat(parser.parse(m4a, "audio/mp4")).hasValue(90000L);
    }

    @Test
    void readsM4aDurationFrom64BitMvhd() {
        byte[] m4a = m4a(44100, 44100 * 5, 1);

        assertThat(parser.parse(m4a, "audio/m4a")).hasValue(5000L);
    }

    @Test
    void formatsWeCannotMeasureAreNotSupported() {
        // 契约列了这些格式，但项目选择"测不出时长就不放行"，由调用方据此拒收并说明原因
        assertThat(parser.supports("audio/ogg")).isFalse();
        assertThat(parser.supports("audio/webm")).isFalse();
        assertThat(parser.parse(new byte[]{1, 2, 3, 4}, "audio/ogg")).isEmpty();
        assertThat(parser.supports(null)).isFalse();
    }

    @Test
    void malformedInputYieldsNoDurationInsteadOfThrowing() {
        assertThat(parser.parse(new byte[]{1, 2, 3}, "audio/wav")).isEmpty();
        assertThat(parser.parse(new byte[]{1, 2, 3}, "audio/mp4")).isEmpty();
        assertThat(parser.parse(null, "audio/wav")).isEmpty();
        // 正确的 RIFF 头但 chunk 位置全是 0
        byte[] broken = new byte[64];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, broken, 0, 4);
        System.arraycopy("WAVE".getBytes(StandardCharsets.US_ASCII), 0, broken, 8, 4);
        assertThat(parser.parse(broken, "audio/wav")).isEmpty();
    }

    @Test
    void readsMp3DurationFromConstantBitrateFrames() {
        // MPEG1 Layer III、128kbps、44100Hz → 单帧 417 字节，每帧 1152 采样
        byte[] mp3 = cbrMp3(100, 417);

        assertThat(parser.supports("audio/mpeg")).isTrue();
        long millis = parser.parse(mp3, "audio/mpeg").orElseThrow();
        // 100 帧 × 1152 / 44100 ≈ 2612ms；用帧长推算会有几百毫秒级偏差
        assertThat(millis).isBetween(2500L, 2700L);
    }

    @Test
    void readsMp3DurationFromVbrXingHeader() {
        byte[] mp3 = vbrMp3(3000);

        // 帧数来自 Xing 段，比按码率估算准
        assertThat(parser.parse(mp3, "audio/mp3")).hasValue(Math.round(3000 * 1152 * 1000.0 / 44100));
    }

    @Test
    void skipsId3v2TagBeforeReadingMp3() {
        byte[] withoutTag = cbrMp3(100, 417);
        byte[] withTag = new byte[withoutTag.length + 10 + 64];
        System.arraycopy("ID3".getBytes(StandardCharsets.US_ASCII), 0, withTag, 0, 3);
        withTag[3] = 4;
        withTag[9] = 64; // synchsafe 长度 64
        System.arraycopy(withoutTag, 0, withTag, 10 + 64, withoutTag.length);

        assertThat(parser.parse(withTag, "audio/mpeg"))
                .isEqualTo(parser.parse(withoutTag, "audio/mpeg"));
    }

    @Test
    void mp3WithoutValidFrameYieldsNoDuration() {
        assertThat(parser.parse(new byte[]{(byte) 0xFF, 0x00, 0x00, 0x00, 0, 0, 0, 0}, "audio/mpeg")).isEmpty();
        assertThat(parser.parse("not audio at all".getBytes(StandardCharsets.US_ASCII), "audio/mpeg")).isEmpty();
    }

    private static byte[] cbrMp3(int frames, int frameSize) {
        byte[] audio = new byte[frames * frameSize];
        for (int i = 0; i < frames; i++) {
            audio[i * frameSize] = (byte) 0xFF;
            audio[i * frameSize + 1] = (byte) 0xFB;   // MPEG1 Layer III，无 CRC
            audio[i * frameSize + 2] = (byte) 0x90;   // 码率索引 9 → 128kbps，采样率 44100
            audio[i * frameSize + 3] = 0x00;
        }
        return audio;
    }

    /** 首帧带 Xing 段，声明总帧数；其余字节只是占位。 */
    private static byte[] vbrMp3(int totalFrames) {
        int frameSize = 417;
        byte[] audio = new byte[2 * frameSize];
        audio[0] = (byte) 0xFF;
        audio[1] = (byte) 0xFB;
        audio[2] = (byte) 0x90;
        // 立体声 → MPEG1 侧信息 32 字节；Xing 段紧随其后
        int xing = 4 + 32;
        System.arraycopy("Xing".getBytes(StandardCharsets.US_ASCII), 0, audio, xing, 4);
        audio[xing + 4] = 0;
        audio[xing + 5] = 0;
        audio[xing + 6] = 0;
        audio[xing + 7] = 1;   // flags：含帧数
        audio[xing + 8] = (byte) (totalFrames >>> 24);
        audio[xing + 9] = (byte) (totalFrames >>> 16);
        audio[xing + 10] = (byte) (totalFrames >>> 8);
        audio[xing + 11] = (byte) totalFrames;
        return audio;
    }
    /** 表头 + 真实长度的 data 字节；解析器会取两者较小值，所以 data 必须真的存在。 */
    private static byte[] wav(int dataSize, int byteRate) {
        return ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
                .put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + dataSize)
                .put("WAVE".getBytes(StandardCharsets.US_ASCII))
                .put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16)
                .putShort((short) 1).putShort((short) 1).putInt(8000).putInt(byteRate)
                .putShort((short) 2).putShort((short) 16)
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(dataSize)
                .array();
    }

    /** 合法的 WAV，但在 fmt 之前先放一个 LIST chunk，用来验证 chunk 是逐个走的。 */
    private static byte[] wavWithLeadingChunk() {
        int dataSize = 16000;
        int byteRate = 16000;
        byte[] listChunk = ByteBuffer.allocate(8 + 8).order(ByteOrder.LITTLE_ENDIAN)
                .put("LIST".getBytes(StandardCharsets.US_ASCII)).putInt(8)
                .put("INFO".getBytes(StandardCharsets.US_ASCII)).putInt(0)
                .array();
        int riffSize = 4 + listChunk.length + 24 + 8 + dataSize;
        return ByteBuffer.allocate(12 + riffSize).order(ByteOrder.LITTLE_ENDIAN)
                .put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(riffSize)
                .put("WAVE".getBytes(StandardCharsets.US_ASCII))
                .put(listChunk)
                .put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16)
                .putShort((short) 1).putShort((short) 1).putInt(8000).putInt(byteRate)
                .putShort((short) 2).putShort((short) 16)
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(dataSize)
                .array();
    }

    private static byte[] m4a(long timescale, long duration, int version) {
        byte[] mvhd = mvhd(timescale, duration, version);

        ByteArrayOutputStream moovBody = new ByteArrayOutputStream();
        moovBody.writeBytes(mvhd);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteBuffer ftyp = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
                .putInt(16).put("ftyp".getBytes(StandardCharsets.US_ASCII))
                .put("M4A ".getBytes(StandardCharsets.US_ASCII)).putInt(0);
        out.writeBytes(ftyp.array());
        ByteBuffer moovHeader = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
                .putInt(8 + moovBody.size()).put("moov".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(moovHeader.array());
        out.writeBytes(moovBody.toByteArray());
        return out.toByteArray();
    }

    private static byte[] mvhd(long timescale, long duration, int version) {
        int bodySize = version == 1 ? 4 + 16 + 4 + 8 + 80 : 4 + 8 + 4 + 4 + 80;
        ByteBuffer box = ByteBuffer.allocate(8 + bodySize).order(ByteOrder.BIG_ENDIAN)
                .putInt(8 + bodySize).put("mvhd".getBytes(StandardCharsets.US_ASCII))
                .put((byte) version).put(new byte[]{0, 0, 0});
        if (version == 1) {
            box.putLong(0).putLong(0).putInt((int) timescale).putLong(duration);
        } else {
            box.putInt(0).putInt(0).putInt((int) timescale).putInt((int) duration);
        }
        box.put(new byte[80]);
        return box.array();
    }
}
