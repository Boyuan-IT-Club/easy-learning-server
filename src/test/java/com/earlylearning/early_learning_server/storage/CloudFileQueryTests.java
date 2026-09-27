package com.earlylearning.early_learning_server.storage;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import java.util.List;

import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.web.GlobalExceptionHandler;
import com.earlylearning.early_learning_server.storage.web.AdminFilePageResponse;
import com.earlylearning.early_learning_server.storage.web.AdminFileResponse;
import com.earlylearning.early_learning_server.storage.web.CloudFileResponse;
import com.earlylearning.early_learning_server.storage.web.FileMetadataController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 文件查询：分页、筛选、引用数、以及元数据的状态规则。
 *
 * <p>夹具自建而不依赖 seed 数据——测试不该因为"有没有灌 seed"而变红。
 */
@SpringBootTest
@TestPropertySource(properties = {
        "aliyun.oss.endpoint=https://oss-cn-hangzhou.aliyuncs.com",
        "aliyun.oss.region=cn-hangzhou",
        "aliyun.oss.bucket-name=test-bucket",
        "aliyun.oss.access-key-id=test-id",
        "aliyun.oss.access-key-secret=test-secret"})
class CloudFileQueryTests {

    private static final String PREFIX = "CF_TESTQ_";
    private static final String SHA = "a".repeat(64);

    @Autowired
    private CloudFileQueryService service;

    @Autowired
    private CloudFileMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FileMetadataController metadataController;

    @Autowired
    private GlobalExceptionHandler exceptionHandler;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(metadataController)
                .setControllerAdvice(exceptionHandler)
                .build();
        insert("CF_TESTQ_IMG_READY", CloudFileKind.IMAGE, CloudFileStatus.READY, "test-图片.png", null);
        insert("CF_TESTQ_IMG_UPLOADING", CloudFileKind.IMAGE, CloudFileStatus.UPLOADING, "test-上传中.png", null);
        insert("CF_TESTQ_PDF_DELETED", CloudFileKind.PDF, CloudFileStatus.DELETED, "test-已删除.pdf", null);
    }

    @AfterEach
    void removeFixtures() {
        jdbc.update("DELETE FROM grammar WHERE grammar_code LIKE 'TG_TESTQ_%'");
        jdbc.update("DELETE FROM course WHERE official_course_code LIKE 'TC_TESTQ_%'");
        mapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CloudFile>()
                .likeRight("file_code", PREFIX));
    }

    @Test
    void listReturnsRequestedPageWithTotal() {
        AdminFilePageResponse firstPage = service.list(1, 2, null, null, null, PREFIX);

        assertThat(firstPage.total()).isEqualTo(3);
        assertThat(firstPage.page()).isEqualTo(1);
        assertThat(firstPage.pageSize()).isEqualTo(2);
        assertThat(firstPage.items()).hasSize(2);
        // 契约：按 id 降序
        assertThat(firstPage.items().get(0).id()).isGreaterThan(firstPage.items().get(1).id());

        AdminFilePageResponse secondPage = service.list(2, 2, null, null, null, PREFIX);
        assertThat(secondPage.items()).hasSize(1);
        assertThat(secondPage.items().get(0).id()).isLessThan(firstPage.items().get(1).id());
    }

    @Test
    void keywordTreatsWildcardsAsLiteralCharacters() {
        insert("CF_TESTQ_PERCENT", CloudFileKind.IMAGE, CloudFileStatus.READY, "test-完成度100%.png", null);

        long withoutKeyword = service.list(1, 20, null, null, null, null).total();
        long literalPercent = service.list(1, 20, null, null, null, "%").total();

        // 关键断言：'%' 被当成通配符时它会命中**全部**记录；转义后只命中真正含字面 % 的那一条。
        assertThat(literalPercent).isLessThan(withoutKeyword);
        assertThat(service.list(1, 20, null, null, null, "100%").total()).isEqualTo(1);
        assertThat(service.list(1, 20, null, null, null, "不存在的关键词").total()).isZero();
    }

    @Test
    void filtersNarrowResultsByExactCodeKindAndStatus() {
        assertThat(service.list(1, 20, "CF_TESTQ_PDF_DELETED", null, null, null).total()).isEqualTo(1);

        // 与 keyword 组合，把结果限定在本类夹具内——否则会被库里的 seed 数据带偏。
        assertThat(service.list(1, 20, null, CloudFileKind.PDF, null, PREFIX).total()).isEqualTo(1);
        assertThat(service.list(1, 20, null, CloudFileKind.AUDIO, null, PREFIX).total()).isZero();
        assertThat(service.list(1, 20, null, null, CloudFileStatus.UPLOADING, PREFIX).total()).isEqualTo(1);
        assertThat(service.list(1, 20, null, null, CloudFileStatus.READY, PREFIX).total()).isEqualTo(1);
        assertThat(service.list(1, 20, null, null, CloudFileStatus.DELETED, PREFIX).total()).isEqualTo(1);
    }

    @Test
    void referenceCountCoversBothForeignKeyAndJsonReferences() {
        Integer fileId = idOf("CF_TESTQ_IMG_READY");
        // 外键引用
        jdbc.update("INSERT INTO grammar (grammar_code, name, version, icon_file_id, status) "
                + "VALUES ('TG_TESTQ_1', 'test-语法', 1, ?, 'ACTIVE')", fileId);
        // JSON 内引用：结构由内容模块定义，本模块只用 JSON_SEARCH 找这个编号
        jdbc.update("INSERT INTO course (official_course_code, content_version, name, activity_configs_json, status) "
                + "VALUES ('TC_TESTQ_1', 'v1', 'test-课程', "
                + "JSON_OBJECT('activities', JSON_ARRAY(JSON_OBJECT('image_file_code', 'CF_TESTQ_IMG_READY'))), 'ACTIVE')");

        AdminFilePageResponse page = service.list(1, 20, "CF_TESTQ_IMG_READY", null, null, null);

        assertThat(page.items()).singleElement()
                .extracting(AdminFileResponse::referenceCount).isEqualTo(2);
    }

    @Test
    void referenceCountIgnoresNothingAboutHistory() {
        Integer fileId = idOf("CF_TESTQ_IMG_READY");
        // 契约："统计所有有效及历史内容"——禁用版本里的引用同样算数
        jdbc.update("INSERT INTO grammar (grammar_code, name, version, icon_file_id, status) "
                + "VALUES ('TG_TESTQ_2', 'test-停用语法', 1, ?, 'DISABLED')", fileId);

        AdminFilePageResponse page = service.list(1, 20, "CF_TESTQ_IMG_READY", null, null, null);
        assertThat(page.items().get(0).referenceCount()).isEqualTo(1);
    }

    @Test
    void metadataReturnsReadyFileAndNeverExposesObjectKey() {
        CloudFileResponse response = service.metadata("CF_TESTQ_IMG_READY");

        assertThat(response.fileCode()).isEqualTo("CF_TESTQ_IMG_READY");
        assertThat(response.status()).isEqualTo(CloudFileStatus.READY);
        // DTO 结构上就没有 object_key 字段——这条由类型保证，不靠运行时过滤
        assertThat(CloudFileResponse.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("objectKey", "object_key");
    }

    @Test
    void metadataRejectsNotReadyWithConflictAndDeletedWithGone() {
        assertThatThrownBy(() -> service.metadata("CF_TESTQ_IMG_UPLOADING"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_READY);

        // DELETED 同时"不是 READY"，但契约给它的是 410——顺序判错就会退化成 409
        assertThatThrownBy(() -> service.metadata("CF_TESTQ_PDF_DELETED"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FILE_DELETED);
    }

    @Test
    void metadataReturnsNotFoundForUnknownCode() {
        assertThatThrownBy(() -> service.metadata("CF_TESTQ_NOT_EXIST"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void invalidPagingParametersAreRejected() {
        assertThatThrownBy(() -> service.list(0, 20, null, null, null, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.list(1, 0, null, null, null, null))
                .isInstanceOf(BusinessException.class);
        // 契约：page_size 最大 100
        assertThatThrownBy(() -> service.list(1, 101, null, null, null, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void metadataEndpointMapsDeletedFileTo410OverHttp() throws Exception {
        // 补上"异常类型 → HTTP 状态"这一环：服务层断言的是异常，这里断言真实响应码。
        mockMvc.perform(get("/api/files/{file_code}", "CF_TESTQ_PDF_DELETED"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("FILE_DELETED"))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(get("/api/files/{file_code}", "CF_TESTQ_NOT_EXIST"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/api/files/{file_code}", "CF_TESTQ_IMG_READY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"));
    }

    private void insert(String fileCode, CloudFileKind kind, CloudFileStatus status, String name, Integer durationMs) {
        CloudFile file = new CloudFile();
        file.setFileCode(fileCode);
        file.setObjectKey("test-query/" + fileCode);
        file.setFileKind(kind);
        file.setFileName(name);
        file.setMimeType(kind == CloudFileKind.PDF ? "application/pdf" : "image/png");
        file.setSizeBytes(1024L);
        file.setDurationMs(durationMs);
        file.setStatus(status);
        file.setSha256(status == CloudFileStatus.READY ? SHA : null);
        mapper.insert(file);
    }

    private Integer idOf(String fileCode) {
        List<CloudFile> found = mapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CloudFile>()
                        .eq("file_code", fileCode));
        return found.get(0).getId();
    }
}
