package com.earlylearning.early_learning_server.storage.controller;

import com.earlylearning.early_learning_server.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.storage.entity.CloudFile;
import com.earlylearning.early_learning_server.storage.mapper.CloudFileMapper;
import com.earlylearning.early_learning_server.storage.model.ObjectStorageService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.earlylearning.early_learning_server.common.web.GlobalExceptionHandler;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 上传接口的端到端切片。
 *
 * <p>{@code addFilters = false}：鉴权不属于本项目（见 M3 上下文包第 7 节），本地没有登录接口可以拿 Token，
 * 因此绕过安全过滤链直接测本模块。测试范围之外的东西一律不假装覆盖。
 *
 * <p>对象存储用假实现：这些用例要验的是本模块的编排与补偿，不是 OSS 本身
 * （OSS 有自己的门控真实测试）。
 */
@SpringBootTest
@TestPropertySource(properties = {
        // 把上限压到 100 字节，这样"超限"用例不必真造 500MB 的请求。
        "storage.upload.max-size-bytes=100",
        "aliyun.oss.endpoint=https://oss-cn-hangzhou.aliyuncs.com",
        "aliyun.oss.region=cn-hangzhou",
        "aliyun.oss.bucket-name=test-bucket",
        "aliyun.oss.access-key-id=test-id",
        "aliyun.oss.access-key-secret=test-secret"})
class AdminFileUploadTests {

    private static final String NAME_PREFIX = "test-";

    private static final byte[] PNG = concat(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A},
            "0123456789abcdef".getBytes(StandardCharsets.US_ASCII));
    private static final byte[] PDF = "%PDF-1.7\nbody".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private AdminFileController controller;

    @Autowired
    private GlobalExceptionHandler exceptionHandler;

    @Autowired
    private CloudFileMapper mapper;

    /**
     * 独立 MockMvc：直接挂真实控制器与真实 Advice，不经过安全过滤链。
     * 控制器从容器里取（而不是 new），这样类上的 {@code @Validated} 代理才在——否则请求头校验不会触发。
     */
    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(exceptionHandler)
                .build();
    }

    private MockMvc mockMvc;

    @MockitoBean
    private ObjectStorageService storage;

    private final AtomicInteger uploadCalls = new AtomicInteger();

    @AfterEach
    void removeTestRows() {
        mapper.delete(new QueryWrapper<CloudFile>().likeRight("file_name", NAME_PREFIX));
    }

    @Test
    void returns201WithReadyAndNeverExposesObjectKey() throws Exception {
        uploadCalls.set(0);
        mockMvc.perform(uploadRequest(UUID.randomUUID().toString(), NAME_PREFIX + "photo.png", PNG, "IMAGE", "image/png"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.file_kind").value("IMAGE"))
                .andExpect(jsonPath("$.data.mime_type").value("image/png"))
                .andExpect(jsonPath("$.data.size_bytes").value(PNG.length))
                .andExpect(jsonPath("$.data.file_code").value(org.hamcrest.Matchers.startsWith("CF_")))
                .andExpect(jsonPath("$.data.sha256").value(org.hamcrest.Matchers.matchesPattern("^[a-f0-9]{64}$")))
                .andExpect(jsonPath("$.data.created_at").exists())
                // object_key 是内部物理位置，不返回。
                .andExpect(content().string(not(containsString("object_key"))))
                .andExpect(content().string(not(containsString("test-bucket"))));
        verify(storage, times(1)).upload(anyString(), any(), anyLong(), eq("image/png"));
    }

    @Test
    void nonAudioExitHasDurationKeyPresentAndNull() throws Exception {
        // 非音频的 duration_ms 显式为 null——键必须出现，否则客户端解析失败。
        mockMvc.perform(uploadRequest(UUID.randomUUID().toString(), NAME_PREFIX + "photo.png", PNG, "IMAGE", "image/png"))
                .andExpect(status().isCreated())
                .andExpect(content().string(containsString("\"duration_ms\":null")));
    }

    @Test
    void sameKeyAndInputReplaysIdenticalBodyAndUploadsOnce() throws Exception {
        String key = UUID.randomUUID().toString();
        String first = mockMvc.perform(uploadRequest(key, NAME_PREFIX + "photo.png", PNG, "IMAGE", "image/png"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(uploadRequest(key, NAME_PREFIX + "photo.png", PNG, "IMAGE", "image/png"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertEquals(first, second, "重放必须与首次逐字节相同");
        verify(storage, times(1)).upload(anyString(), any(), anyLong(), anyString());
        org.junit.jupiter.api.Assertions.assertEquals(1, mapper.selectCount(
                new QueryWrapper<CloudFile>().likeRight("file_name", NAME_PREFIX)));
    }

    @Test
    void overrideNameAndFilenameProduceTheSameFingerprint() throws Exception {
        String key = UUID.randomUUID().toString();
        String viaFilename = mockMvc.perform(uploadRequest(key, NAME_PREFIX + "photo.png", PNG, "IMAGE", "image/png"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        // 等价传法：显式覆盖值等于 multipart 的 filename，它不产生第二个文件。
        String viaOverride = mockMvc.perform(uploadRequest(key, null, PNG, "IMAGE", "image/png", NAME_PREFIX + "photo.png"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertEquals(viaFilename, viaOverride);
        verify(storage, times(1)).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void sameKeyDifferentContentReturns409AndDoesNotUpload() throws Exception {
        String key = UUID.randomUUID().toString();
        mockMvc.perform(uploadRequest(key, NAME_PREFIX + "photo.png", PNG, "IMAGE", "image/png"))
                .andExpect(status().isCreated());

        mockMvc.perform(uploadRequest(key, NAME_PREFIX + "other.png", PDF, "PDF", "application/pdf"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

        verify(storage, times(1)).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void declaredKindThatContradictsContentIs415UnsupportedMediaType() throws Exception {
        mockMvc.perform(uploadRequest(UUID.randomUUID().toString(), NAME_PREFIX + "fake.png", PDF, "IMAGE", "image/png"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        verify(storage, never()).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void declaredMimeThatContradictsContentIs415ContentTypeMismatch() throws Exception {
        // 内容确实是 PDF、kind 也报了 PDF，但 multipart 声明成了 image/png —— 这是"谎报"，另一个码。
        mockMvc.perform(uploadRequest(UUID.randomUUID().toString(), NAME_PREFIX + "doc.pdf", PDF, "PDF", "image/png"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("CONTENT_TYPE_MISMATCH"));
        verify(storage, never()).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void exceedingBusinessLimitIs413WithLimitDetails() throws Exception {
        byte[] tooBig = concat(PNG, new byte[200]);
        mockMvc.perform(uploadRequest(UUID.randomUUID().toString(), NAME_PREFIX + "big.png", tooBig, "IMAGE", "image/png"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"))
                .andExpect(jsonPath("$.details.limit.name").value("size_bytes"))
                .andExpect(jsonPath("$.details.limit.maximum").value(100));
        verify(storage, never()).upload(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void storageFailureIs503AndLeavesNoRecord() throws Exception {
        doThrow(new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "Object upload failed", (Throwable) null))
                .when(storage).upload(anyString(), any(), anyLong(), anyString());

        mockMvc.perform(uploadRequest(UUID.randomUUID().toString(), NAME_PREFIX + "photo.png", PNG, "IMAGE", "image/png"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));

        org.junit.jupiter.api.Assertions.assertEquals(0, mapper.selectCount(
                new QueryWrapper<CloudFile>().likeRight("file_name", NAME_PREFIX)));
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder uploadRequest(
            String idempotencyKey, String fileName, byte[] content, String kind, String declaredMime) {
        return multipart("/admin/files")
                .file(new MockMultipartFile("file", fileName, declaredMime, content))
                .param("file_kind", kind)
                .header("Idempotency-Key", idempotencyKey);
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder uploadRequest(
            String idempotencyKey, String fileName, byte[] content, String kind, String declaredMime, String overrideName) {
        return uploadRequest(idempotencyKey, fileName, content, kind, declaredMime)
                .param("file_name", overrideName);
    }

    private static byte[] concat(byte[] head, byte[] tail) {
        byte[] result = java.util.Arrays.copyOf(head, head.length + tail.length);
        System.arraycopy(tail, 0, result, head.length, tail.length);
        return result;
    }
}
