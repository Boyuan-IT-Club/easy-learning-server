package com.earlylearning.early_learning_server.ai.domain.llm;
import com.earlylearning.early_learning_server.ai.domain.task.AiTaskFailedException;

import java.util.List;

/**
 * 与大模型对话的出站端口：与厂商无关。
 *
 * <p>业务只认这一层。换厂商 = 换一个实现（{@code infrastructure} 下的适配器），业务代码不动；
 * 实现由配置 {@code ai.llm.provider} 选择。
 *
 * <p>请求里带不带图片由调用方决定，选哪个模型由实现按"有没有图片"决定——
 * 例如 ECNU 的 {@code ecnu-max} 不支持图片理解，带图必须走 {@code ecnu-plus}。
 */
public interface ChatModel {

    /**
     * 发起一次对话补全。
     *
     * @param request 消息、图片与可选的输出 JSON Schema
     * @return 模型返回的文本（指定 Schema 时应当是符合 Schema 的纯净 JSON）
     * @throws AiTaskFailedException 调用失败（与其他出站端口一致，携带任务失败码）；
     *         {@link AiTaskFailedException#isRetryable()} 表明是否值得重试
     */
    ChatResponse complete(ChatRequest request);

    /**
     * 本次请求会实际使用的模型标识（供日志与结果记录）。
     *
     * <p>模型元数据要随评分结果一起返回，所以这里要能预先问出模型名，
     * 而不是等响应回来才知道。
     */
    String modelFor(ChatRequest request);

    record ChatRequest(List<ChatMessage> messages,
                       String schemaName,
                       String schemaJson,
                       List<ImagePart> images) {

        /** 本次请求是否带图片——实现据此选模型。 */
        public boolean hasImages() {
            return images != null && !images.isEmpty();
        }
    }

    record ChatMessage(String role, String text) {
    }

    record ImagePart(String mimeType, byte[] content) {
    }

    record ChatResponse(String content, String model) {
    }
}
