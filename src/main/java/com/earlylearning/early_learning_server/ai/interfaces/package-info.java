/**
 * 入站 HTTP 适配。HTTP 是这一层的事：状态码、信封、wire 形状。
 *
 * <ul>
 *   <li>{@code controller} — Controller：校验请求形状、wire → 领域命令映射、领域对象 → HTTP 响应；</li>
 *   <li>{@code dto} — 请求/响应 DTO（Jackson 注解只出现在这里）；</li>
 *   <li>{@code validation} — 请求形状校验器（"这个形态该有哪些字段"）；业务规则不在。</li>
 * </ul>
 */
package com.earlylearning.early_learning_server.ai.interfaces;
