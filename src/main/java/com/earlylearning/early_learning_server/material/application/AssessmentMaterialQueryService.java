package com.earlylearning.early_learning_server.material.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.material.domain.AssessmentMaterial;
import com.earlylearning.early_learning_server.material.domain.ContentStatus;
import com.earlylearning.early_learning_server.material.domain.GrammarDefinition;
import com.earlylearning.early_learning_server.material.domain.MaterialDownload;
import com.earlylearning.early_learning_server.material.domain.MaterialPage;
import com.earlylearning.early_learning_server.material.domain.MaterialSummary;
import com.earlylearning.early_learning_server.material.domain.MaterialVersionSummary;
import com.earlylearning.early_learning_server.material.infrastructure.AssessmentMaterialMapper;
import com.earlylearning.early_learning_server.material.infrastructure.AssessmentMaterialQueryMapper;
import com.earlylearning.early_learning_server.material.infrastructure.GrammarRefMapper;
import com.earlylearning.early_learning_server.storage.application.CloudFileQueryService;
import com.earlylearning.early_learning_server.storage.domain.CloudFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 评估材料的查询用例：管理端筛选分页、版本禁用、平板端版本目录与依赖展开。
 *
 * <p>下载展开遵循契约的整批语义：任一依赖文件不可用即整次失败，不返回不完整清单。
 * 权限：管理员或教师凭证；本模块不校验。
 */
@Service
public class AssessmentMaterialQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String CODE_PATTERN = "^[A-Za-z0-9_-]+$";
    private static final String VERSION_PATTERN = "^[A-Za-z0-9][A-Za-z0-9._-]*$";

    private final AssessmentMaterialMapper mapper;
    private final AssessmentMaterialQueryMapper queryMapper;
    private final GrammarRefMapper grammarMapper;
    private final CloudFileQueryService cloudFiles;
    private final MaterialConfigValidator validator;
    private final TransactionTemplate transaction;
    private final ObjectMapper objectMapper;

    public AssessmentMaterialQueryService(AssessmentMaterialMapper mapper,
                                          AssessmentMaterialQueryMapper queryMapper,
                                          GrammarRefMapper grammarMapper,
                                          CloudFileQueryService cloudFiles,
                                          MaterialConfigValidator validator,
                                          PlatformTransactionManager transactionManager,
                                          ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.queryMapper = queryMapper;
        this.grammarMapper = grammarMapper;
        this.cloudFiles = cloudFiles;
        this.validator = validator;
        this.transaction = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapper;
    }

    public MaterialPage list(int page, int pageSize, String code, ContentStatus status, String keyword) {
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        String codeFilter = code == null || code.isBlank() ? null : code;
        String statusFilter = status == null ? null : status.value();
        String pattern = keyword == null || keyword.isBlank() ? null : "%" + escapeLikeWildcards(keyword) + "%";
        long total = queryMapper.countMatching(codeFilter, statusFilter, pattern);
        List<MaterialSummary> items = queryMapper
                .selectPage(codeFilter, statusFilter, pattern, pageSize, (long) (page - 1) * pageSize)
                .stream().map(MaterialSummary::from).toList();
        return new MaterialPage(items, page, pageSize, total);
    }

    /** 禁用指定版本：禁止新选，历史引用不受影响。重复禁用幂等返回当前行。 */
    public AssessmentMaterial disable(int id) {
        return transaction.execute(status -> {
            AssessmentMaterial row = mapper.selectByIdForUpdate(id);
            if (row == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            if (row.getStatus() == ContentStatus.DISABLED) {
                return row;
            }
            mapper.disableById(id);
            return mapper.selectById(id);
        });
    }

    /** 全量版本目录，含 DISABLED；按稳定编号升序、创建先后升序。 */
    public List<MaterialVersionSummary> versions() {
        return queryMapper.selectAllOrdered().stream().map(MaterialVersionSummary::from).toList();
    }

    /**
     * 读取指定版本并展开完整依赖：配置图片与音频、引用语法条目及其图标。
     * ACTIVE 与 DISABLED 都可读取，供历史引用。
     */
    public MaterialDownload download(String code, String version) {
        requirePathCode(code);
        requirePathVersion(version);
        AssessmentMaterial material = mapper.selectByCodeAndVersion(code, version);
        if (material == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        JsonNode config = readFrozenConfig(material);

        Set<String> dependencyCodes = new LinkedHashSet<>(validator.referencedFileCodes(config));
        Set<String> grammarCodes = validator.referencedGrammarCodes(config);

        List<GrammarDefinition> grammars = grammarCodes.isEmpty()
                ? List.of()
                : selectGrammars(grammarCodes);
        for (GrammarDefinition grammar : grammars) {
            if (grammar.iconFileCode() != null) {
                dependencyCodes.add(grammar.iconFileCode());
            }
        }

        List<CloudFile> dependencies = dependencyCodes.stream().map(this::requireReadyDependency).toList();
        return new MaterialDownload(material, dependencies, grammars);
    }

    private List<GrammarDefinition> selectGrammars(Set<String> grammarCodes) {
        Map<String, GrammarDefinition> byCode = grammarMapper.selectByCodes(grammarCodes).stream()
                .collect(Collectors.toMap(GrammarDefinition::grammarCode, Function.identity()));
        List<GrammarDefinition> ordered = new ArrayList<>();
        for (String code : grammarCodes) {
            GrammarDefinition definition = byCode.get(code);
            if (definition == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "配置引用的语法不存在");
            }
            ordered.add(definition);
        }
        return List.copyOf(ordered);
    }

    /**
     * 依赖文件必须可下载。storage 侧把已删除的文件报成 410，这里按材料下载契约
     * 统一成 409 RESOURCE_NOT_READY：对调用方而言就是这份清单现在拿不完整。
     */
    private CloudFile requireReadyDependency(String fileCode) {
        try {
            return cloudFiles.requireReadable(fileCode);
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.FILE_DELETED) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_READY, "依赖文件已不可下载",
                        ApiErrorDetails.atFile(fileCode));
            }
            throw ex;
        }
    }

    private JsonNode readFrozenConfig(AssessmentMaterial material) {
        try {
            return objectMapper.readTree(material.getActivityConfigsJson());
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "已存储的活动配置无法解析", ex);
        }
    }

    private void requirePathCode(String code) {
        if (code == null || !code.matches(CODE_PATTERN) || code.length() > 128) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "编号格式不合法",
                    ApiErrorDetails.atField("/parameters/code"));
        }
    }

    private void requirePathVersion(String version) {
        if (version == null || !version.matches(VERSION_PATTERN) || version.length() > 64) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "版本标签格式不合法",
                    ApiErrorDetails.atField("/parameters/version"));
        }
    }

    /** LIKE 通配符按普通字符处理，用感叹号转义；反斜杠在 MySQL 字符串里本身有含义，不能用作转义符。 */
    private String escapeLikeWildcards(String keyword) {
        return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
