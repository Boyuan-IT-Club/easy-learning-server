package com.earlylearning.early_learning_server.ai.domain.transcription;

/**
 * 转写结果。契约里成功任务的 {@code result} 就是这个形状。
 *
 * @param transcript 识别文本；空字符串表示没有识别到内容（与识别失败不同）
 */
public record TranscriptionResult(String transcript) {
}
