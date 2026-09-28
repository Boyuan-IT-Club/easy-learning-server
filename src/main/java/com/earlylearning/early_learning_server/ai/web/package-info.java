/**
 * HTTP 边界：Controller、对外契约的数据载体、请求语义校验。
 *
 * <p>这里只做三件事：把请求形状收下来并校验、调用业务服务、把结果装成契约形状。
 * **领域规则不写在这里**——它们属于 {@code score}/{@code transcribe}/{@code task}。
 *
 * <p>注意：本包的请求/响应模型被业务层、端口与适配器复用（见上层 package-info 的依赖说明），
 * 所以改动这些模型的影响面比"只是一个 DTO"要大。
 */
package com.earlylearning.early_learning_server.ai.web;
