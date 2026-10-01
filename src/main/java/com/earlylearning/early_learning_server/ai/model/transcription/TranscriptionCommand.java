package com.earlylearning.early_learning_server.ai.model.transcription;

import com.earlylearning.early_learning_server.ai.model.task.Attempt;
import com.earlylearning.early_learning_server.ai.model.task.BusinessType;

/**
 * 转写任务的应用服务输入：multipart 里的 {@code context} 经 controller 层映射后的形态。
 *
 * <p>契约按 {@code target} 分成两种形态：故事录音只需要前五项；单题录音还要 {@code questionId} 与
 * {@code attempt}，且不允许出现前者不需要的字段。形态校验在应用服务里（错误路径以 /context/ 开头）。
 */
public record TranscriptionCommand(String requestId,
                                   String inputRevision,
                                   BusinessType businessType,
                                   String activityId,
                                   TranscriptionTarget target,
                                   String questionId,
                                   Attempt attempt) {
}
