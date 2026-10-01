package com.earlylearning.early_learning_server.material.model;

import java.util.List;

import com.earlylearning.early_learning_server.entity.AssessmentMaterial;
import com.earlylearning.early_learning_server.entity.CloudFile;

/**
 * 指定版本的下载视图：冻结配置加服务端展开去重后的完整依赖。
 * 依赖包含配置里的图片与音频，以及题目引用语法的图标文件；任一依赖不可用整次查询失败。
 */
public record MaterialDownload(

        AssessmentMaterial content,

        List<CloudFile> dependencies,

        List<GrammarDefinition> grammars) {
}
