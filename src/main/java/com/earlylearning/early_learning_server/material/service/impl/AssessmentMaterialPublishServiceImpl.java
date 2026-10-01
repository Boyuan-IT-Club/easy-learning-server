package com.earlylearning.early_learning_server.material.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.earlylearning.early_learning_server.ai.service.rubric.RubricCatalogService;
import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.idempotency.IdempotencyScope;
import com.earlylearning.early_learning_server.common.idempotency.IdempotencyService;
import com.earlylearning.early_learning_server.common.idempotency.InputFingerprint;
import com.earlylearning.early_learning_server.common.idempotency.StoredResponse;
import com.earlylearning.early_learning_server.common.media.MediaTypeDetector;
import com.earlylearning.early_learning_server.entity.AssessmentMaterial;
import com.earlylearning.early_learning_server.entity.CloudFile;
import com.earlylearning.early_learning_server.enums.CloudFileKind;
import com.earlylearning.early_learning_server.enums.ContentStatus;
import com.earlylearning.early_learning_server.material.client.json.ConfigJson;
import com.earlylearning.early_learning_server.material.client.zip.MaterialZipReader;
import com.earlylearning.early_learning_server.material.client.zip.ZipPackage;
import com.earlylearning.early_learning_server.material.mapper.AssessmentMaterialMapper;
import com.earlylearning.early_learning_server.material.mapper.GrammarRefMapper;
import com.earlylearning.early_learning_server.material.model.MaterialPublishLimits;
import com.earlylearning.early_learning_server.material.service.AssessmentMaterialPublishService;
import com.earlylearning.early_learning_server.material.service.MaterialConfigValidator;
import com.earlylearning.early_learning_server.material.service.ValidatedMaterial;
import com.earlylearning.early_learning_server.storage.model.IncomingFile;
import com.earlylearning.early_learning_server.storage.service.CloudFileService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** {@link AssessmentMaterialPublishService} 的实现。 */
@Service
public class AssessmentMaterialPublishServiceImpl implements AssessmentMaterialPublishService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentMaterialPublishServiceImpl.class);

    private static final IdempotencyScope SCOPE = IdempotencyScope.ASSESSMENT_MATERIAL_PUBLISH;

    /**
     * 材料包接受的媒体格式，比 storage 的上传白名单窄：图片收 JPEG/PNG/WebP，
     * 录音收 m4a/mp3/wav，与评分、转写两侧的格式口径一致。
     */
    private static final Map<String, CloudFileKind> MEDIA_KIND_BY_MIME = Map.of(
            "image/jpeg", CloudFileKind.IMAGE,
            "image/png", CloudFileKind.IMAGE,
            "image/webp", CloudFileKind.IMAGE,
            "audio/mp4", CloudFileKind.AUDIO,
            "audio/mpeg", CloudFileKind.AUDIO,
            "audio/wav", CloudFileKind.AUDIO);

    private final AssessmentMaterialMapper assessmentMaterialMapper;
    private final GrammarRefMapper grammarRefMapper;
    private final IdempotencyService idempotencyService;
    private final MaterialZipReader materialZipReader;
    private final MaterialConfigValidator materialConfigValidator;
    private final CloudFileService cloudFileService;
    private final RubricCatalogService rubricCatalogService;
    private final MediaTypeDetector mediaTypeDetector;
    private final MaterialPublishLimits materialPublishLimits;
    private final TransactionTemplate transaction;
    private final ObjectMapper objectMapper;

    public AssessmentMaterialPublishServiceImpl(AssessmentMaterialMapper assessmentMaterialMapper,
                                                GrammarRefMapper grammarRefMapper,
                                                IdempotencyService idempotencyService,
                                                MaterialZipReader materialZipReader,
                                                MaterialConfigValidator materialConfigValidator,
                                                CloudFileService cloudFileService,
                                                RubricCatalogService rubricCatalogService,
                                                MediaTypeDetector mediaTypeDetector,
                                                MaterialPublishLimits materialPublishLimits,
                                                PlatformTransactionManager transactionManager,
                                                ObjectMapper objectMapper) {
        this.assessmentMaterialMapper = assessmentMaterialMapper;
        this.grammarRefMapper = grammarRefMapper;
        this.idempotencyService = idempotencyService;
        this.materialZipReader = materialZipReader;
        this.materialConfigValidator = materialConfigValidator;
        this.cloudFileService = cloudFileService;
        this.rubricCatalogService = rubricCatalogService;
        this.mediaTypeDetector = mediaTypeDetector;
        this.materialPublishLimits = materialPublishLimits;
        this.transaction = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapper;
    }

    @Override
    public AssessmentMaterial publish(String idempotencyKey, IncomingFile zip) {
        if (zip.size() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (zip.size() > materialPublishLimits.maxZipBytes()) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE, null,
                    ApiErrorDetails.ofLimit(ApiErrorDetails.LimitName.SIZE_BYTES, materialPublishLimits.maxZipBytes()));
        }

        Path staged = null;
        try {
            staged = Files.createTempFile("material-zip-", ".zip");
            zip.stager().stageTo(staged);
            String zipSha256 = sha256Hex(staged);
            String fingerprint = InputFingerprint.of(zipSha256);

            Optional<StoredResponse> replayed = idempotencyService.peek(SCOPE, idempotencyKey, fingerprint);
            if (replayed.isPresent()) {
                log.info("材料发布请求重放，返回首次结果 zipBytes={}", zip.size());
                return fromSnapshot(replayed.get().body());
            }

            ZipPackage pkg = materialZipReader.read(staged);
            try {
                return publishValidatedPackage(idempotencyKey, fingerprint, zipSha256, pkg);
            } finally {
                materialZipReader.cleanup(pkg);
            }
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "材料包暂存读写失败", ex);
        } finally {
            deleteQuietly(staged);
        }
    }

    private AssessmentMaterial publishValidatedPackage(String idempotencyKey, String fingerprint,
                                                       String zipSha256, ZipPackage pkg) {
        JsonNode config = ConfigJson.parse(pkg.configJson());
        Set<String> knownGrammarCodes = selectKnownGrammarCodes(config);
        Set<String> packageFileNames = pkg.fileNames();

        // 第一遍只校验不上传：配置与文件全部合格才开始搬运媒体
        materialConfigValidator.build(config, packageFileNames, rubricCatalogService.contentItemCodes(),
                knownGrammarCodes, verifyingResolver(packageFileNames));

        Map<String, String> codeByName = uploadPackageFiles(zipSha256, pkg);
        ValidatedMaterial materialized = materialConfigValidator.build(config, packageFileNames,
                rubricCatalogService.contentItemCodes(), knownGrammarCodes,
                (fileName, fieldPath) -> codeByName.get(fileName));
        String frozenConfig = objectMapper.writeValueAsString(materialized.activityConfig());

        return transaction.execute(status -> {
            Optional<StoredResponse> claimed = idempotencyService.claim(SCOPE, idempotencyKey, fingerprint);
            if (claimed.isPresent()) {
                return fromSnapshot(claimed.get().body());
            }
            List<AssessmentMaterial> existing =
                    assessmentMaterialMapper.selectByCodeForUpdate(materialized.officialMaterialCode());
            for (AssessmentMaterial row : existing) {
                if (row.getContentVersion().equals(materialized.contentVersion())) {
                    throw new BusinessException(ErrorCode.CONTENT_VERSION_EXISTS,
                            "该编号下此内容版本已发布",
                            ApiErrorDetails.atField("/content_version"));
                }
            }
            assessmentMaterialMapper.disableActive(materialized.officialMaterialCode());

            AssessmentMaterial entity = new AssessmentMaterial();
            entity.setOfficialMaterialCode(materialized.officialMaterialCode());
            entity.setContentVersion(materialized.contentVersion());
            entity.setName(materialized.name());
            entity.setActivityConfigsJson(frozenConfig);
            entity.setStatus(ContentStatus.ACTIVE);
            assessmentMaterialMapper.insert(entity);

            AssessmentMaterial saved = assessmentMaterialMapper.selectById(entity.getId());
            idempotencyService.record(SCOPE, idempotencyKey, 201, saved);
            log.info("发布评估材料 materialId={} code={} version={} previousVersions={}",
                    saved.getId(), saved.getOfficialMaterialCode(), saved.getContentVersion(), existing.size());
            return saved;
        });
    }

    private Set<String> selectKnownGrammarCodes(JsonNode config) {
        Set<String> referenced = materialConfigValidator.referencedGrammarCodes(config);
        if (referenced.isEmpty()) {
            return Set.of();
        }
        return grammarRefMapper.selectByCodes(referenced).stream()
                .map(grammar -> grammar.grammarCode())
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 逐个上传包内媒体。媒体幂等键由包指纹加条目序号派生：同包重试复用已传文件，
     * 不同包即便内容相同也各自成编号，键空间与人工上传（UUID）不相交。
     */
    private Map<String, String> uploadPackageFiles(String zipSha256, ZipPackage pkg) {
        Map<String, String> codeByName = new LinkedHashMap<>();
        int index = 0;
        try {
            for (ZipPackage.PackagedFile file : pkg.files()) {
                CloudFileKind kind = kindOf(file);
                String mediaKey = zipSha256.substring(0, 24) + ":" + index;
                IncomingFile incoming = new IncomingFile(file.sizeBytes(), file.fileName(), null,
                        target -> java.nio.file.Files.copy(file.stagedPath(), target,
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING));
                CloudFile saved = cloudFileService.upload(mediaKey, incoming, kind, file.fileName());
                codeByName.put(file.fileName(), saved.getFileCode());
                index++;
            }
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "媒体文件读取失败", ex);
        }
        return codeByName;
    }

    private CloudFileKind kindOf(ZipPackage.PackagedFile file) throws IOException {
        byte[] head = new byte[MediaTypeDetector.HEAD_BYTES];
        int read;
        try (InputStream in = Files.newInputStream(file.stagedPath())) {
            read = in.readNBytes(head, 0, head.length);
        }
        String mime = mediaTypeDetector.detect(read == head.length ? head : java.util.Arrays.copyOf(head, Math.max(read, 0)));
        CloudFileKind kind = mime == null ? null : MEDIA_KIND_BY_MIME.get(mime);
        if (kind == null) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "包内媒体格式不支持",
                    new ApiErrorDetails(null, null, null, null, null, null, file.fileName()));
        }
        return kind;
    }

    private MaterialConfigValidator.FileRefResolver verifyingResolver(Set<String> packageFileNames) {
        return (fileName, fieldPath) -> {
            if (!packageFileNames.contains(fileName)) {
                throw new BusinessException(ErrorCode.INVALID_RESOURCE_REFERENCE, "配置引用的文件未在包内找到",
                        new ApiErrorDetails(fieldPath, null, null, null, null, null, fileName));
            }
            return "";
        };
    }

    /** 把幂等快照还原成版本实体：重放路径与首次路径产出同一个对象。 */
    private AssessmentMaterial fromSnapshot(String body) {
        return objectMapper.readValue(body, AssessmentMaterial.class);
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

    private void deleteQuietly(Path staged) {
        if (staged == null) {
            return;
        }
        try {
            Files.deleteIfExists(staged);
        } catch (IOException ex) {
            log.error("材料包暂存文件未能删除，需人工清理 path={}", staged, ex);
        }
    }
}
