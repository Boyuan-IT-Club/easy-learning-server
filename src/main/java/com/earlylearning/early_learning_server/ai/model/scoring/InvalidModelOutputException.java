package com.earlylearning.early_learning_server.ai.model.scoring;

/**
 * 模型输出不满足评分契约。
 *
 * <p>这不是接口错误：它发生在异步执行期间，会被落成任务的 {@code MODEL_OUTPUT_INVALID} 失败。
 */
public class InvalidModelOutputException extends RuntimeException {

    public InvalidModelOutputException(String message) {
        super(message);
    }
}
