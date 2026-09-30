package com.earlylearning.early_learning_server.material.application;

import java.util.Set;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.material.domain.config.ActivityConfig;
import com.earlylearning.early_learning_server.material.domain.config.NarrationActivity;
import com.earlylearning.early_learning_server.material.domain.config.QuestioningActivity;
import com.earlylearning.early_learning_server.material.domain.config.SortingActivity;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * config.json 语义校验的行为测试：每个契约错误码至少一条触发路径，外加一条完整转换。
 */
class MaterialConfigValidatorTests {

    private static final Set<String> FILES = Set.of("img1.png", "img2.png", "story.wav");
    private static final Set<String> RUBRIC = Set.of("NARRATIVE_CONTENT_01", "NARRATIVE_CONTENT_06");
    private static final Set<String> GRAMMAR = Set.of("G_PAST");

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final MaterialConfigValidator validator = new MaterialConfigValidator();

    @Test
    void buildsConfigWithConvertedFileCodes() {
        ValidatedMaterial material = validator.build(validConfig(), FILES, RUBRIC, GRAMMAR,
                (fileName, fieldPath) -> "CF_" + fileName.replace('.', '_').toUpperCase());

        assertThat(material.officialMaterialCode()).isEqualTo("MAT_A");
        assertThat(material.contentVersion()).isEqualTo("v1");
        ActivityConfig config = material.activityConfig();
        assertThat(config.schemaVersion()).isEqualTo(2);

        SortingActivity sorting = (SortingActivity) config.activities().get(0);
        assertThat(sorting.config().items()).extracting(item -> item.fileCode())
                .containsExactly("CF_IMG1_PNG", "CF_IMG2_PNG");
        NarrationActivity narration = (NarrationActivity) config.activities().get(2);
        assertThat(narration.config().audioFileCode()).isEqualTo("CF_STORY_WAV");
        assertThat(narration.config().contentItems().get(0).rubricItemCode()).isEqualTo("NARRATIVE_CONTENT_01");
    }

    @Test
    void rejectsWrongSchemaVersion() {
        assertThatThrownBy(() -> validator.build(validConfig().put("schema_version", 3),
                FILES, RUBRIC, GRAMMAR, noopResolver()))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode().name()).isEqualTo("INVALID_ACTIVITY_CONFIG");
                    assertThat(ex.getDetails().fieldPath()).isEqualTo("/schema_version");
                });
    }

    @Test
    void rejectsUnknownRootField() {
        assertThatThrownBy(() -> validator.build(validConfig().put("extra", 1),
                FILES, RUBRIC, GRAMMAR, noopResolver()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getDetails().fieldPath()).isEqualTo("/extra"));
    }

    @Test
    void rejectsMissingStoryNarration() {
        JsonNode config = JSON.readTree(validConfig().toString());
        activitiesOf(config).remove(2);
        assertThatThrownBy(() -> validator.build(config, FILES, RUBRIC, GRAMMAR, noopResolver()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode().name()).isEqualTo("STORY_NARRATION_REQUIRED"));
    }

    @Test
    void rejectsDuplicateActivityType() {
        JsonNode config = JSON.readTree(validConfig().toString());
        activitiesOf(config).add(activitiesOf(config).get(0).deepCopy());
        assertThatThrownBy(() -> validator.build(config, FILES, RUBRIC, GRAMMAR, noopResolver()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode().name()).isEqualTo("INVALID_ACTIVITY_CONFIG"));
    }

    @Test
    void rejectsUnknownRubricMapping() {
        JsonNode config = JSON.readTree(validConfig().toString());
        contentItemOf(config).put("rubric_item_code", "NARRATIVE_CONTENT_99");
        assertThatThrownBy(() -> validator.build(config, FILES, RUBRIC, GRAMMAR, noopResolver()))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode().name()).isEqualTo("INVALID_RUBRIC_MAPPING");
                    assertThat(ex.getDetails().rubricItemCode()).isEqualTo("NARRATIVE_CONTENT_99");
                });
    }

    @Test
    void rejectsFileReferenceMissingFromPackage() {
        JsonNode config = JSON.readTree(validConfig().toString());
        ((tools.jackson.databind.node.ObjectNode) activitiesOf(config).get(2).path("config"))
                .put("audio_file_name", "absent.wav");
        assertThatThrownBy(() -> validator.build(config, FILES, RUBRIC, GRAMMAR,
                (fileName, fieldPath) -> {
                    if (!"absent.wav".equals(fileName)) {
                        return "CF_OK";
                    }
                    throw new BusinessException(
                            com.earlylearning.early_learning_server.common.error.ErrorCode.INVALID_RESOURCE_REFERENCE,
                            "配置引用的文件未在包内找到",
                            new com.earlylearning.early_learning_server.common.error.ApiErrorDetails(
                                    fieldPath, null, null, null, null, null, fileName));
                }))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode().name()).isEqualTo("INVALID_RESOURCE_REFERENCE");
                    assertThat(ex.getDetails().fileName()).isEqualTo("absent.wav");
                    assertThat(ex.getDetails().fieldPath())
                            .isEqualTo("/activities/2/config/audio_file_name");
                });
    }

    @Test
    void rejectsOrderMismatchInSorting() {
        JsonNode config = JSON.readTree(validConfig().toString());
        ((tools.jackson.databind.node.ArrayNode) activitiesOf(config).get(0).path("config")
                .get("correct_order")).add("s1");
        assertThatThrownBy(() -> validator.build(config, FILES, RUBRIC, GRAMMAR, noopResolver()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode().name()).isEqualTo("INVALID_ACTIVITY_CONFIG"));
    }

    @Test
    void rejectsUnknownGrammarReference() {
        JsonNode config = JSON.readTree(validConfig().toString());
        ((tools.jackson.databind.node.ArrayNode) activitiesOf(config).get(1).path("config")
                .path("questions").get(0).get("grammar")).add("G_MISSING");
        assertThatThrownBy(() -> validator.build(config, FILES, RUBRIC, GRAMMAR, noopResolver()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode().name()).isEqualTo("INVALID_GRAMMAR_REFERENCE"));
    }

    @Test
    void collectsReferencedGrammarAndFileCodes() {
        JsonNode frozen = JSON.readTree("""
                {"activities":[
                  {"type":"IMAGE_SORTING","config":{"items":[{"file_code":"CF_A"},{"file_code":"CF_B"}]}},
                  {"type":"QUESTION_ANSWERING","config":{"questions":[{"grammar":["G_X"]},{"grammar":["G_Y"]}]}},
                  {"type":"STORY_NARRATION","config":{"audio_file_code":"CF_C",
                    "content_items":[{"image_file_codes":["CF_A","CF_D"]}]}}
                ]}
                """);
        assertThat(validator.referencedGrammarCodes(frozen)).containsExactlyInAnyOrder("G_X", "G_Y");
        assertThat(validator.referencedFileCodes(frozen))
                .containsExactly("CF_A", "CF_B", "CF_C", "CF_D");
    }

    private tools.jackson.databind.node.ObjectNode validConfig() {
        return (tools.jackson.databind.node.ObjectNode) JSON.readTree("""
                {
                  "official_material_code": "MAT_A",
                  "content_version": "v1",
                  "name": "自测材料",
                  "schema_version": 2,
                  "story_context": "课间踢球砸碎玻璃。",
                  "activities": [
                    {"activity_id":"act_sort","type":"IMAGE_SORTING","config":{
                      "items":[{"item_id":"s1","file_name":"img1.png"},{"item_id":"s2","file_name":"img2.png"}],
                      "correct_order":["s1","s2"]}},
                    {"activity_id":"act_q","type":"QUESTION_ANSWERING","config":{
                      "questions":[{"question_id":"q1","text":"谁打碎了玻璃？","hint":"","grammar":[]}]}},
                    {"activity_id":"act_story","type":"STORY_NARRATION","config":{
                      "audio_file_name":"story.wav",
                      "content_items":[{"content_item_id":"c1","image_file_names":["img1.png","img2.png"],
                                        "rubric_item_code":"NARRATIVE_CONTENT_01"}]}}
                  ]
                }
                """);
    }

    private static tools.jackson.databind.node.ArrayNode activitiesOf(JsonNode config) {
        return (tools.jackson.databind.node.ArrayNode) config.get("activities");
    }

    private static tools.jackson.databind.node.ObjectNode contentItemOf(JsonNode config) {
        return (tools.jackson.databind.node.ObjectNode) activitiesOf(config).get(2)
                .path("config").path("content_items").get(0);
    }

    private MaterialConfigValidator.FileRefResolver noopResolver() {
        return (fileName, fieldPath) -> "CF_OK";
    }
}
