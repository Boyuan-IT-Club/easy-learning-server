package com.earlylearning.early_learning_server.ai.service.rubric;
import com.earlylearning.early_learning_server.ai.model.rubric.MacroDimensionCode;
import com.earlylearning.early_learning_server.ai.model.rubric.MicroDimensionCode;
import com.earlylearning.early_learning_server.ai.model.rubric.RubricProperties;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.earlylearning.early_learning_server.ai.controller.AiRubricController;
import com.earlylearning.early_learning_server.ai.model.rubric.RubricCatalog;
import com.earlylearning.early_learning_server.ai.model.rubric.RubricCatalogEntry;
import com.earlylearning.early_learning_server.ai.model.scoring.story.ProductivityStat;
import com.earlylearning.early_learning_server.ai.model.task.BusinessType;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 评分条目目录：条目集合与顺序，以及"维度枚举改了、条目忘了改"这类漂移。
 */
class RubricCatalogServiceTests {

    private static final String VERSION = "RUBRIC_2026_01";

    /** 契约示例给出的顺序，也正是报告里各轴的顺序。 */
    private static final List<String> EXPECTED_CODES = List.of(
            "EVENT_SEQUENCE", "PLOT_STRUCTURE", "THEME", "COHERENCE", "CAUSAL_LOGIC",
            "NARRATIVE_CONTENT_01", "NARRATIVE_CONTENT_02", "NARRATIVE_CONTENT_03",
            "NARRATIVE_CONTENT_04", "NARRATIVE_CONTENT_05", "NARRATIVE_CONTENT_06",
            "NARRATIVE_PRODUCTIVITY",
            "VOCABULARY_DIVERSITY", "MENTAL_STATE_WORDS", "SYNTACTIC_COMPLEXITY",
            "REFERENTIAL_COHESION", "CONJUNCTION_COHESION",
            "QUESTION_REASONING");

    /**
     * 条目显示名也要钉住。
     *
     * <p>名字直接出现在报告里，而契约自身在这点上是矛盾的：OpenAPI 的 {@code /api/ai/rubrics} 示例把
     * 两个衔接条目都写成「衔接使用」，同一份契约的 AIScore 示例与技术方案「报告结构」表写的是
     * 「指称衔接 / 连词衔接」。实现取后者；这里把它冻结下来，避免以后又一次静默漂移。
     */
    private static final java.util.List<String> EXPECTED_NAMES = java.util.List.of(
            "事件顺序", "情节结构", "主题体现", "故事连贯性", "因果逻辑",
            "图1", "图2、3", "图4、5", "图6", "图7-1", "图7-2",
            "叙事产生性（量化的统计）",
            "词汇丰富度", "心理状态词", "句法复杂度", "指称衔接", "连词衔接",
            "统一问答推理");

    private final RubricCatalogService service = new RubricCatalogService(new RubricProperties(VERSION));

    @Test
    void itemsAreTheContractCodesInTheContractOrder() {
        RubricCatalog catalog = service.catalog();

        assertThat(catalog.items()).extracting(RubricCatalogEntry::itemCode)
                .containsExactlyElementsOf(EXPECTED_CODES);

        assertThat(catalog.items()).extracting(RubricCatalogEntry::itemName)
                .containsExactlyElementsOf(EXPECTED_NAMES);
    }

    @Test
    void catalogCoversEveryDimensionCodeSoAddingOneCannotBeForgotten() {
        Set<String> itemCodes = new LinkedHashSet<>(service.catalog().items().stream()
                .map(RubricCatalogEntry::itemCode).toList());

        Set<String> dimensionCodes = new LinkedHashSet<>();
        for (MacroDimensionCode code : MacroDimensionCode.values()) {
            dimensionCodes.add(code.name());
        }
        for (MicroDimensionCode code : MicroDimensionCode.values()) {
            dimensionCodes.add(code.name());
        }
        // 两个维度枚举必须全部出现在目录里
        assertThat(itemCodes).containsAll(dimensionCodes);
        // 加上图片分组、叙事产生性与问答推理，不多不少
        assertThat(itemCodes).hasSize(dimensionCodes.size() + 6 + 1 + 1);
        assertThat(itemCodes).contains(ProductivityStat.ITEM_CODE, "QUESTION_REASONING");
    }

    @Test
    void catalogCarriesTheActualVersionAndFrozenSchemaVersion() {
        RubricCatalog catalog = service.catalog();

        assertThat(catalog.rubricVersion()).isEqualTo(VERSION);
        assertThat(catalog.aiScoreSchemaVersion()).isEqualTo(2);
        assertThat(catalog.businessTypes()).containsExactly(BusinessType.ASSESSMENT, BusinessType.CLASSROOM);
    }

    @Test
    void missingServerSideRubricConfigurationYields503() {
        RubricCatalogService broken = new RubricCatalogService(new RubricProperties(" "));

        assertThatThrownBy(broken::catalog)
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RUBRIC_UNAVAILABLE);
    }

    @Test
    void endpointReturnsTheCatalogInTheStandardEnvelope() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new AiRubricController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/ai/rubrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.rubric_version").value(VERSION))
                .andExpect(jsonPath("$.data.ai_score_schema_version").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(18));
    }
}
