package com.earlylearning.early_learning_server.ai.application.scoring;

import java.time.Instant;
import java.util.UUID;

import com.earlylearning.early_learning_server.ai.domain.rubric.RubricService;
import com.earlylearning.early_learning_server.ai.domain.task.AiTask;
import com.earlylearning.early_learning_server.ai.application.task.AiTaskRunner;
import com.earlylearning.early_learning_server.ai.domain.task.AiTaskSubmission;
import com.earlylearning.early_learning_server.ai.domain.task.FailedStage;
import com.earlylearning.early_learning_server.ai.domain.task.TaskFailureCode;
import com.earlylearning.early_learning_server.ai.domain.task.TaskKind;
import com.earlylearning.early_learning_server.ai.domain.task.TaskStage;
import com.earlylearning.early_learning_server.ai.domain.scoring.question.AnswerScoringCommand;
import com.earlylearning.early_learning_server.ai.domain.scoring.question.AnswerScoringInput;
import com.earlylearning.early_learning_server.ai.domain.scoring.question.AnswerScoringOutput;
import com.earlylearning.early_learning_server.ai.domain.scoring.question.AnswerScoringResult;
import com.earlylearning.early_learning_server.ai.domain.scoring.question.AnswerScorer;
import com.earlylearning.early_learning_server.ai.domain.scoring.question.QuestionScoreValidator;
import com.earlylearning.early_learning_server.ai.domain.scoring.ScoredQuestion;
import com.earlylearning.early_learning_server.common.idempotency.InputFingerprint;
import org.springframework.stereotype.Service;

/**
 * 单题评分任务的提交。
 *
 * <p>幂等与重试语义由 {@link AiTaskSubmission} 共用；版本语义由 {@link RubricService} 共用。
 * 这里只剩单题评分特有的部分。请求形状的校验在 interfaces 层完成，这里收到的是
 * {@link AnswerScoringCommand}；返回领域对象 {@link AiTask}，HTTP 形状由 controller 转换。
 *
 * <p><b>一次提交只评一次作答</b>：{@code attempt} 标明这次是提示前还是提示后。
 * 服务端不把两次作答关联起来，也不计算"最终分"——云端只负责评分与校验，
 * 结果由客户端写入本地；「最终分取提示后分、未提示沿用提示前分」是客户端的合成规则。
 *
 * <p>权限：按教师隔离任务；本模块不校验。
 */
@Service
public class AiAnswerScoringService {

    private final AiTaskSubmission submission;
    private final AiTaskRunner runner;
    private final RubricService rubricService;
    private final AnswerScorer scorer;
    private final QuestionScoreValidator scoreValidator;
    private final ScoringImageResolver imageResolver;

    public AiAnswerScoringService(AiTaskSubmission submission,
                                  AiTaskRunner runner,
                                  RubricService rubricService,
                                  AnswerScorer scorer,
                                  QuestionScoreValidator scoreValidator,
                                  ScoringImageResolver imageResolver) {
        this.submission = submission;
        this.runner = runner;
        this.rubricService = rubricService;
        this.scorer = scorer;
        this.scoreValidator = scoreValidator;
        this.imageResolver = imageResolver;
    }

    public AiTask submit(AnswerScoringCommand command, int retryAttempt) {
        String resolvedVersion = rubricService.resolveVersion(command.rubricVersion());
        return submission.submitAndRun(command.requestId(), fingerprintOf(command), retryAttempt,
                () -> newTask(command, resolvedVersion),
                task -> runScoring(task, toInput(command), task.getRubricVersion()),
                // 与故事评分同一条规则：重试沿用任务记录的版本，核对不过就拒绝且不动任务状态
                task -> {
                    String recordedVersion = rubricService.versionForRetry(
                            task.getRubricVersion(), command.rubricVersion());
                    task.restart(Instant.now());
                    return runScoring(task, toInput(command), recordedVersion);
                });
    }

    /** 新建任务；只在首次提交（CREATE）时调用，登记由 {@code resolveAndRegister} 一并完成。 */
    private AiTask newTask(AnswerScoringCommand command, String rubricVersion) {
        return new AiTask(
                UUID.randomUUID().toString(),
                command.requestId(),
                command.inputRevision(),
                TaskKind.ANSWER_SCORING,
                AiTaskSubmission.FIRST_ATTEMPT,
                rubricVersion,
                command.businessType(),
                command.activityId(),
                Instant.now());
    }


    /**
     * 把命令解析成适配器的领域输入：在这里按 file_code 取图。
     *
     * <p>放在提交的同步路径上而不是任务里，是为了让"图片编号有问题"立刻以 404/409/410/415 暴露，
     * 与签发、元数据接口语义一致；若落成任务失败，失败码的语义并不贴切。
     * 重放（REPLAY）不会走到这里，不会白白重复下载。
     */
    private AnswerScoringInput toInput(AnswerScoringCommand command) {
        return new AnswerScoringInput(
                command.question().questionId(),
                command.question().text(),
                command.question().hint(),
                command.attempt(),
                command.confirmedText(),
                command.storyContext(),
                imageResolver.resolve(command.images()));
    }

    private AiTask runScoring(AiTask task, AnswerScoringInput input, String rubricVersion) {
        runner.run(task, TaskStage.SCORING, FailedStage.SCORE, TaskFailureCode.MODEL_OUTPUT_INVALID, () -> {
            AnswerScoringOutput output = scorer.score(input, rubricVersion);
            // 模型输出必须过运行时语义校验：缺分数、分数越界、编造引文都在这里被挡下
            scoreValidator.validate(output, rubricVersion, input.confirmedText());
            // 题号与 attempt 由服务端写入，不采信模型
            return new AnswerScoringResult(input.questionId(), input.attempt(),
                    output.score(), output.modelMeta());
        });
        return task;
    }

    /**
     * 指纹覆盖请求的全部输入，不含评分依据版本（版本由任务记录持有、重试要沿用）。
     *
     * <p>{@code attempt} 必须在指纹里：同一道题的提示前与提示后是两份不同的输入，
     * 各自用不同的 {@code request_id}，混用会被判成"换了输入"。
     */
    private String fingerprintOf(AnswerScoringCommand command) {
        ScoredQuestion question = command.question();
        StringBuilder images = new StringBuilder();
        if (command.images() != null) {
            for (var image : command.images()) {
                images.append(image.fileCode()).append('=').append(image.kind())
                        .append(':').append(image.contentBase64() == null
                                ? "-" : InputFingerprint.sha256Hex(image.contentBase64()))
                        .append(':').append(image.description()).append('|');
            }
        }
        return InputFingerprint.of(
                TaskKind.ANSWER_SCORING.name(),
                command.requestId(),
                command.inputRevision(),
                String.valueOf(command.businessType()),
                command.activityId(),
                command.confirmedText(),
                command.storyContext(),
                question.questionId(),
                question.text(),
                question.hint(),
                command.attempt().name(),
                images.toString());
    }
}
