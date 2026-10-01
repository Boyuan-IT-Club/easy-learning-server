package com.earlylearning.early_learning_server.ai.service.scoring.impl;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.earlylearning.early_learning_server.ai.client.task.AiTaskRunner;
import com.earlylearning.early_learning_server.ai.model.rubric.RubricService;
import com.earlylearning.early_learning_server.ai.model.scoring.ScoredQuestion;
import com.earlylearning.early_learning_server.ai.model.scoring.question.AnswerScorer;
import com.earlylearning.early_learning_server.ai.model.scoring.question.AnswerScoringCommand;
import com.earlylearning.early_learning_server.ai.model.scoring.question.AnswerScoringInput;
import com.earlylearning.early_learning_server.ai.model.scoring.question.AnswerScoringOutput;
import com.earlylearning.early_learning_server.ai.model.scoring.question.AnswerScoringResult;
import com.earlylearning.early_learning_server.ai.model.scoring.question.QuestionScoreValidator;
import com.earlylearning.early_learning_server.ai.model.task.AiTask;
import com.earlylearning.early_learning_server.ai.model.task.AiTaskSubmission;
import com.earlylearning.early_learning_server.ai.model.task.FailedStage;
import com.earlylearning.early_learning_server.ai.model.task.TaskFailureCode;
import com.earlylearning.early_learning_server.ai.model.task.TaskKind;
import com.earlylearning.early_learning_server.ai.model.task.TaskStage;
import com.earlylearning.early_learning_server.ai.service.scoring.AiAnswerScoringService;
import com.earlylearning.early_learning_server.ai.service.scoring.ScoringImageService;
import com.earlylearning.early_learning_server.common.idempotency.InputFingerprint;

import lombok.RequiredArgsConstructor;

/** {@link AiAnswerScoringService} 的实现。 */
@Service
@RequiredArgsConstructor
public class AiAnswerScoringServiceImpl implements AiAnswerScoringService {

    private final AiTaskSubmission aiTaskSubmission;
    private final AiTaskRunner aiTaskRunner;
    private final RubricService rubricService;
    private final AnswerScorer answerScorer;
    private final QuestionScoreValidator questionScoreValidator;
    private final ScoringImageService scoringImageService;

    @Override
    public AiTask submit(AnswerScoringCommand command, int retryAttempt) {
        String resolvedVersion = rubricService.resolveVersion(command.rubricVersion());
        return aiTaskSubmission.submitAndRun(command.requestId(), fingerprintOf(command), retryAttempt,
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
                scoringImageService.resolve(command.images()));
    }

    private AiTask runScoring(AiTask task, AnswerScoringInput input, String rubricVersion) {
        aiTaskRunner.run(task, TaskStage.SCORING, FailedStage.SCORE, TaskFailureCode.MODEL_OUTPUT_INVALID, () -> {
            AnswerScoringOutput output = answerScorer.score(input, rubricVersion);
            // 模型输出必须过运行时语义校验：缺分数、分数越界、编造引文都在这里被挡下
            questionScoreValidator.validate(output, rubricVersion, input.confirmedText());
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
