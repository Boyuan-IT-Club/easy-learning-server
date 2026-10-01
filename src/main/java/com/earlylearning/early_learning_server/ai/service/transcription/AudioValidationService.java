package com.earlylearning.early_learning_server.ai.service.transcription;

import com.earlylearning.early_learning_server.ai.client.media.AudioDurationParser;
import com.earlylearning.early_learning_server.common.error.BusinessException;

/**
 * 转写音频的准入校验：格式、体积、时长。每一种不合格都给出能直接定位原因的说明。
 *
 * <p>契约列了 m4a/mp3/wav/ogg/webm，但 ogg 与 webm 的时长无法在内存里测出来
 * （见 {@link AudioDurationParser}），按"测不出就不放行"处理——拒收时明确说明是格式原因，
 * 而不是笼统的"不支持的格式"。
 */
public interface AudioValidationService {

    /**
     * 识别音频的实际格式。
     *
     * <p>调用方在 {@link #validate} 之后还要用它，因为任务执行时需要把实际 MIME 交给识别适配器。
     */
    String detect(byte[] audio);

    /**
     * 校验通过后返回音频时长（毫秒）。
     *
     * @param declaredMime multipart 声明的 Content-Type，可为 null
     * @throws BusinessException 格式无法识别、格式无法校验时长、声明与实际不符、超体积或超时长
     */
    long validate(byte[] audio, String declaredMime);
}
