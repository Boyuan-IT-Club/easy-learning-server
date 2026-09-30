package com.earlylearning.early_learning_server.material.interfaces;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.earlylearning.early_learning_server.common.web.GlobalExceptionHandler;
import com.earlylearning.early_learning_server.material.interfaces.controller.AssessmentMaterialAdminController;
import com.earlylearning.early_learning_server.material.interfaces.controller.AssessmentMaterialCatalogController;
import com.earlylearning.early_learning_server.storage.domain.CloudFile;
import com.earlylearning.early_learning_server.storage.domain.ObjectStorageService;
import com.earlylearning.early_learning_server.storage.infrastructure.CloudFileMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 评估材料五个接口的全链路行为：发布、幂等、版本切换与依赖展开。
 * 走真库与真实解包，只把对象存储换成 Mockito 桩（沿用官方文件上传测试的搭法）。
 */
@SpringBootTest(properties = {
        "aliyun.oss.endpoint=https://oss-cn-hangzhou.fake",
        "aliyun.oss.region=cn-hangzhou",
        "aliyun.oss.bucket-name=fake-bucket",
        "aliyun.oss.access-key-id=fake",
        "aliyun.oss.access-key-secret=fake",
})
class AssessmentMaterialFlowTests {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};

    @Autowired
    private AssessmentMaterialAdminController adminController;

    @Autowired
    private AssessmentMaterialCatalogController catalogController;

    @Autowired
    private GlobalExceptionHandler exceptionHandler;

    @Autowired
    private CloudFileMapper cloudFileMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private ObjectStorageService storage;

    private MockMvc mockMvc;

    /** 已生成包的媒体幂等键前缀，供清理幂等记录用。 */
    private final List<String> mediaKeyPrefixes = new ArrayList<>();

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminController, catalogController)
                .setControllerAdvice(exceptionHandler)
                .build();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM assessment_material WHERE official_material_code LIKE 'ZTESTM%'");
        cloudFileMapper.delete(new QueryWrapper<CloudFile>().likeRight("file_name", "ztestm-"));
        jdbc.update("DELETE FROM idempotency_record WHERE idempotency_key LIKE 'ZTESTM-KEY-%'");
        for (String prefix : mediaKeyPrefixes) {
            jdbc.update("DELETE FROM idempotency_record WHERE idempotency_key LIKE CONCAT(?, '%')", prefix);
        }
    }

    @Test
    void publishConvertsNamesToCodesAndReturnsCreated() throws Exception {
        byte[] zip = zipOf("ZTESTM_A", "v1", "NARRATIVE_CONTENT_01");
        mockMvc.perform(multipart("/admin/assessment-materials")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "file", "material.zip", "application/zip", zip))
                        .header("Idempotency-Key", "ZTESTM-KEY-A"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.official_material_code").value("ZTESTM_A"))
                .andExpect(jsonPath("$.data.content_version").value("v1"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.activity_configs_json.schema_version").value(2))
                .andExpect(jsonPath("$.data.activity_configs_json.activities[0].config.items[0].file_code")
                        .isNotEmpty())
                .andExpect(jsonPath("$.data.activity_configs_json.activities[2].config.audio_file_code")
                        .isNotEmpty());

        List<CloudFile> uploaded = cloudFileMapper.selectList(
                new QueryWrapper<CloudFile>().likeRight("file_name", "ztestm-"));
        assertThat(uploaded).hasSize(3);
        assertThat(uploaded).allMatch(file -> file.getFileCode().startsWith("CF_"));
        verify(storage, atLeast(3)).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void newVersionDisablesThePreviousOne() throws Exception {
        mockMvc.perform(multipart("/admin/assessment-materials").file(file(zipOf("ZTESTM_B", "v1", "NARRATIVE_CONTENT_01")))
                        .header("Idempotency-Key", "ZTESTM-KEY-B1"))
                .andExpect(status().isCreated());
        mockMvc.perform(multipart("/admin/assessment-materials").file(file(zipOf("ZTESTM_B", "v2", "NARRATIVE_CONTENT_02")))
                        .header("Idempotency-Key", "ZTESTM-KEY-B2"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/assessment-materials/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].content_version").value("v1"))
                .andExpect(jsonPath("$.data.items[0].status").value("DISABLED"))
                .andExpect(jsonPath("$.data.items[1].content_version").value("v2"))
                .andExpect(jsonPath("$.data.items[1].status").value("ACTIVE"));
    }

    @Test
    void sameCodeAndVersionIsRejectedAsConflict() throws Exception {
        mockMvc.perform(multipart("/admin/assessment-materials").file(file(zipOf("ZTESTM_C", "v1", "NARRATIVE_CONTENT_01")))
                        .header("Idempotency-Key", "ZTESTM-KEY-C1"))
                .andExpect(status().isCreated());
        mockMvc.perform(multipart("/admin/assessment-materials").file(file(zipOf("ZTESTM_C", "v1", "NARRATIVE_CONTENT_02")))
                        .header("Idempotency-Key", "ZTESTM-KEY-C2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTENT_VERSION_EXISTS"));
    }

    @Test
    void idempotentReplayReturnsOriginalResultWithoutNewUploads() throws Exception {
        byte[] zip = zipOf("ZTESTM_D", "v1", "NARRATIVE_CONTENT_01");
        String first = mockMvc.perform(multipart("/admin/assessment-materials").file(file(zip))
                        .header("Idempotency-Key", "ZTESTM-KEY-D"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String replay = mockMvc.perform(multipart("/admin/assessment-materials").file(file(zip))
                        .header("Idempotency-Key", "ZTESTM-KEY-D"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(replay).isEqualTo(first);
        int uploads = org.mockito.Mockito.mockingDetails(storage).getInvocations().size();
        mockMvc.perform(multipart("/admin/assessment-materials").file(file(zip))
                        .header("Idempotency-Key", "ZTESTM-KEY-D"))
                .andExpect(status().isCreated());
        assertThat(org.mockito.Mockito.mockingDetails(storage).getInvocations().size()).isEqualTo(uploads);
    }

    @Test
    void sameKeyWithDifferentPackageIsConflict() throws Exception {
        mockMvc.perform(multipart("/admin/assessment-materials").file(file(zipOf("ZTESTM_E", "v1", "NARRATIVE_CONTENT_01")))
                        .header("Idempotency-Key", "ZTESTM-KEY-E"))
                .andExpect(status().isCreated());
        mockMvc.perform(multipart("/admin/assessment-materials").file(file(zipOf("ZTESTM_E2", "v1", "NARRATIVE_CONTENT_01")))
                        .header("Idempotency-Key", "ZTESTM-KEY-E"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void downloadExpandsDeduplicatedDependencies() throws Exception {
        mockMvc.perform(multipart("/admin/assessment-materials").file(file(zipOf("ZTESTM_F", "v1", "NARRATIVE_CONTENT_01")))
                        .header("Idempotency-Key", "ZTESTM-KEY-F"))
                .andExpect(status().isCreated());

        // 排序两图与故事分组两图是同一对文件,依赖去重后应恰好三条:两图 + 音频
        mockMvc.perform(get("/api/assessment-materials/ZTESTM_F/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.official_material_code").value("ZTESTM_F"))
                .andExpect(jsonPath("$.data.dependencies.length()").value(3))
                .andExpect(jsonPath("$.data.dependencies[0].file_code").isNotEmpty())
                .andExpect(jsonPath("$.data.dependencies[2].file_kind").value("AUDIO"))
                .andExpect(jsonPath("$.data.grammars.length()").value(0));
    }

    @Test
    void downloadUnknownOrMalformedIdentifiers() throws Exception {
        mockMvc.perform(get("/api/assessment-materials/ZTESTM_NONE/v9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/assessment-materials/bad!code/v1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.field_path").value("/parameters/code"));
    }

    @Test
    void disableIsIdempotentAndUnknownIdIsNotFound() throws Exception {
        String body = mockMvc.perform(multipart("/admin/assessment-materials").file(file(zipOf("ZTESTM_G", "v1", "NARRATIVE_CONTENT_01")))
                        .header("Idempotency-Key", "ZTESTM-KEY-G"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int id = Integer.parseInt(extractJsonField(body, "id"));

        mockMvc.perform(post("/admin/assessment-materials/{id}/disable", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
        mockMvc.perform(post("/admin/assessment-materials/{id}/disable", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
        mockMvc.perform(post("/admin/assessment-materials/99999999/disable"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listFiltersByCodeAndKeyword() throws Exception {
        mockMvc.perform(multipart("/admin/assessment-materials").file(file(zipOf("ZTESTM_H", "v1", "NARRATIVE_CONTENT_01")))
                        .header("Idempotency-Key", "ZTESTM-KEY-H"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/admin/assessment-materials")
                        .param("official_material_code", "ZTESTM_H")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].official_material_code").value("ZTESTM_H"))
                .andExpect(jsonPath("$.data.items[0].activity_configs_json").doesNotExist());
        mockMvc.perform(get("/admin/assessment-materials").param("keyword", "不存在的关键词"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(get("/admin/assessment-materials").param("page", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedZipAndBadRubricMappingAreRejected() throws Exception {
        byte[] noConfig = rawZip("ztestm-img.png", PNG);
        mockMvc.perform(multipart("/admin/assessment-materials").file(file(noConfig))
                        .header("Idempotency-Key", "ZTESTM-KEY-I1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(multipart("/admin/assessment-materials")
                        .file(file(zipOf("ZTESTM_I", "v1", "NARRATIVE_CONTENT_99")))
                        .header("Idempotency-Key", "ZTESTM-KEY-I2"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_RUBRIC_MAPPING"))
                .andExpect(jsonPath("$.details.rubric_item_code").value("NARRATIVE_CONTENT_99"));
        verify(storage, never()).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void unsupportedMediaFormatIsRejectedBeforeUpload() throws Exception {
        byte[] gif = {'G', 'I', 'F', '8', '9', 'a', 1, 2, 3, 4};
        java.util.Map<String, byte[]> files = new java.util.LinkedHashMap<>();
        files.put("ztestm-img1.png", gif);
        files.put("ztestm-img2.png", PNG);
        files.put("ztestm-story.wav", tinyWav());
        byte[] zip = zipBytes(java.util.Map.of("official_material_code", "ZTESTM_J",
                        "content_version", "v1", "rubric", "NARRATIVE_CONTENT_01"), files);
        mockMvc.perform(multipart("/admin/assessment-materials").file(file(zip))
                        .header("Idempotency-Key", "ZTESTM-KEY-J"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.details.file_name").value("ztestm-img1.png"));
        verify(storage, never()).upload(anyString(), any(), anyLong(), anyString());
    }

    private org.springframework.mock.web.MockMultipartFile file(byte[] zip) {
        return new org.springframework.mock.web.MockMultipartFile("file", "material.zip", "application/zip", zip);
    }

    /** 生成一份最小可发布的材料包:两张图 + 一段音频 + 三类活动。 */
    private byte[] zipOf(String code, String version, String rubricCode) {
        java.util.Map<String, String> meta = new java.util.HashMap<>();
        meta.put("official_material_code", code);
        meta.put("content_version", version);
        meta.put("rubric", rubricCode);
        byte[] zip = zipBytes(meta, java.util.Map.of(
                "ztestm-img1.png", PNG,
                "ztestm-img2.png", PNG,
                "ztestm-story.wav", tinyWav()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(zip);
            mediaKeyPrefixes.add(HexFormat.of().formatHex(digest).substring(0, 24));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
        return zip;
    }

    private byte[] zipBytes(java.util.Map<String, String> meta, java.util.Map<String, byte[]> files) {
        String code = meta.getOrDefault("official_material_code", "ZTESTM_X");
        String version = meta.getOrDefault("content_version", "v1");
        String rubric = meta.getOrDefault("rubric", "NARRATIVE_CONTENT_01");
        String config = """
                {
                  "official_material_code": "%s",
                  "content_version": "%s",
                  "name": "自测评估材料 %s",
                  "schema_version": 2,
                  "story_context": "课间踢球砸碎玻璃。",
                  "activities": [
                    {"activity_id":"act_sort","type":"IMAGE_SORTING","config":{
                      "items":[{"item_id":"s1","file_name":"ztestm-img1.png"},
                               {"item_id":"s2","file_name":"ztestm-img2.png"}],
                      "correct_order":["s1","s2"]}},
                    {"activity_id":"act_q","type":"QUESTION_ANSWERING","config":{
                      "questions":[{"question_id":"q1","text":"玻璃是谁打碎的？","hint":"","grammar":[]}]}},
                    {"activity_id":"act_story","type":"STORY_NARRATION","config":{
                      "audio_file_name":"ztestm-story.wav",
                      "content_items":[{"content_item_id":"c1",
                                        "image_file_names":["ztestm-img1.png","ztestm-img2.png"],
                                        "rubric_item_code":"%s"}]}}
                  ]
                }
                """.formatted(code, version, version, rubric);
        java.util.Map<String, byte[]> entries = new java.util.LinkedHashMap<>(files);
        entries.put("config.json", config.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(buffer)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new java.util.zip.ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        return buffer.toByteArray();
    }

    /** 44 字节头的单声道 8kHz PCM WAV，内容为静音；WAV 字段是小端序。 */
    private static byte[] tinyWav() {
        int dataLen = 8000;   // 0.5 秒静音
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(44 + dataLen);
        try {
            out.write("RIFF".getBytes(StandardCharsets.US_ASCII));
            out.write(leInt(36 + dataLen));
            out.write("WAVE".getBytes(StandardCharsets.US_ASCII));
            out.write("fmt ".getBytes(StandardCharsets.US_ASCII));
            out.write(leInt(16));
            out.write(leShort(1));
            out.write(leShort(1));
            out.write(leInt(8000));
            out.write(leInt(16000));
            out.write(leShort(2));
            out.write(leShort(16));
            out.write("data".getBytes(StandardCharsets.US_ASCII));
            out.write(leInt(dataLen));
            out.write(new byte[dataLen]);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        return out.toByteArray();
    }

    private static byte[] leShort(int value) {
        return new byte[] {(byte) value, (byte) (value >>> 8)};
    }

    private static byte[] leInt(int value) {
        return new byte[] {(byte) value, (byte) (value >>> 8), (byte) (value >>> 16), (byte) (value >>> 24)};
    }

    private byte[] rawZip(String name, byte[] content) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(buffer)) {
            zip.putNextEntry(new java.util.zip.ZipEntry(name));
            zip.write(content);
            zip.closeEntry();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        return buffer.toByteArray();
    }

    private String extractJsonField(String body, String field) {
        int index = body.indexOf("\"" + field + "\":");
        int start = body.indexOf(':', index) + 1;
        int end = body.indexOf(',', start);
        return body.substring(start, end).trim();
    }
}
