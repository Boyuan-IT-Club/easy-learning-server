package com.earlylearning.early_learning_server.ai.domain.scoring.story;
import com.earlylearning.early_learning_server.ai.domain.scoring.ScoringGroup;
import com.earlylearning.early_learning_server.ai.domain.scoring.ImageRef;

import java.util.List;

import com.earlylearning.early_learning_server.ai.domain.task.BusinessType;

/**
 * 故事评分的应用服务输入：HTTP 请求经 interfaces 层校验并映射后的形态。
 *
 * <p>与 {@link StoryScoringInput} 的区别：这个还带着提交元信息（request_id 等）与未解析的图片引用，
 * 供登记任务、计算输入指纹；那个是图片解析完毕后交给评分适配器的东西。
 *
 * @param rubricVersion 可省略；省略时服务端采用固定配置的版本，并在任务凭据里返回实际版本
 * @param confirmedText 经教师确认的原话；空字符串表示已确认无回应，不代表缺失或转写失败
 * @param storyContext  经确认的故事依据，不接受占位内容
 * @param contentItems  本次叙事的全部图片分组，按配置顺序
 * @param images        必须为每个分组引用的图片逐一提供真实图片或确认说明，不得只发 file_code
 */
public record StoryScoringCommand(String requestId,
                                  String inputRevision,
                                  BusinessType businessType,
                                  String activityId,
                                  String rubricVersion,
                                  String confirmedText,
                                  String storyContext,
                                  List<ScoringGroup> contentItems,
                                  List<ImageRef> images) {
}
