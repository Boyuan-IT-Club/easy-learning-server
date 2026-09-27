package com.earlylearning.early_learning_server.ai.score;

import java.time.Instant;
import java.util.UUID;

import com.earlylearning.early_learning_server.ai.port.AnswerScorer;
import com.earlylearning.early_learning_server.ai.rubric.RubricService;
import com.earlylearning.early_learning_server.ai.score.AnswerScoringOutput;
import com.earlylearning.early_learning_server.ai.score.AnswerScoringResult;
import com.earlylearning.early_learning_server.ai.score.QuestionScoreValidator;
import com.earlylearning.early_learning_server.ai.task.AiTask;
import com.earlylearning.early_learning_server.ai.task.AiTaskRunner;
import com.earlylearning.early_learning_server.ai.task.AiTaskSubmission;
import com.earlylearning.early_learning_server.ai.task.FailedStage;
import com.earlylearning.early_learning_server.ai.task.TaskFailureCode;
import com.earlylearning.early_learning_server.ai.task.TaskKind;
import com.earlylearning.early_learning_server.ai.task.TaskStage;
import com.earlylearning.early_learning_server.ai.web.AnswerScoringRequest;
import com.earlylearning.early_learning_server.ai.web.AnswerScoringRequestValidator;
import com.earlylearning.early_learning_server.ai.web.ScoringQuestion;
import com.earlylearning.early_learning_server.ai.web.TaskHandleResponse;
import com.earlylearning.early_learning_server.common.idempotency.InputFingerprint;
import org.springframework.stereotype.Service;

/**
 * 单题评分任务的提交。
 *
 * <p>幂等与重试语义由 {@link AiTaskSubmission} 共用；版本语义由 {@link RubricService} 共用。
 * 这里只剩单题评分特有的部分。
 *
 * <p><b>一次提交只评一次作答</b>：{@code attempt} 标明这次是提示前还是提示后。
 * 服务端**不把两次作答关联起来**，也不计算"最终分"——契约规定云端只负责评分与校验，
 * 结果由客户端写入本地；「最终分取提示后分、未提示沿用提示前分」是客户端的合成规则。
 *
 * <p>权限：契约要求按教师隔离任务；本模块不校验。
 */
@Service
public class AiAnswerScoringService {

    private static final int FIRST_ATTEMPT = 0;

    private final AiTaskSubmission submission;
    private final AiTaskRunner runner;
    private final RubricService rubricService;
    private final AnswerScorer scorer;
    private final QuestionScoreValidator scoreValidator;
    private final AnswerScoringRequestValidator requestValidator;

    public AiAnswerScoringService(AiTaskSubmission submission,
                                  AiTaskRunner runner,
                                  RubricService rubricService,
                                  AnswerScorer scorer,
                                  QuestionScoreValidator scoreValidator,
                                  AnswerScoringRequestValidator requestValidator) {
        this.submission = submission;
        this.runner = runner;
        this.rubricService = rubricService;
        this.scorer = scorer;
        this.scoreValidator = scoreValidator;
        this.requestValidator = requestValidator;
    }

    public TaskHandleResponse submit(AnswerScoringRequest request, int retryAttempt) {
        requestValidator.validate(request);
        String resolvedVersion = rubricService.resolveVersion(request.rubricVersion());
        String fingerprint = fingerprintOf(request);

        AiTaskSubmission.Outcome outcome = submission.resolveAndRegister(request.requestId(), fingerprint,
                retryAttempt, () -> newTask(request, resolvedVersion));
        AiTask task = switch (outcome.action()) {
            case CREATE -> runScoring(outcome.task(), request, resolvedVersion);
            case RESTART -> restart(outcome.task(), request);
            case REPLAY -> outcome.task();
        };
        return TaskHandleResponse.from(task);
    }

    /** 新建任务；只在首次提交（CREATE）时调用，登记由 {@code resolveAndRegister} 一并完成。 */
    private AiTask newTask(AnswerScoringRequest request, String rubricVersion) {
        return new AiTask(
                UUID.randomUUID().toString(),
                request.requestId(),
                request.inputRevision(),
                TaskKind.ANSWER_SCORING,
                FIRST_ATTEMPT,
                rubricVersion,
                request.businessType(),
                request.activityId(),
                Instant.now());
    }

    /** 重试沿用**任务记录的版本**，不能因为请求或配置变了就换标准。 */
    private AiTask restart(AiTask task, AnswerScoringRequest request) {
        String recordedVersion = rubricService.versionForRetry(task.getRubricVersion(), request.rubricVersion());
        task.restart(Instant.now());
        return runScoring(task, request, recordedVersion);
    }

    private AiTask runScoring(AiTask task, AnswerScoringRequest request, String rubricVersion) {
        runner.run(task, TaskStage.SCORING, FailedStage.SCORE, TaskFailureCode.MODEL_OUTPUT_INVALID, () -> {
            AnswerScoringOutput output = scorer.score(request, rubricVersion);
            // 模型输出必须过运行时语义校验：缺分数、分数越界、编造引文都在这里被挡下
            scoreValidator.validate(output, rubricVersion, request.confirmedText());
            // 题号与 attempt 由服务端写入，不采信模型
            return new AnswerScoringResult(request.question().questionId(), request.attempt(),
                    output.score(), output.modelMeta());
        });
        return task;
    }

    /**
     * 指纹覆盖请求的全部输入，**不含评分标准版本**（版本由任务记录持有、重试要沿用）。
     *
     * <p>{@code attempt} 必须在指纹里：同一道题的提示前与提示后是**两份不同的输入**，
     * 各自用不同的 {@code request_id}，混用会被判成"换了输入"。
     */
    private String fingerprintOf(AnswerScoringRequest request) {
        ScoringQuestion question = request.question();
        StringBuilder images = new StringBuilder();
        if (request.images() != null) {
            for (var image : request.images()) {
                images.append(image.fileCode()).append('=').append(image.kind())
                        .append(':').append(image.contentBase64() == null
                                ? "-" : InputFingerprint.sha256Hex(image.contentBase64()))
                        .append(':').append(image.description()).append('|');
            }
        }
        return InputFingerprint.of(
                TaskKind.ANSWER_SCORING.name(),
                request.requestId(),
                request.inputRevision(),
                String.valueOf(request.businessType()),
                request.activityId(),
                request.confirmedText(),
                request.storyContext(),
                question.questionId(),
                question.text(),
                question.hint(),
                request.attempt().name(),
                images.toString());
    }
}
