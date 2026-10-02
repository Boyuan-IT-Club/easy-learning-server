package com.earlylearning.early_learning_server.ai.service.transcription;

import com.earlylearning.early_learning_server.ai.model.task.AiTask;
import com.earlylearning.early_learning_server.ai.model.task.AiTaskSubmission;
import com.earlylearning.early_learning_server.ai.model.transcription.TranscriptionCommand;

/**
 * 转写任务的提交。
 *
 * <p>幂等与重试语义由 {@link AiTaskSubmission} 统一实现；这里只负责转写特有的部分：
 * 上下文形态校验、输入指纹的构成、以及把音频交给识别适配器。
 * 返回领域对象 {@link AiTask}，HTTP 形状由 controller 转换。
 *
 * <p>权限：按教师隔离任务；本模块不校验。
 */
public interface AiTranscriptionService {

    AiTask submit(byte[] audio, String mimeType, TranscriptionCommand command, int retryAttempt);
}
