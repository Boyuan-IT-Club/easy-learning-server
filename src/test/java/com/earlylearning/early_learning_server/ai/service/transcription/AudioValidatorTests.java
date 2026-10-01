package com.earlylearning.early_learning_server.ai.service.transcription;
import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import com.earlylearning.early_learning_server.ai.model.transcription.AiTranscriptionLimits;
import com.earlylearning.early_learning_server.ai.client.media.AudioDurationParser;
import com.earlylearning.early_learning_server.ai.service.transcription.AudioValidator;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.media.MediaTypeDetector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AudioValidatorTests {

    /** 体积上限设得很小，便于用很小的夹具触发；时长上限的 10 分钟。 */
    private static final AiTranscriptionLimits SMALL_SIZE_LIMITS = new AiTranscriptionLimits(5000, 600_000);
    private static final AiTranscriptionLimits ROOMY_LIMITS = new AiTranscriptionLimits(50_000_000, 600_000);

    private final AudioValidator smallSizeValidator = new AudioValidator(
            new MediaTypeDetector(), new AudioDurationParser(), SMALL_SIZE_LIMITS);
    private final AudioValidator roomyValidator = new AudioValidator(
            new MediaTypeDetector(), new AudioDurationParser(), ROOMY_LIMITS);

    @Test
    void acceptsWavAndReturnsDuration() {
        long duration = roomyValidator.validate(wav(32000, 16000), "audio/wav");

        assertThat(duration).isEqualTo(2000L);
    }

    @Test
    void unmeasurableFormatIsRejectedAndTheReasonNamesTheFormat() {
        byte[] ogg = concat("OggS".getBytes(StandardCharsets.US_ASCII), new byte[32]);

        assertThatThrownBy(() -> roomyValidator.validate(ogg, "audio/ogg"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法校验时长")
                .hasMessageContaining("audio/ogg")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void unrecognizableContentIsRejectedWithReason() {
        assertThatThrownBy(() -> roomyValidator.validate("not audio".getBytes(StandardCharsets.US_ASCII), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法识别音频格式")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void nonAudioContentIsRejectedWithItsDetectedType() {
        byte[] pdf = concat("%PDF-1.7".getBytes(StandardCharsets.US_ASCII), new byte[16]);

        assertThatThrownBy(() -> roomyValidator.validate(pdf, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是音频")
                .hasMessageContaining("application/pdf");
    }

    @Test
    void declaredMimeThatContradictsContentIsContentTypeMismatch() {
        assertThatThrownBy(() -> roomyValidator.validate(wav(32000, 16000), "audio/mpeg"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONTENT_TYPE_MISMATCH);
    }

    @Test
    void oversizedAudioIs413WithLimitDetails() {
        assertThatThrownBy(() -> smallSizeValidator.validate(wav(40000, 16000), "audio/wav"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    var business = (BusinessException) ex;
                    assertThat(business.getErrorCode()).isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE);
                    assertThat(business.getDetails().limit().name())
                            .isEqualTo(com.earlylearning.early_learning_server.common.error.ApiErrorDetails.LimitName.SIZE_BYTES);
                    assertThat(business.getDetails().limit().maximum()).isEqualTo(5000L);
                });
    }

    @Test
    void overlongAudioIs413WithTheDedicatedCode() {
        // byteRate 取 16，让 700 秒的音频只需要 11200 字节
        byte[] longWav = wav(700 * 16, 16);

        assertThatThrownBy(() -> roomyValidator.validate(longWav, "audio/wav"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    var business = (BusinessException) ex;
                    assertThat(business.getErrorCode()).isEqualTo(ErrorCode.AUDIO_DURATION_EXCEEDED);
                    assertThat(business.getDetails().limit().name())
                            .isEqualTo(com.earlylearning.early_learning_server.common.error.ApiErrorDetails.LimitName.DURATION_MS);
                    assertThat(business.getDetails().limit().maximum()).isEqualTo(600_000L);
                });
    }

    @Test
    void supportedFormatWithUnparsableContentIsRejectedWithReason() {
        // 声明是 m4a，detect 也认成 m4a，但内部结构不可解析
        byte[] broken = ByteBuffer.allocate(24).order(ByteOrder.BIG_ENDIAN)
                .putInt(24).put("ftyp".getBytes(StandardCharsets.US_ASCII))
                .put("M4A ".getBytes(StandardCharsets.US_ASCII)).putInt(0).array();

        assertThatThrownBy(() -> roomyValidator.validate(broken, "audio/mp4"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法解析")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

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

    private static byte[] concat(byte[] head, byte[] tail) {
        byte[] result = java.util.Arrays.copyOf(head, head.length + tail.length);
        System.arraycopy(tail, 0, result, head.length, tail.length);
        return result;
    }
}
