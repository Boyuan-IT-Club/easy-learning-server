package com.earlylearning.early_learning_server.material.domain;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.earlylearning.early_learning_server.material.domain.config.ActivityConfig;

/**
 * 已发布的评估材料版本。同一 official_material_code 下最多一个 ACTIVE 版本；
 * 行内容发布后不可变，修订只能发新 content_version。
 *
 * <p>{@code activityConfigsJson} 存发布时冻结的 {@link ActivityConfig} JSON：
 * 配置里的文件引用已经从包内文件名转换成 CF_ 编号，后续下载按编号展开依赖。
 */
@TableName("assessment_material")
public class AssessmentMaterial {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String officialMaterialCode;

    private String contentVersion;

    private String name;

    private String activityConfigsJson;

    private ContentStatus status;

    /** 由数据库默认值填充；构造新行时保持为 null，MyBatis-Plus 会把它排除出 INSERT。 */
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getOfficialMaterialCode() {
        return officialMaterialCode;
    }

    public void setOfficialMaterialCode(String officialMaterialCode) {
        this.officialMaterialCode = officialMaterialCode;
    }

    public String getContentVersion() {
        return contentVersion;
    }

    public void setContentVersion(String contentVersion) {
        this.contentVersion = contentVersion;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getActivityConfigsJson() {
        return activityConfigsJson;
    }

    public void setActivityConfigsJson(String activityConfigsJson) {
        this.activityConfigsJson = activityConfigsJson;
    }

    public ContentStatus getStatus() {
        return status;
    }

    public void setStatus(ContentStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
