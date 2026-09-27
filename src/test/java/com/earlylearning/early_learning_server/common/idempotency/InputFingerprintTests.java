package com.earlylearning.early_learning_server.common.idempotency;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** 指纹必须「一样就是一样、不一样就是不一样」——判定错了要么重放错结果，要么误报 409。 */
class InputFingerprintTests {

    @Test
    void sameInputProducesSameFingerprint() {
        assertEquals(InputFingerprint.of("audio", "LF_20260101", "12345"),
                InputFingerprint.of("audio", "LF_20260101", "12345"));
    }

    @Test
    void differentSegmentOrOrderProducesDifferentFingerprint() {
        assertNotEquals(InputFingerprint.of("a", "b"), InputFingerprint.of("a", "c"));
        assertNotEquals(InputFingerprint.of("a", "b"), InputFingerprint.of("b", "a"));
    }

    @Test
    void segmentBoundariesDoNotCollide() {
        assertNotEquals(InputFingerprint.of("ab", "c"), InputFingerprint.of("a", "bc"));
    }

    @Test
    void emptyMissingAndAbsentPartsDiffer() {
        assertNotEquals(InputFingerprint.of(""), InputFingerprint.of());
        assertNotEquals(InputFingerprint.of(""), InputFingerprint.of((String) null));
        assertNotEquals(InputFingerprint.of((String) null), InputFingerprint.of());
    }

    @Test
    void byteDigestMatchesStandardVector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                InputFingerprint.sha256Hex("abc"));
        assertEquals(InputFingerprint.sha256Hex("abc"),
                InputFingerprint.sha256Hex("abc".getBytes(StandardCharsets.UTF_8)));
    }
}
