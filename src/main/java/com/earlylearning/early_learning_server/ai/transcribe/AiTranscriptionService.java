package com.earlylearning.early_learning_server.ai.transcribe;

import java.time.Instant;
import java.util.UUID;

import com.earlylearning.early_learning_server.ai.port.SpeechTranscriber;
import com.earlylearning.early_learning_server.ai.task.AiTask;
import com.earlylearning.early_learning_server.ai.task.AiTaskRunner;
import com.earlylearning.early_learning_server.ai.task.AiTaskSubmission;
import com.earlylearning.early_learning_server.ai.task.FailedStage;
import com.earlylearning.early_learning_server.ai.task.TaskFailureCode;
import com.earlylearning.early_learning_server.ai.task.TaskKind;
import com.earlylearning.early_learning_server.ai.task.TaskStage;
import com.earlylearning.early_learning_server.ai.web.TaskHandleResponse;
import com.earlylearning.early_learning_server.ai.web.TranscriptionContext;
import com.earlylearning.early_learning_server.ai.web.TranscriptionTarget;
import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.idempotency.InputFingerprint;
import org.springframework.stereotype.Service;

/**
 * 转写任务的提交。
 *
 * <p>幂等与重试语义由 {@link AiTaskSubmission} 统一实现；这里只负责转写特有的部分：
 * 上下文校验、输入指纹的构成、以及把音频交给识别适配器。
 *
 * <p>权限：契约要求按教师隔离任务；本模块不校验。
 */
@Service
public class AiTranscriptionService {

    private static final int FIRST_ATTEMPT = 0;
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

    public TaskHandleResponse submit(byte[] audio, String mimeType, TranscriptionContext context, int retryAttempt) {
        requireValidContext(context);
        String fingerprint = fingerprintOf(context, audio);

        AiTaskSubmission.Outcome outcome = submission.resolveAndRegister(context.requestId(), fingerprint,
                retryAttempt, () -> newTask(context));
        AiTask task = switch (outcome.action()) {
            case CREATE -> runTranscription(outcome.task(), audio, mimeType);
            case RESTART -> restart(outcome.task(), audio, mimeType);
            case REPLAY -> outcome.task();
        };
        return TaskHandleResponse.from(task);
    }

    /** 新建任务；只在首次提交（CREATE）时调用，登记由 {@code resolveAndRegister} 一并完成。 */
    private AiTask newTask(TranscriptionContext context) {
        return new AiTask(
                UUID.randomUUID().toString(),
                context.requestId(),
                context.inputRevision(),
                TaskKind.TRANSCRIPTION,
                FIRST_ATTEMPT,
                null,
                context.businessType(),
                context.activityId(),
                Instant.now());
    }

    private AiTask restart(AiTask task, byte[] audio, String mimeType) {
        task.restart(Instant.now());
        return runTranscription(task, audio, mimeType);
    }

    private AiTask runTranscription(AiTask task, byte[] audio, String mimeType) {
        runner.run(task, TaskStage.TRANSCRIBING, FailedStage.TRANSCRIBE, TaskFailureCode.ASR_FAILED,
                () -> new TranscriptionResult(transcriber.transcribe(audio, mimeType)));
        return task;
    }

    /** 指纹覆盖请求的全部输入；含任务类型，因此三个提交接口之间复用 request_id 会被判为冲突。 */
    private String fingerprintOf(TranscriptionContext context, byte[] audio) {
        return InputFingerprint.of(
                TaskKind.TRANSCRIPTION.name(),
                context.requestId(),
                context.inputRevision(),
                String.valueOf(context.businessType()),
                context.activityId(),
                String.valueOf(context.target()),
                String.valueOf(context.questionId()),
                String.valueOf(context.attempt()),
                InputFingerprint.sha256Hex(audio));
    }

    /** 按 target 校验上下文形态：单题录音必须有 question_id 与 attempt，故事录音则不能有。 */
    private void requireValidContext(TranscriptionContext context) {
        if (context == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, ApiErrorDetails.atField("/context"));
        }
        requirePresent(context.requestId(), "request_id");
        requirePresent(context.inputRevision(), "input_revision");
        requirePresent(context.businessType(), "business_type");
        requirePresent(context.activityId(), "activity_id");
        requirePresent(context.target(), "target");

        boolean singleQuestion = context.target() == TranscriptionTarget.QUESTION_ANSWERING;
        if (singleQuestion) {
            requirePresent(context.questionId(), "question_id");
            if (context.attempt() == null) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST,
                        ApiErrorDetails.atField(CONTEXT_FIELD_PREFIX + "attempt"));
            }
        } else if (context.questionId() != null || context.attempt() != null) {
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
