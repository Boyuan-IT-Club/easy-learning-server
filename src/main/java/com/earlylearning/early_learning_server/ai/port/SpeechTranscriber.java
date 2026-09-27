package com.earlylearning.early_learning_server.ai.port;

/**
 * 语音识别适配器。
 *
 * <p>实现可替换：接口只声明契约，不关心谁来实现。
 * 输入是内存中的音频字节——契约要求音频只在内存处理，实现**不得**把它落到磁盘。
 *
 * <p><b>默认实现见 {@link com.earlylearning.early_learning_server.ai.adapter.fake.FakeTranscriberConfig}（假实现，接通真实识别服务前把链路跑通用）。</b>
 * 接入真实识别服务时：提供本接口同类型的 Bean 并标 {@code @Primary} 即可覆盖，业务代码不用改。
 */
public interface SpeechTranscriber {

    /**
     * 识别一段录音。
     *
     * @return 识别文本；**空字符串表示"没有识别到内容"**，与识别失败不同（后者应抛异常）
     * @throws AiTaskFailedException 识别失败（携带任务失败码）
     */
    String transcribe(byte[] audio, String mimeType);
}
