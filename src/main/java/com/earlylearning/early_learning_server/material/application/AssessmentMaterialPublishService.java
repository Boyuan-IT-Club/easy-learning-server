package com.earlylearning.early_learning_server.material.application;

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

import com.earlylearning.early_learning_server.ai.application.rubric.RubricCatalogService;
import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.idempotency.IdempotencyScope;
import com.earlylearning.early_learning_server.common.idempotency.IdempotencyService;
import com.earlylearning.early_learning_server.common.idempotency.InputFingerprint;
import com.earlylearning.early_learning_server.common.idempotency.StoredResponse;
import com.earlylearning.early_learning_server.common.media.MediaTypeDetector;
import com.earlylearning.early_learning_server.material.domain.AssessmentMaterial;
import com.earlylearning.early_learning_server.material.domain.ContentStatus;
import com.earlylearning.early_learning_server.material.domain.MaterialPublishLimits;
import com.earlylearning.early_learning_server.material.infrastructure.AssessmentMaterialMapper;
import com.earlylearning.early_learning_server.material.infrastructure.GrammarRefMapper;
import com.earlylearning.early_learning_server.material.infrastructure.json.ConfigJson;
import com.earlylearning.early_learning_server.material.infrastructure.zip.MaterialZipReader;
import com.earlylearning.early_learning_server.material.infrastructure.zip.ZipPackage;
import com.earlylearning.early_learning_server.storage.application.CloudFileService;
import com.earlylearning.early_learning_server.storage.domain.CloudFile;
import com.earlylearning.early_learning_server.storage.domain.CloudFileKind;
import com.earlylearning.early_learning_server.storage.domain.IncomingFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * ZIP 发布用例：解包与校验在事务外完成，全部通过后在短事务里提交版本行——
 * 新版设 ACTIVE、同编号旧版设 DISABLED，失败不发布半成品也不动旧版。
 *
 * <p>媒体文件通过 storage 的上传服务保存成独立官方素材（各自带幂等），发布重试时
 * 同包文件直接复用，不重复占用存储。事务失败（如版本号冲突）时已上传的素材保留：
 * 它们是合法官方文件，修正后的重新发布会原样复用。
 *
 * <p>权限：管理员凭证；本模块不校验。
 */
@Service
public class AssessmentMaterialPublishService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentMaterialPublishService.class);

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

    private final AssessmentMaterialMapper mapper;
    private final GrammarRefMapper grammarMapper;
    private final IdempotencyService idempotency;
    private final MaterialZipReader zipReader;
    private final MaterialConfigValidator validator;
    private final CloudFileService cloudFileService;
    private final RubricCatalogService rubricCatalog;
    private final MediaTypeDetector mediaTypeDetector;
    private final MaterialPublishLimits limits;
    private final TransactionTemplate transaction;
    private final ObjectMapper objectMapper;

    public AssessmentMaterialPublishService(AssessmentMaterialMapper mapper,
                                            GrammarRefMapper grammarMapper,
                                            IdempotencyService idempotency,
                                            MaterialZipReader zipReader,
                                            MaterialConfigValidator validator,
                                            CloudFileService cloudFileService,
                                            RubricCatalogService rubricCatalog,
                                            MediaTypeDetector mediaTypeDetector,
                                            MaterialPublishLimits limits,
                                            PlatformTransactionManager transactionManager,
                                            ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.grammarMapper = grammarMapper;
        this.idempotency = idempotency;
        this.zipReader = zipReader;
        this.validator = validator;
        this.cloudFileService = cloudFileService;
        this.rubricCatalog = rubricCatalog;
        this.mediaTypeDetector = mediaTypeDetector;
        this.limits = limits;
        this.transaction = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapper;
    }

    /**
     * 上传 ZIP 并发布评估材料版本。
     *
     * @return 首次发布或幂等重放得到的版本实体；同一键同包两条路径产出等价结果
     */
    public AssessmentMaterial publish(String idempotencyKey, IncomingFile zip) {
        if (zip.size() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (zip.size() > limits.maxZipBytes()) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE, null,
                    ApiErrorDetails.ofLimit(ApiErrorDetails.LimitName.SIZE_BYTES, limits.maxZipBytes()));
        }

        Path staged = null;
        try {
            staged = Files.createTempFile("material-zip-", ".zip");
            zip.stager().stageTo(staged);
            String zipSha256 = sha256Hex(staged);
            String fingerprint = InputFingerprint.of(zipSha256);

            Optional<StoredResponse> replayed = idempotency.peek(SCOPE, idempotencyKey, fingerprint);
            if (replayed.isPresent()) {
                return fromSnapshot(replayed.get().body());
            }

            ZipPackage pkg = zipReader.read(staged);
            try {
                return publishValidatedPackage(idempotencyKey, fingerprint, zipSha256, pkg);
            } finally {
                zipReader.cleanup(pkg);
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
        validator.build(config, packageFileNames, rubricCatalog.contentItemCodes(), knownGrammarCodes,
                verifyingResolver(packageFileNames));

        Map<String, String> codeByName = uploadPackageFiles(zipSha256, pkg);
        ValidatedMaterial materialized = validator.build(config, packageFileNames,
                rubricCatalog.contentItemCodes(), knownGrammarCodes,
                (fileName, fieldPath) -> codeByName.get(fileName));
        String frozenConfig = objectMapper.writeValueAsString(materialized.activityConfig());

        return transaction.execute(status -> {
            Optional<StoredResponse> claimed = idempotency.claim(SCOPE, idempotencyKey, fingerprint);
            if (claimed.isPresent()) {
                return fromSnapshot(claimed.get().body());
            }
            List<AssessmentMaterial> existing = mapper.selectByCodeForUpdate(materialized.officialMaterialCode());
            for (AssessmentMaterial row : existing) {
                if (row.getContentVersion().equals(materialized.contentVersion())) {
                    throw new BusinessException(ErrorCode.CONTENT_VERSION_EXISTS,
                            "该编号下此内容版本已发布",
                            ApiErrorDetails.atField("/content_version"));
                }
            }
            mapper.disableActive(materialized.officialMaterialCode());

            AssessmentMaterial entity = new AssessmentMaterial();
            entity.setOfficialMaterialCode(materialized.officialMaterialCode());
            entity.setContentVersion(materialized.contentVersion());
            entity.setName(materialized.name());
            entity.setActivityConfigsJson(frozenConfig);
            entity.setStatus(ContentStatus.ACTIVE);
            mapper.insert(entity);

            AssessmentMaterial saved = mapper.selectById(entity.getId());
            idempotency.record(SCOPE, idempotencyKey, 201, saved);
            return saved;
        });
    }

    private Set<String> selectKnownGrammarCodes(JsonNode config) {
        Set<String> referenced = validator.referencedGrammarCodes(config);
        if (referenced.isEmpty()) {
            return Set.of();
        }
        return grammarMapper.selectByCodes(referenced).stream()
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
