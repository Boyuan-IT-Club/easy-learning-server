package com.earlylearning.early_learning_server.ai.service.transcription;

import java.util.Locale;
import java.util.OptionalLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.earlylearning.early_learning_server.ai.client.media.AudioDurationParser;
import com.earlylearning.early_learning_server.ai.model.transcription.AiTranscriptionLimits;
import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.media.MediaTypeDetector;

/**
 * 转写音频的准入校验：格式、体积、时长。每一种不合格都给出能直接定位原因的说明。
 *
 * <p>契约列了 m4a/mp3/wav/ogg/webm，但 ogg 与 webm 的时长无法在内存里测出来
 * （见 {@link AudioDurationParser}），按"测不出就不放行"处理——拒收时明确说明是格式原因，
 * 而不是笼统的"不支持的格式"。
 */
@Component
public class AudioValidator {

    private static final Logger log = LoggerFactory.getLogger(AudioValidator.class);

    private final MediaTypeDetector mediaTypeDetector;
    private final AudioDurationParser audioDurationParser;
    private final AiTranscriptionLimits aiTranscriptionLimits;

    public AudioValidator(MediaTypeDetector mediaTypeDetector,
                          AudioDurationParser audioDurationParser,
                          AiTranscriptionLimits aiTranscriptionLimits) {
        this.mediaTypeDetector = mediaTypeDetector;
        this.audioDurationParser = audioDurationParser;
        this.aiTranscriptionLimits = aiTranscriptionLimits;
    }

    /**
     * 识别音频的实际格式。
     *
     * <p>调用方在 {@link #validate} 之后还要用它，因为任务执行时需要把实际 MIME 交给识别适配器。
     */
    public String detect(byte[] audio) {
        return mediaTypeDetector.detect(head(audio));
    }

    /**
     * 校验通过后返回音频时长（毫秒）。
     *
     * @param declaredMime multipart 声明的 Content-Type，可为 null
     * @throws BusinessException 格式无法识别、格式无法校验时长、声明与实际不符、超体积或超时长
     */
    public long validate(byte[] audio, String declaredMime) {
        String detected = detect(audio);
        if (detected == null) {
            log.info("音频格式无法识别 declaredMime={} sizeBytes={} head={}",
                    declaredMime, audio.length, headPreview(audio));
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "无法识别音频格式，支持 m4a/mp3/wav");
        }
        if (!detected.startsWith("audio/")) {
            log.info("上传的不是音频 detectedMime={}", detected);
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "该内容不是音频：" + detected);
        }
        requireDeclarationAgrees(declaredMime, detected);

        if (!audioDurationParser.supports(detected)) {
            // 契约里列了 ogg/webm，但不接受它们：无法在内存中校验时长
            log.info("音频格式不接受：无法校验时长 detectedMime={}", detected);
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "该音频格式无法校验时长，暂不接受：" + detected + "；请改用 m4a/mp3/wav");
        }
        if (audio.length > aiTranscriptionLimits.maxSizeBytes()) {
            log.info("音频超出体积上限 detectedMime={} sizeBytes={} limit={}",
                    detected, audio.length, aiTranscriptionLimits.maxSizeBytes());
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE,
                    ApiErrorDetails.ofLimit(ApiErrorDetails.LimitName.SIZE_BYTES,
                            aiTranscriptionLimits.maxSizeBytes()));
        }

        OptionalLong duration = audioDurationParser.parse(audio, detected);
        if (duration.isEmpty()) {
            log.info("音频内容无法解析 detectedMime={} sizeBytes={}", detected, audio.length);
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "音频内容无法解析，无法确认时长：" + detected);
        }

        long durationMs = duration.getAsLong();
        if (durationMs > aiTranscriptionLimits.maxDurationMs()) {
            log.info("音频超出时长上限 detectedMime={} durationMs={} limit={}",
                    detected, durationMs, aiTranscriptionLimits.maxDurationMs());
            throw new BusinessException(ErrorCode.AUDIO_DURATION_EXCEEDED,
                    ApiErrorDetails.ofLimit(ApiErrorDetails.LimitName.DURATION_MS,
                            aiTranscriptionLimits.maxDurationMs()));
        }
        return durationMs;
    }

    private void requireDeclarationAgrees(String declaredMime, String detected) {
        if (declaredMime == null || declaredMime.isBlank()
                || "application/octet-stream".equalsIgnoreCase(declaredMime)) {
            return;
        }
        String normalized = declaredMime.split(";")[0].trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals(detected)) {
            log.info("声明与实际不符 declaredMime={} detectedMime={}", normalized, detected);
            throw new BusinessException(ErrorCode.CONTENT_TYPE_MISMATCH);
        }
    }

    private byte[] head(byte[] audio) {
        return audio.length <= MediaTypeDetector.HEAD_BYTES
                ? audio
                : java.util.Arrays.copyOf(audio, MediaTypeDetector.HEAD_BYTES);
    }

    /** 拒收时打出内容的开头：上传端把占位文本当文件发过来时，靠它一眼定位。 */
    private String headPreview(byte[] audio) {
        int limit = Math.min(audio.length, 32);
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            hex.append(String.format("%02x", audio[i]));
        }
        String text = new String(audio, 0, limit, java.nio.charset.StandardCharsets.UTF_8)
                .replaceAll("\\p{Cntrl}", ".");
        return hex.length() == 0 ? "(empty)" : "hex[" + hex + "] text[" + text + "]";
    }
}
