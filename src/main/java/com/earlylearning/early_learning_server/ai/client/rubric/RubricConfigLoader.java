package com.earlylearning.early_learning_server.ai.client.rubric;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.earlylearning.early_learning_server.ai.model.rubric.RubricConfig;
import com.earlylearning.early_learning_server.ai.model.rubric.RubricProperties;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * 加载随代码发布的评分标准，并校验它与配置里的版本号一致。
 *
 * <p>为什么要在启动时校验：版本号有两个来源——{@code ai.rubric.version}（任务凭据里回给客户端的那个）
 * 和标准文件里的 {@code rubric_version}。两者不一致意味着"客户端以为在用 A 版标准，实际模型读的是 B 版"，
 * 这种错一旦发生，所有分数都不可信且很难发现。宁可启动失败。
 */
@Component
@Slf4j
public class RubricConfigLoader {

    private static final String RESOURCE = "ai/rubric-config.json";

    private final RubricConfig config;

    public RubricConfigLoader(RubricProperties properties) {
        this.config = load();
        if (!properties.version().equals(config.rubricVersion())) {
            throw new IllegalStateException("评分标准版本不一致：配置为 " + properties.version()
                    + "，标准文件（" + RESOURCE + "）为 " + config.rubricVersion());
        }
        log.info("评分标准已加载 version={} macrostructure={} microstructure={} questionReasoning={}",
                config.rubricVersion(), config.macrostructure().size(), config.microstructure().size(),
                config.questionReasoning() == null ? "无" : config.questionReasoning().itemCode());
    }

    public RubricConfig config() {
        return config;
    }

    private static RubricConfig load() {
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        if (!resource.exists()) {
            throw new IllegalStateException("缺少评分标准配置：" + RESOURCE);
        }
        try (InputStream in = resource.getInputStream()) {
            return new ObjectMapper().readValue(in, RubricConfig.class);
        } catch (IOException ex) {
            throw new IllegalStateException("评分标准配置无法读取：" + RESOURCE, ex);
        }
    }
}
