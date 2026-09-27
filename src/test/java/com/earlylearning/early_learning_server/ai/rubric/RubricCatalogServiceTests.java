package com.earlylearning.early_learning_server.ai.rubric;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.earlylearning.early_learning_server.ai.task.BusinessType;
import com.earlylearning.early_learning_server.ai.score.ProductivityStat;
import com.earlylearning.early_learning_server.ai.web.AiRubricController;
import com.earlylearning.early_learning_server.ai.web.RubricCatalogItem;
import com.earlylearning.early_learning_server.ai.web.RubricCatalogResponse;
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
            "NARRATIVE_CONTENT_04", "NARRATIVE_CONTENT_05",
            "DETAIL_EXPANSION",
            "NARRATIVE_PRODUCTIVITY",
            "VOCABULARY_DIVERSITY", "MENTAL_STATE_WORDS", "SYNTACTIC_COMPLEXITY",
            "REFERENTIAL_COHESION", "CONJUNCTION_COHESION",
            "QUESTION_REASONING");

    private final RubricCatalogService service = new RubricCatalogService(new RubricProperties(VERSION));

    @Test
    void itemsAreTheContractCodesInTheContractOrder() {
        RubricCatalogResponse catalog = service.catalog();

        assertThat(catalog.items()).extracting(RubricCatalogItem::itemCode)
                .containsExactlyElementsOf(EXPECTED_CODES);
    }

    @Test
    void catalogCoversEveryDimensionCodeSoAddingOneCannotBeForgotten() {
        Set<String> itemCodes = new LinkedHashSet<>(service.catalog().items().stream()
                .map(RubricCatalogItem::itemCode).toList());

        Set<String> dimensionCodes = new LinkedHashSet<>();
        for (MacroDimensionCode code : MacroDimensionCode.values()) {
            dimensionCodes.add(code.name());
        }
        for (MicroDimensionCode code : MicroDimensionCode.values()) {
            dimensionCodes.add(code.name());
        }
        // 两个维度枚举必须**全部**出现在目录里
        assertThat(itemCodes).containsAll(dimensionCodes);
        // 加上图片分组、叙事产生性与问答推理，不多不少
        assertThat(itemCodes).hasSize(dimensionCodes.size() + 5 + 1 + 1);
        assertThat(itemCodes).contains(ProductivityStat.ITEM_CODE, "QUESTION_REASONING");
    }

    @Test
    void catalogCarriesTheActualVersionAndFrozenSchemaVersion() {
        RubricCatalogResponse catalog = service.catalog();

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
