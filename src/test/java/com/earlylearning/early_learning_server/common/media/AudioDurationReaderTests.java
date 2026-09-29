package com.earlylearning.early_learning_server.common.media;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AudioDurationReaderTests {

    @TempDir
    Path tempDir;

    private final AudioDurationReader reader = new AudioDurationReader();

    @Test
    void readsDurationOfSynthesizedWav() throws IOException {
        Path file = tempDir.resolve("sample.wav");
        Files.write(file, wav(2, 8000));

        assertEquals(2000L, reader.readMillis(file.toFile(), "audio/wav"));
    }

    @Test
    void returnsNullForUnsupportedContainerInsteadOfFailing() throws IOException {
        Path file = tempDir.resolve("sample.webm");
        Files.write(file, new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3});

        assertNull(reader.readMillis(file.toFile(), "audio/webm"));
        assertNull(reader.readMillis(file.toFile(), null));
    }

    @Test
    void returnsNullAndDoesNotThrowWhenDeclaredSupportedButUnparsable() throws IOException {
        Path file = tempDir.resolve("broken.mp3");
        Files.write(file, new byte[]{1, 2, 3, 4, 5});

        assertNull(reader.readMillis(file.toFile(), "audio/mpeg"));
    }

    /** 生成一个最小可解析的 16-bit 单声道 PCM WAV。 */
    @Test
    void readsDurationFromTheStagingFileNameUsedByUploads() throws IOException {
        // 上传先把内容落成 cloud-file-xxxx.part。jaudiotagger 按文件名扩展名挑 reader，
        // 曾经因此对所有音频都返回 null（日志：No Reader associated with this extension:part）。
        Path staged = tempDir.resolve("cloud-file-123.part");
        Files.write(staged, wav(2, 8000));

        assertEquals(2000L, reader.readMillis(staged.toFile(), "audio/wav"));
    }

    private static byte[] wav(int seconds, int sampleRate) {
        int dataSize = seconds * sampleRate * 2;
        return ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
                .put("RIFF".getBytes()).putInt(36 + dataSize).put("WAVE".getBytes())
                .put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(sampleRate).putInt(sampleRate * 2).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes()).putInt(dataSize)
                .array();
    }
}
