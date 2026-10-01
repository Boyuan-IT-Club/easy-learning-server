package com.earlylearning.early_learning_server.ai.service.scoring;

import java.time.Instant;
import java.util.UUID;

import com.earlylearning.early_learning_server.ai.model.rubric.RubricService;
import com.earlylearning.early_learning_server.ai.model.task.AiTask;
import com.earlylearning.early_learning_server.ai.service.task.AiTaskRunner;
import com.earlylearning.early_learning_server.ai.model.task.AiTaskSubmission;
import com.earlylearning.early_learning_server.ai.model.task.FailedStage;
import com.earlylearning.early_learning_server.ai.model.task.TaskFailureCode;
import com.earlylearning.early_learning_server.ai.model.task.TaskKind;
import com.earlylearning.early_learning_server.ai.model.task.TaskStage;
import com.earlylearning.early_learning_server.ai.model.scoring.story.StoryScoringCommand;
import com.earlylearning.early_learning_server.ai.model.scoring.story.StoryScoringInput;
import com.earlylearning.early_learning_server.ai.model.scoring.story.StoryScoringResult;
import com.earlylearning.early_learning_server.ai.model.scoring.story.StoryScorer;
import com.earlylearning.early_learning_server.ai.model.scoring.story.ScoreValidator;
import com.earlylearning.early_learning_server.common.idempotency.InputFingerprint;
import org.springframework.stereotype.Service;

/**
 * 故事评分任务的提交。
 *
 * <p>幂等与重试语义由 {@link AiTaskSubmission} 共用；这里负责评分特有的部分：评分依据版本的确定、
 * 图片解析，以及调用模型并对输出做运行时校验。请求形状的校验在 controller 层完成，这里收到的是
 * {@link StoryScoringCommand}；返回领域对象 {@link AiTask}，HTTP 形状由 controller 转换。
 *
 * <p>版本规则：
 * <ul>
 *   <li>首次可省略版本 → 用服务端固定配置，并在任务凭据里返回实际版本；</li>
 *   <li>指定版本当前不可用 → 503 {@code RUBRIC_UNAVAILABLE}，不静默换版；</li>
 *   <li>同一任务的重试继续使用任务记录的实际评分版本——所以重试时以任务记录的版本为准，
 *       请求若指定了别的版本则按"不可用"拒绝，而不是悄悄改用新版本。</li>
 * </ul>
 *
 * <p>权限：按教师隔离任务；本模块不校验。
 */
@Service
public class AiStoryScoringService {

    private final AiTaskSubmission submission;
    private final AiTaskRunner runner;
    private final RubricService rubricService;
    private final StoryScorer scorer;
    private final ScoreValidator scoreValidator;
    private final ScoringImageResolver imageResolver;

    public AiStoryScoringService(AiTaskSubmission submission,
                                 AiTaskRunner runner,
                                 RubricService rubricService,
                                 StoryScorer scorer,
                                 ScoreValidator scoreValidator,
                                 ScoringImageResolver imageResolver) {
        this.submission = submission;
        this.runner = runner;
        this.rubricService = rubricService;
        this.scorer = scorer;
        this.scoreValidator = scoreValidator;
        this.imageResolver = imageResolver;
    }

    public AiTask submit(StoryScoringCommand command, int retryAttempt) {
        String resolvedVersion = rubricService.resolveVersion(command.rubricVersion());
        return submission.submitAndRun(command.requestId(), fingerprintOf(command), retryAttempt,
                () -> newTask(command, resolvedVersion),
                // CREATE：任务创建时已写入解析好的版本，直接沿用
                task -> runScoring(task, toInput(command), task.getRubricVersion()),
                // RESTART：先核对"请求的版本 vs 任务记录的版本"再拉回队列——
                // 校验不过时任务保持 FAILED，不产生"已重启却没有工作"的中间态
                task -> {
                    String recordedVersion = rubricService.versionForRetry(
                            task.getRubricVersion(), command.rubricVersion());
                    task.restart(Instant.now());
                    return runScoring(task, toInput(command), recordedVersion);
                });
    }

    /** 新建任务；只在首次提交（CREATE）时调用，登记由 {@code resolveAndRegister} 一并完成。 */
    private AiTask newTask(StoryScoringCommand command, String rubricVersion) {
        return new AiTask(
                UUID.randomUUID().toString(),
                command.requestId(),
                command.inputRevision(),
                TaskKind.STORY_SCORING,
                AiTaskSubmission.FIRST_ATTEMPT,
                rubricVersion,
                command.businessType(),
                command.activityId(),
                Instant.now());
    }


    /**
     * 把命令解析成适配器的领域输入：在这里把图片解析好（内联的解码、要取回的取回）。
     *
     * <p>放在提交的同步路径上而不是任务里：图片有问题就让 400/413 立刻出现，
     * 语义与请求校验一致；落成任务失败的话，"base64 非法"会被表达成模型输出不合法，
     * 与单题评分（同样在提交时解析）也保持一致。
     */
    private StoryScoringInput toInput(StoryScoringCommand command) {
        return new StoryScoringInput(command.confirmedText(), command.storyContext(),
                command.contentItems(), imageResolver.resolve(command.images()));
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
     * 指纹覆盖请求的全部输入，但不含评分依据版本：版本由任务记录持有，
     * 重试要沿用它；若把版本算进指纹，配置更新后连正常的重试都会被判成"换了输入"。
     */
    private String fingerprintOf(StoryScoringCommand command) {
        StringBuilder groups = new StringBuilder();
        for (var item : command.contentItems()) {
            groups.append(item.contentItemId()).append('=').append(item.rubricItemCode())
                    .append(':').append(String.join(",", item.imageFileCodes())).append('|');
        }
        StringBuilder images = new StringBuilder();
        for (var image : command.images()) {
            images.append(image.fileCode()).append('=').append(image.kind())
                    .append(':').append(image.contentBase64() == null ? "-" : InputFingerprint.sha256Hex(image.contentBase64()))
                    .append(':').append(image.description()).append('|');
        }
        return InputFingerprint.of(
                TaskKind.STORY_SCORING.name(),
                command.requestId(),
                command.inputRevision(),
                String.valueOf(command.businessType()),
                command.activityId(),
                command.confirmedText(),
                command.storyContext(),
                groups.toString(),
                images.toString());
    }
}
