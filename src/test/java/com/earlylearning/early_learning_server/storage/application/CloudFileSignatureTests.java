package com.earlylearning.early_learning_server.storage.application;
import com.earlylearning.early_learning_server.storage.interfaces.dto.DownloadSignatureBatchResponse;
import com.earlylearning.early_learning_server.storage.infrastructure.CloudFileMapper;
import com.earlylearning.early_learning_server.storage.domain.ObjectStorageService;
import com.earlylearning.early_learning_server.storage.domain.CloudFileStatus;
import com.earlylearning.early_learning_server.storage.domain.CloudFileKind;
import com.earlylearning.early_learning_server.storage.domain.CloudFile;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.web.GlobalExceptionHandler;
import com.earlylearning.early_learning_server.storage.interfaces.controller.FileMetadataController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 批量签发下载地址：整批语义、状态限制、命名空间与失败定位。
 */
@SpringBootTest
@TestPropertySource(properties = {
        "aliyun.oss.endpoint=https://oss-cn-hangzhou.aliyuncs.com",
        "aliyun.oss.region=cn-hangzhou",
        "aliyun.oss.bucket-name=test-bucket",
        "aliyun.oss.access-key-id=test-id",
        "aliyun.oss.access-key-secret=test-secret"})
class CloudFileSignatureTests {

    private static final String SHA = "c".repeat(64);

    @Autowired
    private CloudFileSignatureService service;

    @Autowired
    private CloudFileMapper mapper;

    @Autowired
    private FileMetadataController controller;

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
        when(storage.generateDownloadUrl(anyString())).thenAnswer(invocation -> new ObjectStorageService.DownloadUrl(
                URI.create("https://test-bucket.oss-cn-hangzhou.aliyuncs.com/" + invocation.getArgument(0)
                        + "?x-oss-signature=EXAMPLE_NOT_VALID"),
                Instant.now().plusSeconds(900)));

        insert("CF_TESTS_READY_A", CloudFileStatus.READY);
        insert("CF_TESTS_READY_B", CloudFileStatus.READY);
        insert("CF_TESTS_UPLOADING", CloudFileStatus.UPLOADING);
        insert("CF_TESTS_DELETED", CloudFileStatus.DELETED);
    }

    @AfterEach
    void removeFixtures() {
        mapper.delete(new QueryWrapper<CloudFile>().likeRight("file_code", "CF_TESTS_"));
    }

    @Test
    void signsEveryItemInRequestOrder() {
        var response = com.earlylearning.early_learning_server.storage.interfaces.dto.DownloadSignatureBatchResponse
                .from(service.sign(List.of("CF_TESTS_READY_B", "CF_TESTS_READY_A")));

        assertThat(response.items()).extracting("fileCode")
                .containsExactly("CF_TESTS_READY_B", "CF_TESTS_READY_A");
        assertThat(response.items()).allSatisfy(item -> {
            assertThat(item.downloadUrl()).startsWith("https://");
            assertThat(item.sha256()).isEqualTo(SHA);
            assertThat(item.expiresAt()).isNotBlank();
            assertThat(item.sizeBytes()).isEqualTo(1024L);
        });
        verify(storage, times(2)).generateDownloadUrl(anyString());
    }

    @Test
    void anyFailureFailsTheWholeBatchAndSignsNothing() {
        assertThatThrownBy(() -> service.sign(List.of("CF_TESTS_READY_A", "CF_TESTS_UPLOADING")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_READY);

        // 整批语义：校验阶段不签发任何地址，合格的第一个也不能被签
        verify(storage, times(0)).generateDownloadUrl(anyString());
    }

    @Test
    void failureAnnotationPointsAtTheFailingItem() {
        assertThatThrownBy(() -> service.sign(List.of("CF_TESTS_READY_A", "CF_TESTS_DELETED")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    var details = ((BusinessException) ex).getDetails();
                    assertThat(details.fieldPath()).isEqualTo("/file_codes/1");
                    assertThat(details.fileCode()).isEqualTo("CF_TESTS_DELETED");
                });
    }

    @Test
    void deletedIs410AndUnknownIs404() {
        assertThatThrownBy(() -> service.sign(List.of("CF_TESTS_DELETED")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.sign(List.of("CF_TESTS_NOT_EXIST")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getDetails().fieldPath())
                        .isEqualTo("/file_codes/0"));
    }

    @Test
    void localNamespaceAndDuplicatesAreRejectedAs400() {
        // LF_ 是平板本地自产文件的命名空间，云端不存在；请求体 schema 本身就排除了它。
        assertThatThrownBy(() -> service.sign(List.of("LF_20260927_ABCDEF")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getDetails().fieldPath())
                        .isEqualTo("/file_codes/0"));

        assertThatThrownBy(() -> service.sign(List.of("CF_TESTS_READY_A", "CF_TESTS_READY_A")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getDetails().fieldPath())
                        .isEqualTo("/file_codes/1"));
    }

    @Test
    void emptyOrOversizedBatchIsRejected() {
        assertThatThrownBy(() -> service.sign(List.of()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getDetails().fieldPath())
                        .isEqualTo("/file_codes"));

        assertThatThrownBy(() -> service.sign(java.util.stream.IntStream.range(0, 101)
                .mapToObj(i -> "CF_TESTS_BULK_" + i).toList()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void endpointReturnsSignedItemsOverHttp() throws Exception {
        mockMvc.perform(post("/api/files/signatures")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"file_codes\":[\"CF_TESTS_READY_A\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.items[0].file_code").value("CF_TESTS_READY_A"))
                .andExpect(jsonPath("$.data.items[0].download_url").value(org.hamcrest.Matchers.startsWith("https://")))
                .andExpect(jsonPath("$.data.items[0].sha256").value(SHA));

        mockMvc.perform(post("/api/files/signatures")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"file_codes\":[\"CF_TESTS_DELETED\"]}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("FILE_DELETED"))
                .andExpect(jsonPath("$.details.field_path").value("/file_codes/0"));
    }

    private void insert(String fileCode, CloudFileStatus status) {
        CloudFile file = new CloudFile();
        file.setFileCode(fileCode);
        file.setObjectKey("test-sign/" + fileCode);
        file.setFileKind(CloudFileKind.IMAGE);
        file.setFileName("test-" + fileCode + ".png");
        file.setMimeType("image/png");
        file.setSizeBytes(1024L);
        file.setStatus(status);
        file.setSha256(status == CloudFileStatus.READY ? SHA : null);
        mapper.insert(file);
    }
}
