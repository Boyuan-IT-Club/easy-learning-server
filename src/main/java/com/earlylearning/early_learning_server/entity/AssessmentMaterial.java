package com.earlylearning.early_learning_server.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.earlylearning.early_learning_server.common.enums.ContentStatus;

import lombok.Getter;
import lombok.Setter;

/**
 * 已发布的评估材料版本。同一 official_material_code 下最多一个 ACTIVE 版本；
 * 行内容发布后不可变，修订只能发新 content_version。
 *
 * <p>{@code activityConfigsJson} 存发布时冻结的 {@code material.model.config.ActivityConfig} JSON：
 * 配置里的文件引用已经从包内文件名转换成 CF_ 编号，后续下载按编号展开依赖。
 */
@TableName("assessment_material")
@Getter
@Setter
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
}
