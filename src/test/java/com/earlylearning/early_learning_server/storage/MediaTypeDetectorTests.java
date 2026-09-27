package com.earlylearning.early_learning_server.storage;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MediaTypeDetectorTests {

    private final MediaTypeDetector detector = new MediaTypeDetector();

    @Test
    void recognisesEveryAllowedFormat() {
        assertEquals("application/pdf", detector.detect(head("%PDF-1.7\n")));
        assertEquals("image/png", detector.detect(head("\u0089PNG\r\n\u001A\n")));
        assertEquals("image/jpeg", detector.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0}));
        assertEquals("image/gif", detector.detect(head("GIF89a\u0001\u0000")));
        assertEquals("image/webp", detector.detect(head("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ")));
        assertEquals("audio/wav", detector.detect(head("RIFF\u0000\u0000\u0000\u0000WAVEfmt ")));
        assertEquals("audio/ogg", detector.detect(head("OggS\u0000\u0002")));
        assertEquals("audio/flac", detector.detect(head("fLaC\u0000\u0000\u0000\"")));
        assertEquals("audio/mpeg", detector.detect(head("ID3\u0004\u0000\u0000")));
        assertEquals("audio/mpeg", detector.detect(new byte[]{(byte) 0xFF, (byte) 0xFB, (byte) 0x90, 0x64, 0, 0, 0, 0, 0, 0, 0, 0}));
        assertEquals("audio/webm", detector.detect(new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0, 0, 0, 0, 0, 0, 0, 0}));
        assertEquals("audio/mp4", detector.detect(head("\u0000\u0000\u0000\u0018ftypM4A ")));
    }

    @Test
    void rejectsRiffSubtypeThatIsNotAudioOrImage() {
        assertNull(detector.detect(head("RIFF\u0000\u0000\u0000\u0000AVI LIST")));
    }

    @Test
    void rejectsMp4BrandsThatAreNotAudio() {
        // 视频品牌必须被拒——否则录像会被当成录音混进官方音频资源。
        assertNull(detector.detect(head("\u0000\u0000\u0000\u0018ftypqt  ")));
        assertNull(detector.detect(head("\u0000\u0000\u0000\u0018ftypavc1")));
    }

    @Test
    void returnsNullForUnknownOrTooShortInput() {
        assertNull(detector.detect(head("hello world!")));
        assertNull(detector.detect(new byte[]{1, 2}));
        assertNull(detector.detect(null));
        assertNull(detector.detect(new byte[0]));
    }

    private static byte[] head(String text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(text.getBytes(StandardCharsets.ISO_8859_1));
        while (out.size() < MediaTypeDetector.HEAD_BYTES) {
            out.write(0);
        }
        return out.toByteArray();
    }
}
