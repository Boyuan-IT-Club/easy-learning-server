package com.earlylearning.early_learning_server.material.dto;

import java.util.List;

import com.earlylearning.early_learning_server.material.model.GrammarDefinition;
import com.earlylearning.early_learning_server.material.model.MaterialDownload;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 下载契约形状：冻结配置加服务端展开去重后的完整依赖与语法条目。
 * FileDependency 的 duration_ms 对非音频显式输出 null。
 */
public record AssessmentMaterialDownloadResponse(

        @JsonProperty("content") AssessmentMaterialResponse content,

        @JsonProperty("dependencies") List<FileDependencyResponse> dependencies,

        @JsonProperty("grammars") List<GrammarResponse> grammars) {

    public static AssessmentMaterialDownloadResponse from(MaterialDownload download, ObjectMapper objectMapper) {
        JsonNode config = objectMapper.readTree(download.content().getActivityConfigsJson());
        return new AssessmentMaterialDownloadResponse(
                AssessmentMaterialResponse.from(download.content(), config),
                download.dependencies().stream().map(FileDependencyResponse::from).toList(),
                download.grammars().stream().map(GrammarResponse::from).toList());
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record FileDependencyResponse(

            @JsonProperty("file_code") String fileCode,

            @JsonProperty("file_kind") String fileKind,

            @JsonProperty("mime_type") String mimeType,

            @JsonProperty("size_bytes") Long sizeBytes,

            @JsonProperty("sha256") String sha256,

            @JsonProperty("duration_ms") Integer durationMs) {

        static FileDependencyResponse from(com.earlylearning.early_learning_server.entity.CloudFile file) {
            return new FileDependencyResponse(
                    file.getFileCode(),
                    file.getFileKind().value(),
                    file.getMimeType(),
                    file.getSizeBytes(),
                    file.getSha256(),
                    file.getDurationMs());
        }
    }

    public record GrammarResponse(

            @JsonProperty("grammar_code") String grammarCode,

            @JsonProperty("name") String name,

            @JsonProperty("version") int version,

            @JsonProperty("icon_file_code") String iconFileCode,

            @JsonProperty("status") String status,

            @JsonProperty("created_at") String createdAt,

            @JsonProperty("updated_at") String updatedAt) {

        static GrammarResponse from(GrammarDefinition grammar) {
            return new GrammarResponse(
                    grammar.grammarCode(),
                    grammar.name(),
                    grammar.version(),
                    grammar.iconFileCode(),
                    grammar.status().value(),
                    AssessmentMaterialResponse.isoUtc(grammar.createdAt()),
                    AssessmentMaterialResponse.isoUtc(grammar.updatedAt()));
        }
    }
}
