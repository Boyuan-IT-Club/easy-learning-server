package com.earlylearning.early_learning_server.ai.service.rubric;

import java.util.Set;

import com.earlylearning.early_learning_server.ai.model.rubric.MacroDimensionCode;
import com.earlylearning.early_learning_server.ai.model.rubric.MicroDimensionCode;
import com.earlylearning.early_learning_server.ai.model.rubric.RubricCatalog;
import com.earlylearning.early_learning_server.common.error.BusinessException;

/**
 * 评分条目目录。
 *
 * <p>目录是服务端统一持有的那套规则的可读版本：条目编号与评分结果里的 {@code item_code} 一一对应，
 * 顺序就是报告里雷达图的轴顺序。
 *
 * <p>条目清单写死在代码里而不是从评分配置读：它与 {@link MacroDimensionCode} /
 * {@link MicroDimensionCode} 必须一致，写在同一处才能让"加了维度却忘了条目"在测试里暴露出来
 * （见 {@code RubricCatalogServiceTests} 的集合一致性用例）。
 *
 * <p>契约要求：目录可缓存复用，正常评估、课堂与评分前都无需调用；没有版本查询参数、
 * 不列历史版本、不提供编辑或自动更新。
 */
public interface RubricCatalogService {

    /**
     * 图片分组可映射的评分条目编号。评估材料发布时校验 content_items 的
     * rubric_item_code 是否在统一规则里，编号集合与目录一致。
     */
    Set<String> contentItemCodes();

    /**
     * @return 领域对象；转成 HTTP 形状是 controller 层的事
     * @throws BusinessException 服务端统一评分配置缺失或不可用（503 {@code RUBRIC_UNAVAILABLE}）
     */
    RubricCatalog catalog();
}
