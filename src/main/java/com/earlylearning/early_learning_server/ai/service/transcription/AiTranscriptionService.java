package com.earlylearning.early_learning_server.ai.service.transcription;

import java.time.Instant;
import java.util.UUID;

import com.earlylearning.early_learning_server.ai.model.task.AiTask;
import com.earlylearning.early_learning_server.ai.service.task.AiTaskRunner;
import com.earlylearning.early_learning_server.ai.model.task.AiTaskSubmission;
import com.earlylearning.early_learning_server.ai.model.task.FailedStage;
import com.earlylearning.early_learning_server.ai.model.task.TaskFailureCode;
import com.earlylearning.early_learning_server.ai.model.task.TaskKind;
import com.earlylearning.early_learning_server.ai.model.task.TaskStage;
import com.earlylearning.early_learning_server.ai.model.transcription.SpeechTranscriber;
import com.earlylearning.early_learning_server.ai.model.transcription.TranscriptionCommand;
import com.earlylearning.early_learning_server.ai.model.transcription.TranscriptionResult;
import com.earlylearning.early_learning_server.ai.model.transcription.TranscriptionTarget;
import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.idempotency.InputFingerprint;
import org.springframework.stereotype.Service;

/**
 * 转写任务的提交。
 *
 * <p>幂等与重试语义由 {@link AiTaskSubmission} 统一实现；这里只负责转写特有的部分：
 * 上下文形态校验、输入指纹的构成、以及把音频交给识别适配器。
 * 返回领域对象 {@link AiTask}，HTTP 形状由 controller 转换。
 *
 * <p>权限：按教师隔离任务；本模块不校验。
 */
@Service
public class AiTranscriptionService {

    private static final String CONTEXT_FIELD_PREFIX = "/context/";

    private final AiTaskSubmission submission;
    private final AiTaskRunner runner;
    private final SpeechTranscriber transcriber;

    public AiTranscriptionService(AiTaskSubmission submission,
                                  AiTaskRunner runner,
                                  SpeechTranscriber transcriber) {
        this.submission = submission;
        this.runner = runner;
        this.transcriber = transcriber;
    }

    public AiTask submit(byte[] audio, String mimeType, TranscriptionCommand command, int retryAttempt) {
        requireValidContext(command);
        return submission.submitAndRun(command.requestId(), fingerprintOf(command, audio), retryAttempt,
                () -> newTask(command),
                task -> runTranscription(task, audio, mimeType));
    }

    /** 新建任务；只在首次提交（CREATE）时调用，登记由 {@code resolveAndRegister} 一并完成。 */
    private AiTask newTask(TranscriptionCommand command) {
        return new AiTask(
                UUID.randomUUID().toString(),
                command.requestId(),
                command.inputRevision(),
                TaskKind.TRANSCRIPTION,
                AiTaskSubmission.FIRST_ATTEMPT,
                null,
                command.businessType(),
                command.activityId(),
                Instant.now());
    }


    private AiTask runTranscription(AiTask task, byte[] audio, String mimeType) {
        runner.run(task, TaskStage.TRANSCRIBING, FailedStage.TRANSCRIBE, TaskFailureCode.ASR_FAILED,
                () -> new TranscriptionResult(transcriber.transcribe(audio, mimeType)));
        return task;
    }

    /** 指纹覆盖请求的全部输入；含任务类型，因此三个提交接口之间复用 request_id 会被判为冲突。 */
    private String fingerprintOf(TranscriptionCommand command, byte[] audio) {
        return InputFingerprint.of(
                TaskKind.TRANSCRIPTION.name(),
                command.requestId(),
                command.inputRevision(),
                String.valueOf(command.businessType()),
                command.activityId(),
                String.valueOf(command.target()),
                String.valueOf(command.questionId()),
                String.valueOf(command.attempt()),
                InputFingerprint.sha256Hex(audio));
    }

    /** 按 target 校验上下文形态：单题录音必须有 question_id 与 attempt，故事录音则不能有。 */
    private void requireValidContext(TranscriptionCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField("/context"));
        }
        requirePresent(command.requestId(), "request_id");
        requirePresent(command.inputRevision(), "input_revision");
        requirePresent(command.businessType(), "business_type");
        requirePresent(command.activityId(), "activity_id");
        requirePresent(command.target(), "target");

        boolean singleQuestion = command.target() == TranscriptionTarget.QUESTION_ANSWERING;
        if (singleQuestion) {
            requirePresent(command.questionId(), "question_id");
            if (command.attempt() == null) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST,
                        ApiErrorDetails.atField(CONTEXT_FIELD_PREFIX + "attempt"));
            }
        } else if (command.questionId() != null || command.attempt() != null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    ApiErrorDetails.atField(CONTEXT_FIELD_PREFIX + "question_id"));
        }
    }

    private void requirePresent(Object value, String field) {
        if (value == null || (value instanceof String text && text.isBlank())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    ApiErrorDetails.atField(CONTEXT_FIELD_PREFIX + field));
        }
    }
}
