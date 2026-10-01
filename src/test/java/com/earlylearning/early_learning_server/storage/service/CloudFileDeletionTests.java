package com.earlylearning.early_learning_server.storage.service;
import com.earlylearning.early_learning_server.storage.mapper.CloudFileMapper;
import com.earlylearning.early_learning_server.storage.model.ObjectStorageService;
import com.earlylearning.early_learning_server.storage.entity.CloudFileStatus;
import com.earlylearning.early_learning_server.storage.entity.CloudFileKind;
import com.earlylearning.early_learning_server.storage.entity.CloudFile;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import java.util.List;

import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.web.GlobalExceptionHandler;
import com.earlylearning.early_learning_server.storage.controller.AdminFileController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 标记删除：引用保护、幂等、状态约束，以及"只标记不物理删除"。
 */
@SpringBootTest
@TestPropertySource(properties = {
        "aliyun.oss.endpoint=https://oss-cn-hangzhou.aliyuncs.com",
        "aliyun.oss.region=cn-hangzhou",
        "aliyun.oss.bucket-name=test-bucket",
        "aliyun.oss.access-key-id=test-id",
        "aliyun.oss.access-key-secret=test-secret"})
class CloudFileDeletionTests {

    private static final String SHA = "b".repeat(64);

    @Autowired
    private CloudFileDeletionService service;

    @Autowired
    private CloudFileMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AdminFileController controller;

    @Autowired
    private GlobalExceptionHandler exceptionHandler;

    @MockitoBean
    private ObjectStorageService storage;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(exceptionHandler)
                .build();
        insert("CF_TESTD_FREE", CloudFileStatus.READY);
        insert("CF_TESTD_BY_FK", CloudFileStatus.READY);
        insert("CF_TESTD_BY_JSON", CloudFileStatus.READY);
        insert("CF_TESTD_UPLOADING", CloudFileStatus.UPLOADING);
        insert("CF_TESTD_ALREADY_DELETED", CloudFileStatus.DELETED);
    }

    @AfterEach
    void removeFixtures() {
        jdbc.update("DELETE FROM grammar WHERE grammar_code LIKE 'TG_TESTD_%'");
        jdbc.update("DELETE FROM course WHERE official_course_code LIKE 'TC_TESTD_%'");
        mapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CloudFile>()
                .likeRight("file_code", "CF_TESTD_"));
    }

    @Test
    void unreferencedReadyFileIsMarkedDeleted() {
        var response = service.markDeleted("CF_TESTD_FREE");

        assertThat(response.getStatus()).isEqualTo(CloudFileStatus.DELETED);
        assertThat(statusOf("CF_TESTD_FREE")).isEqualTo("DELETED");
    }

    @Test
    void referencedFileIsRejectedAndItsStatusIsUntouched() {
        jdbc.update("INSERT INTO grammar (grammar_code, name, version, icon_file_id, status) "
                + "VALUES ('TG_TESTD_1', 'test-语法', 1, ?, 'ACTIVE')", idOf("CF_TESTD_BY_FK"));

        assertThatThrownBy(() -> service.markDeleted("CF_TESTD_BY_FK"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_IN_USE);

        // 契约："命中返回 409，保持原状态" —— 状态必须一个字节都没动
        assertThat(statusOf("CF_TESTD_BY_FK")).isEqualTo("READY");
    }

    @Test
    void jsonReferenceAlsoBlocksDeletion() {
        jdbc.update("INSERT INTO course (official_course_code, content_version, name, activity_configs_json, status) "
                + "VALUES ('TC_TESTD_1', 'v1', 'test-课程', "
                + "JSON_OBJECT('activities', JSON_ARRAY(JSON_OBJECT('image_file_code', 'CF_TESTD_BY_JSON'))), 'ACTIVE')");

        assertThatThrownBy(() -> service.markDeleted("CF_TESTD_BY_JSON"))
                .isInstanceOf(BusinessException.class);
        assertThat(statusOf("CF_TESTD_BY_JSON")).isEqualTo("READY");
    }

    @Test
    void deletingTwiceIsIdempotent() {
        var first = service.markDeleted("CF_TESTD_ALREADY_DELETED");
        var second = service.markDeleted("CF_TESTD_ALREADY_DELETED");

        assertThat(first.getStatus()).isEqualTo(CloudFileStatus.DELETED);
        assertThat(second.getStatus()).isEqualTo(CloudFileStatus.DELETED);
        assertThat(statusOf("CF_TESTD_ALREADY_DELETED")).isEqualTo("DELETED");
    }

    @Test
    void nonReadyFileIsNotDeletable() {
        assertThatThrownBy(() -> service.markDeleted("CF_TESTD_UPLOADING"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_READY);
        assertThat(statusOf("CF_TESTD_UPLOADING")).isEqualTo("UPLOADING");
    }

    @Test
    void unknownCodeIsNotFound() {
        assertThatThrownBy(() -> service.markDeleted("CF_TESTD_NOPE"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void markingDeletedNeverTouchesObjectStorage() {
        service.markDeleted("CF_TESTD_FREE");

        // 契约："此 API 不物理删除对象"——本模块结构上不依赖存储服务，这里从行为上再确认一次
        verify(storage, never()).delete(anyString());
    }

    @Test
    void endpointReturnsExpectedStatusCodes() throws Exception {
        mockMvc.perform(post("/admin/files/{file_code}/delete", "CF_TESTD_FREE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.status").value("DELETED"))
                .andExpect(jsonPath("$.data.file_code").value("CF_TESTD_FREE"));

        mockMvc.perform(post("/admin/files/{file_code}/delete", "CF_TESTD_NOPE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(post("/admin/files/{file_code}/delete", "CF_TESTD_UPLOADING"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_READY"));
    }

    private void insert(String fileCode, CloudFileStatus status) {
        CloudFile file = new CloudFile();
        file.setFileCode(fileCode);
        file.setObjectKey("test-delete/" + fileCode);
        file.setFileKind(CloudFileKind.IMAGE);
        file.setFileName("test-" + fileCode + ".png");
        file.setMimeType("image/png");
        file.setSizeBytes(1024L);
        file.setStatus(status);
        file.setSha256(status == CloudFileStatus.DELETED ? null : SHA);
        mapper.insert(file);
    }

    private Integer idOf(String fileCode) {
        List<CloudFile> found = mapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CloudFile>()
                        .eq("file_code", fileCode));
        return found.get(0).getId();
    }

    private String statusOf(String fileCode) {
        return jdbc.queryForObject("SELECT status FROM storage_cloud_file WHERE file_code = ?",
                String.class, fileCode);
    }
}
