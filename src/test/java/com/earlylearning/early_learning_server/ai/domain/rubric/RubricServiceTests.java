package com.earlylearning.early_learning_server.ai.domain.rubric;
import com.earlylearning.early_learning_server.ai.domain.rubric.RubricService;
import com.earlylearning.early_learning_server.ai.domain.rubric.RubricProperties;
import com.earlylearning.early_learning_server.ai.domain.rubric.MicroDimensionCode;
import com.earlylearning.early_learning_server.ai.domain.rubric.MacroDimensionCode;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RubricServiceTests {

    private static final String CURRENT = "RUBRIC_2026_01";

    private final RubricService service = new RubricService(new RubricProperties(CURRENT));

    @Test
    void omittingVersionUsesTheServerFixedOne() {
        assertThat(service.resolveVersion(null)).isEqualTo(CURRENT);
        assertThat(service.resolveVersion("  ")).isEqualTo(CURRENT);
    }

    @Test
    void passingTheCurrentVersionIsAccepted() {
        assertThat(service.resolveVersion(CURRENT)).isEqualTo(CURRENT);
    }

    @Test
    void unavailableVersionIsRejectedAndNeverSilentlySwitched() {
        assertThatThrownBy(() -> service.resolveVersion("RUBRIC_OLD_2025"))
                .isInstanceOf(BusinessException.class)
                //  message 是「可展示的脱敏说明，不包含输入文本」：
                // 服务端自己的当前版本可以写，请求里带的那个版本号不能回显。
                .hasMessageContaining(CURRENT)
                .hasMessageNotContaining("RUBRIC_OLD_2025")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RUBRIC_UNAVAILABLE);

        // 换版必须被拒，而不是"用当前版本继续"
        assertThat(service.currentVersion()).isEqualTo(CURRENT);
    }

    @Test
    void dimensionSetsAreCompleteAndOrdered() {
        assertThat(service.macroDimensions()).hasSize(5)
                .containsExactly(MacroDimensionCode.EVENT_SEQUENCE,
                        MacroDimensionCode.PLOT_STRUCTURE,
                        MacroDimensionCode.THEME,
                        MacroDimensionCode.COHERENCE,
                        MacroDimensionCode.CAUSAL_LOGIC);
        assertThat(service.microDimensions()).hasSize(5)
                .containsExactly(MicroDimensionCode.VOCABULARY_DIVERSITY,
                        MicroDimensionCode.MENTAL_STATE_WORDS,
                        MicroDimensionCode.SYNTACTIC_COMPLEXITY,
                        MicroDimensionCode.REFERENTIAL_COHESION,
                        MicroDimensionCode.CONJUNCTION_COHESION);
    }

    @Test
    void everyDimensionHasADisplayName() {
        assertThat(service.macroDimensions()).allSatisfy(code ->
                assertThat(code.displayName()).isNotBlank());
        assertThat(service.microDimensions()).allSatisfy(code ->
                assertThat(code.displayName()).isNotBlank());
    }
}
