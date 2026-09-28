package com.earlylearning.early_learning_server.ai.score;

import java.time.Instant;
import java.util.UUID;

import com.earlylearning.early_learning_server.ai.rubric.RubricService;
import com.earlylearning.early_learning_server.ai.task.AiTask;
import com.earlylearning.early_learning_server.ai.task.AiTaskRunner;
import com.earlylearning.early_learning_server.ai.task.AiTaskSubmission;
import com.earlylearning.early_learning_server.ai.task.FailedStage;
import com.earlylearning.early_learning_server.ai.task.TaskFailureCode;
import com.earlylearning.early_learning_server.ai.task.TaskKind;
import com.earlylearning.early_learning_server.ai.task.TaskStage;
import com.earlylearning.early_learning_server.ai.web.StoryScoringRequest;
import com.earlylearning.early_learning_server.ai.web.StoryScoringRequestValidator;
import com.earlylearning.early_learning_server.ai.web.TaskHandleResponse;
import com.earlylearning.early_learning_server.common.idempotency.InputFingerprint;
import org.springframework.stereotype.Service;

/**
 * 故事评分任务的提交。
 *
 * <p>幂等与重试语义由 {@link AiTaskSubmission} 共用；这里负责评分特有的部分：请求校验、
 * **评分标准版本的确定**、以及调用模型并对输出做运行时校验。
 *
 * <p>版本规则（契约原文）：
 * <ul>
 *   <li>首次可省略版本 → 用服务端固定配置，并在任务凭据里返回**实际**版本；</li>
 *   <li>指定版本当前不可用 → 503 {@code RUBRIC_UNAVAILABLE}，**不静默换版**；</li>
 *   <li>**同一任务的重试继续使用任务记录的实际评分版本**——所以重试时以任务记录的版本为准，
 *       请求若指定了别的版本则按"不可用"拒绝，而不是悄悄改用新版本。</li>
 * </ul>
 *
 * <p>权限：契约要求按教师隔离任务；本模块不校验。
 */
@Service
public class AiStoryScoringService {

    private static final int FIRST_ATTEMPT = 0;

    private final AiTaskSubmission submission;
    private final AiTaskRunner runner;
    private final RubricService rubricService;
    private final StoryScorer scorer;
    private final ScoreValidator scoreValidator;
    private final StoryScoringRequestValidator requestValidator;
    private final ScoringImageResolver imageResolver;

    public AiStoryScoringService(AiTaskSubmission submission,
                                 AiTaskRunner runner,
                                 RubricService rubricService,
                                 StoryScorer scorer,
                                 ScoreValidator scoreValidator,
                                 StoryScoringRequestValidator requestValidator,
                                 ScoringImageResolver imageResolver) {
        this.submission = submission;
        this.runner = runner;
        this.rubricService = rubricService;
        this.scorer = scorer;
        this.scoreValidator = scoreValidator;
        this.requestValidator = requestValidator;
        this.imageResolver = imageResolver;
    }

    public TaskHandleResponse submit(StoryScoringRequest request, int retryAttempt) {
        requestValidator.validate(request);
        String resolvedVersion = rubricService.resolveVersion(request.rubricVersion());
        String fingerprint = fingerprintOf(request);

        AiTaskSubmission.Outcome outcome = submission.resolveAndRegister(request.requestId(), fingerprint,
                retryAttempt, () -> newTask(request, resolvedVersion));
        AiTask task = switch (outcome.action()) {
            case CREATE -> runScoring(outcome.task(), toInput(request), resolvedVersion);
            case RESTART -> restart(outcome.task(), request);
            case REPLAY -> outcome.task();
        };
        return TaskHandleResponse.from(task);
    }

    /** 新建任务；只在首次提交（CREATE）时调用，登记由 {@code resolveAndRegister} 一并完成。 */
    private AiTask newTask(StoryScoringRequest request, String rubricVersion) {
        return new AiTask(
                UUID.randomUUID().toString(),
                request.requestId(),
                request.inputRevision(),
                TaskKind.STORY_SCORING,
                FIRST_ATTEMPT,
                rubricVersion,
                request.businessType(),
                request.activityId(),
                Instant.now());
    }

    /** 重试沿用**任务记录的版本**，不能因为请求或配置变了就换标准。 */
    private AiTask restart(AiTask task, StoryScoringRequest request) {
        String recordedVersion = rubricService.versionForRetry(task.getRubricVersion(), request.rubricVersion());
        task.restart(Instant.now());
        return runScoring(task, toInput(request), recordedVersion);
    }

    /**
     * 把请求解析成适配器的领域输入：**在这里把图片解析好**（内联的解码、要取回的取回）。
     *
     * <p>放在提交的同步路径上而不是任务里：图片有问题就让 400/413 立刻出现，
     * 语义与请求校验一致；落成任务失败的话，"base64 非法"会被表达成模型输出不合法，
     * 与单题评分（同样在提交时解析）也保持一致。
     */
    private StoryScoringInput toInput(StoryScoringRequest request) {
        return new StoryScoringInput(request.confirmedText(), request.storyContext(),
                request.contentItems(), imageResolver.resolve(request.images()));
    }

    private AiTask runScoring(AiTask task, StoryScoringInput input, String rubricVersion) {
        runner.run(task, TaskStage.SCORING, FailedStage.SCORE, TaskFailureCode.MODEL_OUTPUT_INVALID, () -> {
            var score = scorer.score(input, rubricVersion);
            // 模型输出必须过运行时语义校验，不合格就不能变成"成功结果"
            scoreValidator.validate(score, rubricVersion, input.confirmedText(), input.contentItems());
            return new StoryScoringResult(score);
        });
        return task;
    }

    /**
     * 指纹覆盖请求的全部输入，但**不含评分标准版本**：版本由任务记录持有，
     * 重试要沿用它；若把版本算进指纹，配置更新后连正常的重试都会被判成"换了输入"。
     */
    private String fingerprintOf(StoryScoringRequest request) {
        StringBuilder groups = new StringBuilder();
        for (var item : request.contentItems()) {
            groups.append(item.contentItemId()).append('=').append(item.rubricItemCode())
                    .append(':').append(String.join(",", item.imageFileCodes())).append('|');
        }
        StringBuilder images = new StringBuilder();
        for (var image : request.images()) {
            images.append(image.fileCode()).append('=').append(image.kind())
                    .append(':').append(image.contentBase64() == null ? "-" : InputFingerprint.sha256Hex(image.contentBase64()))
                    .append(':').append(image.description()).append('|');
        }
        return InputFingerprint.of(
                TaskKind.STORY_SCORING.name(),
                request.requestId(),
                request.inputRevision(),
                String.valueOf(request.businessType()),
                request.activityId(),
                request.confirmedText(),
                request.storyContext(),
                groups.toString(),
                images.toString());
    }
}
