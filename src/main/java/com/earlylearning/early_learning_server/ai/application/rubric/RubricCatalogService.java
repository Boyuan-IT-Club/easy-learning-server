package com.earlylearning.early_learning_server.ai.application.rubric;
import com.earlylearning.early_learning_server.ai.interfaces.dto.RubricCatalogItem;
import com.earlylearning.early_learning_server.ai.domain.rubric.RubricProperties;
import com.earlylearning.early_learning_server.ai.domain.rubric.MicroDimensionCode;
import com.earlylearning.early_learning_server.ai.domain.rubric.MacroDimensionCode;

import java.util.ArrayList;
import java.util.List;

import com.earlylearning.early_learning_server.ai.domain.task.BusinessType;
import com.earlylearning.early_learning_server.ai.domain.task.TaskKind;
import com.earlylearning.early_learning_server.ai.domain.rubric.RubricCatalog;
import com.earlylearning.early_learning_server.ai.domain.rubric.RubricCatalogEntry;
import com.earlylearning.early_learning_server.ai.domain.scoring.story.AiScore;
import com.earlylearning.early_learning_server.ai.domain.scoring.story.ProductivityStat;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import org.springframework.stereotype.Service;

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
 * <p>：目录可缓存复用，正常评估、课堂与评分前都无需调用；没有版本查询参数、
 * 不列历史版本、不提供编辑或自动更新。
 */
@Service
public class RubricCatalogService {

    private static final String SHARED_APPLICABILITY = "所有故事共用的统一叙事或问答评价条目。";
    private static final String CONTENT_APPLICABILITY =
            "所有故事通用；本故事图片通过 content_items 显式映射到此条目，不按图片序号猜测。";

    private static final String QUESTION_REASONING_CODE = "QUESTION_REASONING";
    private static final String PRODUCTIVITY_NAME = "叙事产生性（量化的统计）";
    private static final String QUESTION_REASONING_NAME = "统一问答推理";

    /**
     * 标准七图材料的图片分组条目(依据《多图叙事评分细则》):图 2、3 与图 4、5 各是一个条目,
     * 图 7 拆两行——图7-1 评叙事内容、图7-2 评细节拓展。材料编排固定,不做成配置。
     */
    private static final List<ContentItemSpec> STANDARD_CONTENT_ITEMS = List.of(
            new ContentItemSpec("NARRATIVE_CONTENT_01", "图1"),
            new ContentItemSpec("NARRATIVE_CONTENT_02", "图2、3"),
            new ContentItemSpec("NARRATIVE_CONTENT_03", "图4、5"),
            new ContentItemSpec("NARRATIVE_CONTENT_04", "图6"),
            new ContentItemSpec("NARRATIVE_CONTENT_05", "图7-1"),
            new ContentItemSpec("NARRATIVE_CONTENT_06", "图7-2"));

    private final RubricProperties properties;

    public RubricCatalogService(RubricProperties properties) {
        this.properties = properties;
    }

    /**
     * 图片分组可映射的评分条目编号。评估材料发布时校验 content_items 的
     * rubric_item_code 是否在统一规则里，编号集合与目录一致。
     */
    public java.util.Set<String> contentItemCodes() {
        return STANDARD_CONTENT_ITEMS.stream()
                .map(ContentItemSpec::code)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * @return 领域对象；转成 HTTP 形状是 interfaces 层的事
     * @throws BusinessException 服务端统一评分配置缺失或不可用（503 {@code RUBRIC_UNAVAILABLE}）
     */
    public RubricCatalog catalog() {
        String version = properties.version();
        if (version == null || version.isBlank()) {
            throw new BusinessException(ErrorCode.RUBRIC_UNAVAILABLE, "服务端统一评分配置不可用");
        }
        return new RubricCatalog(
                version,
                AiScore.SCHEMA_VERSION,
                List.of(BusinessType.values()),
                items());
    }

    /** 条目顺序即契约示例的顺序，也是报告里各轴的顺序。 */
    private List<RubricCatalogEntry> items() {
        List<RubricCatalogEntry> items = new ArrayList<>();
        items.add(shared(MacroDimensionCode.EVENT_SEQUENCE.name(), MacroDimensionCode.EVENT_SEQUENCE.displayName()));
        items.add(shared(MacroDimensionCode.PLOT_STRUCTURE.name(), MacroDimensionCode.PLOT_STRUCTURE.displayName()));
        items.add(shared(MacroDimensionCode.THEME.name(), MacroDimensionCode.THEME.displayName()));
        items.add(shared(MacroDimensionCode.COHERENCE.name(), MacroDimensionCode.COHERENCE.displayName()));
        items.add(shared(MacroDimensionCode.CAUSAL_LOGIC.name(), MacroDimensionCode.CAUSAL_LOGIC.displayName()));
        for (ContentItemSpec spec : STANDARD_CONTENT_ITEMS) {
            items.add(new RubricCatalogEntry(spec.code(), spec.name(), TaskKind.STORY_SCORING, CONTENT_APPLICABILITY));
        }
        items.add(shared(ProductivityStat.ITEM_CODE, PRODUCTIVITY_NAME));
        for (MicroDimensionCode code : MicroDimensionCode.values()) {
            items.add(shared(code.name(), code.displayName()));
        }
        items.add(new RubricCatalogEntry(QUESTION_REASONING_CODE, QUESTION_REASONING_NAME,
                TaskKind.ANSWER_SCORING, SHARED_APPLICABILITY));
        return List.copyOf(items);
    }

    private RubricCatalogEntry shared(String code, String name) {
        return new RubricCatalogEntry(code, name, TaskKind.STORY_SCORING, SHARED_APPLICABILITY);
    }

    private record ContentItemSpec(String code, String name) {
    }
}
