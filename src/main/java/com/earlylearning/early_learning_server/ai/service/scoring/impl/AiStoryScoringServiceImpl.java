package com.earlylearning.early_learning_server.ai.service.scoring.impl;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.earlylearning.early_learning_server.ai.client.task.AiTaskRunner;
import com.earlylearning.early_learning_server.ai.model.rubric.RubricService;
import com.earlylearning.early_learning_server.ai.model.scoring.story.ScoreValidator;
import com.earlylearning.early_learning_server.ai.model.scoring.story.StoryScorer;
import com.earlylearning.early_learning_server.ai.model.scoring.story.StoryScoringCommand;
import com.earlylearning.early_learning_server.ai.model.scoring.story.StoryScoringInput;
import com.earlylearning.early_learning_server.ai.model.scoring.story.StoryScoringResult;
import com.earlylearning.early_learning_server.ai.model.task.AiTask;
import com.earlylearning.early_learning_server.ai.model.task.AiTaskSubmission;
import com.earlylearning.early_learning_server.ai.model.task.FailedStage;
import com.earlylearning.early_learning_server.ai.model.task.TaskFailureCode;
import com.earlylearning.early_learning_server.ai.model.task.TaskKind;
import com.earlylearning.early_learning_server.ai.model.task.TaskStage;
import com.earlylearning.early_learning_server.ai.service.scoring.AiStoryScoringService;
import com.earlylearning.early_learning_server.ai.service.scoring.ScoringImageService;
import com.earlylearning.early_learning_server.common.idempotency.InputFingerprint;

import lombok.RequiredArgsConstructor;

/** {@link AiStoryScoringService} 的实现。 */
@Service
@RequiredArgsConstructor
public class AiStoryScoringServiceImpl implements AiStoryScoringService {

    private final AiTaskSubmission aiTaskSubmission;
    private final AiTaskRunner aiTaskRunner;
    private final RubricService rubricService;
    private final StoryScorer storyScorer;
    private final ScoreValidator scoreValidator;
    private final ScoringImageService scoringImageService;

    @Override
    public AiTask submit(StoryScoringCommand command, int retryAttempt) {
        String resolvedVersion = rubricService.resolveVersion(command.rubricVersion());
        return aiTaskSubmission.submitAndRun(command.requestId(), fingerprintOf(command), retryAttempt,
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
                command.contentItems(), scoringImageService.resolve(command.images()));
    }

    private AiTask runScoring(AiTask task, StoryScoringInput input, String rubricVersion) {
        aiTaskRunner.run(task, TaskStage.SCORING, FailedStage.SCORE, TaskFailureCode.MODEL_OUTPUT_INVALID, () -> {
            var score = storyScorer.score(input, rubricVersion);
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
