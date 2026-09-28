package com.earlylearning.early_learning_server.ai.llm;

/**
 * 调用大模型失败。
 *
 * <p>{@code retryable} 由适配器判断（限流、超时、5xx 值得重试；鉴权失败、参数不合法不值得），
 * 上层据此决定是否落成**可重试**的任务失败。
 */
public class ChatModelException extends RuntimeException {

    private final boolean retryable;

    public ChatModelException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public ChatModelException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
