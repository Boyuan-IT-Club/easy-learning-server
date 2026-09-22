/**
 * 字典条目与课程词语位置管理。
 *
 * <p>负责词条和词语位置维护、字典导入及业务校验、完整字典获取。词条以 entry_code 寻址，词语位置关联具体课程版本；字典不增加内容版本号。</p>
 *
 * <p>数据边界：dict_entry、dict_gloss。</p>
 * <p>依据：<a href="https://boyuanclub.feishu.cn/wiki/WhWyw6Stgi5OBXkwSlccBMzRnVd">05_课程字典与语法，3.12 字典</a>；
 * 表结构和 JSON 契约以 docs/技术方案.md 为准。</p>
 *
 * <p>Controller、DTO、Service、Mapper、Entity 及子包按实际功能需要创建。</p>
 */
package com.earlylearning.early_learning_server.dictionary;

