package com.earlylearning.early_learning_server.material.service;

import java.util.List;

import com.earlylearning.early_learning_server.entity.AssessmentMaterial;
import com.earlylearning.early_learning_server.entity.ContentStatus;
import com.earlylearning.early_learning_server.material.model.MaterialDownload;
import com.earlylearning.early_learning_server.material.model.MaterialPage;
import com.earlylearning.early_learning_server.material.model.MaterialVersionSummary;

/**
 * 评估材料的查询用例：管理端筛选分页、版本禁用、平板端版本目录与依赖展开。
 *
 * <p>下载展开遵循契约的整批语义：任一依赖文件不可用即整次失败，不返回不完整清单。
 * 权限：管理员或教师凭证；本模块不校验。
 */
public interface AssessmentMaterialQueryService {

    MaterialPage list(int page, int pageSize, String code, ContentStatus status, String keyword);

    /** 禁用指定版本：禁止新选，历史引用不受影响。重复禁用幂等返回当前行。 */
    AssessmentMaterial disable(int id);

    /** 全量版本目录，含 DISABLED；按稳定编号升序、创建先后升序。 */
    List<MaterialVersionSummary> versions();

    /**
     * 读取指定版本并展开完整依赖：配置图片与音频、引用语法条目及其图标。
     * ACTIVE 与 DISABLED 都可读取，供历史引用。
     */
    MaterialDownload download(String code, String version);
}
