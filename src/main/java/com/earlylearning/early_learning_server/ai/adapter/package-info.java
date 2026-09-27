/**
 * 出站端口的实现（适配器）。
 *
 * <p>目录结构即"谁能被替换"：{@code port} 是接口，这里是实现。当前只有 {@code fake} 一套，
 * 接入真实模型后新增同级子包（如 {@code adapter.openai}）并标 {@code @Primary}，业务代码不动。
 */
package com.earlylearning.early_learning_server.ai.adapter;
