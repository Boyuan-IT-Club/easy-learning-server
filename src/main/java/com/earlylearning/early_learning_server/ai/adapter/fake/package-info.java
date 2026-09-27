/**
 * 默认（假）实现：接通真实模型前把链路跑通用。
 *
 * <p>行为可控，便于联调与测试：
 * <ul>
 *   <li>{@code ai.fake-transcriber.*} / {@code ai.fake-scorer.*} / {@code ai.fake-answer-scorer.*}
 *       系统属性触发"调用失败"与"输出不合法"两类路径；</li>
 *   <li>"输出不合法"是**故意**的：用来验证契约那句"不合格输出不能变成成功结果"确实被执行。</li>
 * </ul>
 *
 * <p>理由与证据都是固定内容：不给儿童数据留任何进入文本的路径。
 */
package com.earlylearning.early_learning_server.ai.adapter.fake;
