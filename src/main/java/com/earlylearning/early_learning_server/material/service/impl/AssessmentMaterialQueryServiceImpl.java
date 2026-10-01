package com.earlylearning.early_learning_server.material.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.entity.AssessmentMaterial;
import com.earlylearning.early_learning_server.entity.CloudFile;
import com.earlylearning.early_learning_server.entity.ContentStatus;
import com.earlylearning.early_learning_server.material.mapper.AssessmentMaterialMapper;
import com.earlylearning.early_learning_server.material.mapper.AssessmentMaterialQueryMapper;
import com.earlylearning.early_learning_server.material.mapper.GrammarRefMapper;
import com.earlylearning.early_learning_server.material.model.GrammarDefinition;
import com.earlylearning.early_learning_server.material.model.MaterialDownload;
import com.earlylearning.early_learning_server.material.model.MaterialPage;
import com.earlylearning.early_learning_server.material.model.MaterialSummary;
import com.earlylearning.early_learning_server.material.model.MaterialVersionSummary;
import com.earlylearning.early_learning_server.material.service.AssessmentMaterialQueryService;
import com.earlylearning.early_learning_server.material.service.MaterialConfigValidator;
import com.earlylearning.early_learning_server.storage.service.CloudFileQueryService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** {@link AssessmentMaterialQueryService} 的实现。 */
@Service
public class AssessmentMaterialQueryServiceImpl implements AssessmentMaterialQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String CODE_PATTERN = "^[A-Za-z0-9_-]+$";
    private static final String VERSION_PATTERN = "^[A-Za-z0-9][A-Za-z0-9._-]*$";

    private final AssessmentMaterialMapper assessmentMaterialMapper;
    private final AssessmentMaterialQueryMapper assessmentMaterialQueryMapper;
    private final GrammarRefMapper grammarRefMapper;
    private final CloudFileQueryService cloudFileQueryService;
    private final MaterialConfigValidator materialConfigValidator;
    private final TransactionTemplate transaction;
    private final ObjectMapper objectMapper;

    public AssessmentMaterialQueryServiceImpl(AssessmentMaterialMapper assessmentMaterialMapper,
                                              AssessmentMaterialQueryMapper assessmentMaterialQueryMapper,
                                              GrammarRefMapper grammarRefMapper,
                                              CloudFileQueryService cloudFileQueryService,
                                              MaterialConfigValidator materialConfigValidator,
                                              PlatformTransactionManager transactionManager,
                                              ObjectMapper objectMapper) {
        this.assessmentMaterialMapper = assessmentMaterialMapper;
        this.assessmentMaterialQueryMapper = assessmentMaterialQueryMapper;
        this.grammarRefMapper = grammarRefMapper;
        this.cloudFileQueryService = cloudFileQueryService;
        this.materialConfigValidator = materialConfigValidator;
        this.transaction = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapper;
    }

    @Override
    public MaterialPage list(int page, int pageSize, String code, ContentStatus status, String keyword) {
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        String codeFilter = code == null || code.isBlank() ? null : code;
        String statusFilter = status == null ? null : status.value();
        String pattern = keyword == null || keyword.isBlank() ? null : "%" + escapeLikeWildcards(keyword) + "%";
        long total = assessmentMaterialQueryMapper.countMatching(codeFilter, statusFilter, pattern);
        List<MaterialSummary> items = assessmentMaterialQueryMapper
                .selectPage(codeFilter, statusFilter, pattern, pageSize, (long) (page - 1) * pageSize)
                .stream().map(MaterialSummary::from).toList();
        return new MaterialPage(items, page, pageSize, total);
    }

    @Override
    public AssessmentMaterial disable(int id) {
        return transaction.execute(status -> {
            AssessmentMaterial row = assessmentMaterialMapper.selectByIdForUpdate(id);
            if (row == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            if (row.getStatus() == ContentStatus.DISABLED) {
                return row;
            }
            assessmentMaterialMapper.disableById(id);
            return assessmentMaterialMapper.selectById(id);
        });
    }

    @Override
    public List<MaterialVersionSummary> versions() {
        return assessmentMaterialQueryMapper.selectAllOrdered().stream().map(MaterialVersionSummary::from).toList();
    }

    @Override
    public MaterialDownload download(String code, String version) {
        requirePathCode(code);
        requirePathVersion(version);
        AssessmentMaterial material = assessmentMaterialMapper.selectByCodeAndVersion(code, version);
        if (material == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        JsonNode config = readFrozenConfig(material);

        Set<String> dependencyCodes = new LinkedHashSet<>(materialConfigValidator.referencedFileCodes(config));
        Set<String> grammarCodes = materialConfigValidator.referencedGrammarCodes(config);

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
        Map<String, GrammarDefinition> byCode = grammarRefMapper.selectByCodes(grammarCodes).stream()
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
            return cloudFileQueryService.requireReadable(fileCode);
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
