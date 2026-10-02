package com.earlylearning.early_learning_server.storage.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.earlylearning.early_learning_server.common.enums.CloudFileKind;
import com.earlylearning.early_learning_server.common.enums.CloudFileStatus;
import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.idempotency.IdempotencyScope;
import com.earlylearning.early_learning_server.common.idempotency.IdempotencyService;
import com.earlylearning.early_learning_server.common.idempotency.InputFingerprint;
import com.earlylearning.early_learning_server.common.idempotency.StoredResponse;
import com.earlylearning.early_learning_server.common.media.AudioDurationReader;
import com.earlylearning.early_learning_server.common.media.MediaTypeDetector;
import com.earlylearning.early_learning_server.entity.CloudFile;
import com.earlylearning.early_learning_server.storage.mapper.CloudFileMapper;
import com.earlylearning.early_learning_server.storage.model.CloudFileCodeGenerator;
import com.earlylearning.early_learning_server.storage.model.IncomingFile;
import com.earlylearning.early_learning_server.storage.model.ObjectKeyGenerator;
import com.earlylearning.early_learning_server.storage.model.ObjectStorageService;
import com.earlylearning.early_learning_server.storage.model.UploadLimits;
import com.earlylearning.early_learning_server.storage.service.CloudFileService;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/** {@link CloudFileService} 的实现。 */
@Service
@Slf4j
public class CloudFileServiceImpl implements CloudFileService {

    private static final IdempotencyScope SCOPE = IdempotencyScope.ADMIN_FILE_UPLOAD;

    /** 允许的 MIME → 它必须归属的文件类型。契约只收官方 AUDIO/IMAGE/PDF。 */
    private static final Map<String, CloudFileKind> KIND_BY_MIME = Map.ofEntries(
            Map.entry("application/pdf", CloudFileKind.PDF),
            Map.entry("image/png", CloudFileKind.IMAGE),
            Map.entry("image/jpeg", CloudFileKind.IMAGE),
            Map.entry("image/gif", CloudFileKind.IMAGE),
            Map.entry("image/webp", CloudFileKind.IMAGE),
            Map.entry("audio/mpeg", CloudFileKind.AUDIO),
            Map.entry("audio/mp4", CloudFileKind.AUDIO),
            Map.entry("audio/wav", CloudFileKind.AUDIO),
            Map.entry("audio/ogg", CloudFileKind.AUDIO),
            Map.entry("audio/flac", CloudFileKind.AUDIO),
            Map.entry("audio/webm", CloudFileKind.AUDIO));

    /**
     * 客户端没给出有意义的声明时，不判定为「声明与实际不符」。
     *
     * <p>浏览器对不认识的扩展名会统一发 {@code application/octet-stream}，那不是「说错了」而是「没声明」。
     * 契约的 415 针对的是谎报（说 image/png 实际是 PDF），不是「没报」。
     */
    private static final String NO_MEANINGFUL_DECLARATION = "application/octet-stream";

    private static final int MAX_DISPLAY_NAME_LENGTH = 255;

    private final CloudFileMapper cloudFileMapper;
    private final ObjectStorageService objectStorageService;
    private final IdempotencyService idempotencyService;
    private final MediaTypeDetector mediaTypeDetector;
    private final AudioDurationReader audioDurationReader;
    private final CloudFileCodeGenerator cloudFileCodeGenerator;
    private final ObjectKeyGenerator objectKeyGenerator;
    private final UploadLimits uploadLimits;
    private final TransactionTemplate transaction;
    private final ObjectMapper objectMapper;

    public CloudFileServiceImpl(CloudFileMapper cloudFileMapper,
                                ObjectStorageService objectStorageService,
                                IdempotencyService idempotencyService,
                                MediaTypeDetector mediaTypeDetector,
                                AudioDurationReader audioDurationReader,
                                CloudFileCodeGenerator cloudFileCodeGenerator,
                                ObjectKeyGenerator objectKeyGenerator,
                                UploadLimits uploadLimits,
                                org.springframework.transaction.PlatformTransactionManager transactionManager,
                                ObjectMapper objectMapper) {
        this.cloudFileMapper = cloudFileMapper;
        this.objectStorageService = objectStorageService;
        this.idempotencyService = idempotencyService;
        this.mediaTypeDetector = mediaTypeDetector;
        this.audioDurationReader = audioDurationReader;
        this.cloudFileCodeGenerator = cloudFileCodeGenerator;
        this.objectKeyGenerator = objectKeyGenerator;
        this.uploadLimits = uploadLimits;
        this.transaction = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapper;
    }

    @Override
    public CloudFile upload(String idempotencyKey,
                            IncomingFile file,
                            CloudFileKind declaredKind,
                            String overrideFileName) {
        String displayName = normalizeDisplayName(overrideFileName, file.originalFilename());
        long size = file.size();
        if (size <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (size > uploadLimits.maxSizeBytes()) {
            // 只给 details、不自定义 message：错误码自带的说明（"文件/音频/图片超出部署允许上限"）就是契约要的措辞。
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE, null,
                    ApiErrorDetails.ofLimit(ApiErrorDetails.LimitName.SIZE_BYTES, uploadLimits.maxSizeBytes()));
        }

        Path staged = null;
        try {
            staged = Files.createTempFile("cloud-file-", ".part");
            file.stager().stageTo(staged);

            String detectedMime = mediaTypeDetector.detect(readHead(staged));
            if (detectedMime == null || KIND_BY_MIME.get(detectedMime) != declaredKind) {
                log.warn("上传文件的实际类型与声明不符 declaredKind={} detectedMime={}", declaredKind, detectedMime);
                throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
            }
            requireDeclarationAgrees(file.declaredContentType(), detectedMime);

            String sha256 = sha256Hex(staged);
            String fingerprint = InputFingerprint.of(sha256, displayName, declaredKind.name());

            Optional<StoredResponse> replayed = idempotencyService.peek(SCOPE, idempotencyKey, fingerprint);
            if (replayed.isPresent()) {
                log.info("文件上传请求重放，返回首次结果 kind={} sizeBytes={}", declaredKind, size);
                return fromSnapshot(replayed.get().body());
            }

            String objectKey = objectKeyGenerator.next(declaredKind);
            Integer durationMs = declaredKind == CloudFileKind.AUDIO
                    ? toInteger(audioDurationReader.readMillis(staged.toFile(), detectedMime))
                    : null;

            try (InputStream content = Files.newInputStream(staged)) {
                objectStorageService.upload(objectKey, content, size, detectedMime);
            }

            AtomicBoolean rowWritten = new AtomicBoolean(false);
            try {
                CloudFile stored = transaction.execute(status -> {
                    Optional<StoredResponse> claimed = idempotencyService.claim(SCOPE, idempotencyKey, fingerprint);
                    if (claimed.isPresent()) {
                        return fromSnapshot(claimed.get().body());
                    }
                    CloudFile entity = newFile(cloudFileCodeGenerator.next(), objectKey, declaredKind,
                            displayName, detectedMime, size, durationMs, sha256);
                    cloudFileMapper.insert(entity);
                    rowWritten.set(true);
                    CloudFile saved = cloudFileMapper.selectById(entity.getId());
                    idempotencyService.record(SCOPE, idempotencyKey, 201, saved);
                    return saved;
                });
                if (rowWritten.get()) {
                    log.info("上传官方文件 fileCode={} kind={} mime={} sizeBytes={}",
                            stored.getFileCode(), declaredKind, detectedMime, size);
                } else {
                    // 并发的同键请求先完成了，这次上传的对象用不上
                    log.info("同键上传已由并发请求完成，返回其结果并清理本次对象 kind={}", declaredKind);
                    deleteUploadedObject(objectKey, null);
                }
                return stored;
            } catch (RuntimeException failure) {
                deleteUploadedObject(objectKey, failure);
                throw failure;
            }
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "上传暂存文件读写失败", ex);
        } finally {
            deleteStagedQuietly(staged);
        }
    }

    /** 把幂等快照还原成文件实体：重放路径与首次路径产出同一个对象。 */
    private CloudFile fromSnapshot(String body) {
        return objectMapper.readValue(body, CloudFile.class);
    }

    private CloudFile newFile(String fileCode, String objectKey, CloudFileKind kind, String displayName,
                              String mimeType, long size, Integer durationMs, String sha256) {
        CloudFile entity = new CloudFile();
        entity.setFileCode(fileCode);
        entity.setObjectKey(objectKey);
        entity.setFileKind(kind);
        entity.setFileName(displayName);
        entity.setMimeType(mimeType);
        entity.setSizeBytes(size);
        entity.setDurationMs(durationMs);
        entity.setStatus(CloudFileStatus.READY);
        entity.setSha256(sha256);
        return entity;
    }

    /**
     * 规范化显示名：覆盖值优先，否则用 multipart 的 filename。
     *
     * <p>幂等比较使用最终规范化显示名，所以这个结果既要落库、也要参与指纹——
     * 两种等价传法（覆盖值 / filename）必须产生同一个指纹。
     */
    private String normalizeDisplayName(String override, String originalFilename) {
        String candidate = override != null && !override.isBlank() ? override : originalFilename;
        if (candidate == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        String cleaned = candidate.replace('\\', '/');
        int lastSlash = cleaned.lastIndexOf('/');
        if (lastSlash >= 0) {
            cleaned = cleaned.substring(lastSlash + 1);
        }
        cleaned = cleaned.replaceAll("\\p{Cntrl}", "").trim();
        if (cleaned.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (cleaned.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return cleaned;
    }

    private void requireDeclarationAgrees(String declaredMime, String detectedMime) {
        if (declaredMime == null || declaredMime.isBlank()
                || NO_MEANINGFUL_DECLARATION.equalsIgnoreCase(declaredMime)) {
            return;
        }
        String normalized = declaredMime.split(";")[0].trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals(detectedMime)) {
            throw new BusinessException(ErrorCode.CONTENT_TYPE_MISMATCH);
        }
    }

    private byte[] readHead(Path file) throws IOException {
        byte[] head = new byte[MediaTypeDetector.HEAD_BYTES];
        try (InputStream in = Files.newInputStream(file)) {
            int read = in.readNBytes(head, 0, head.length);
            return read == head.length ? head : java.util.Arrays.copyOf(head, Math.max(read, 0));
        }
    }

    private String sha256Hex(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("运行环境缺少 SHA-256", ex);
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * 补偿删除：本次上传的对象没有被数据库记录引用时调用。
     *
     * <p>补偿失败不能吞——它意味着一个孤儿对象留在存储里。记 ERROR 日志让它可见；
     * 若本次本来就有主异常，保留主异常（补偿失败只记日志），否则把补偿失败作为本次失败抛出。
     */
    private void deleteUploadedObject(String objectKey, RuntimeException primaryFailure) {
        try {
            objectStorageService.delete(objectKey);
        } catch (RuntimeException compensationFailure) {
            log.error("补偿删除失败，对象成为孤儿 objectKey={}，需人工清理", objectKey, compensationFailure);
            if (primaryFailure == null) {
                throw compensationFailure;
            }
        }
    }

    private void deleteStagedQuietly(Path staged) {
        if (staged == null) {
            return;
        }
        try {
            Files.deleteIfExists(staged);
        } catch (IOException ex) {
            log.error("上传暂存文件未能删除，需人工清理 path={}", staged, ex);
        }
    }

    private Integer toInteger(Long value) {
        return value == null ? null : value.intValue();
    }
}
