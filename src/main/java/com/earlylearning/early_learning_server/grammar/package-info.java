/**
 * 语法要素管理。
 *
 * <p>负责语法名称、图标引用、启用/禁用、当前版本维护和版本查询。以 grammar_code 稳定寻址，只保留当前版本；图标文件交由 storage 管理，个案语法统计由移动端负责。</p>
 *
 * <p>数据边界：grammar。</p>
 * <p>依据：<a href="https://boyuanclub.feishu.cn/wiki/WhWyw6Stgi5OBXkwSlccBMzRnVd">05_课程字典与语法，3.13 语法要素</a>；
 * 表结构和 JSON 契约以 docs/技术方案.md 为准。</p>
 *
 * <p>Controller、DTO、Service、Mapper、Entity 及子包按实际功能需要创建。</p>
 */
package com.earlylearning.early_learning_server.grammar;

