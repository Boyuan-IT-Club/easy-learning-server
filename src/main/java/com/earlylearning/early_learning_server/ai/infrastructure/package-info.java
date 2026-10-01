/**
 * 基础设施层：domain 端口的实现——ECNU 大模型适配器、fake 缺省实现（compose 不联网也能跑通链路）、
 * 音频字节解析、评分标准文件加载、任务内存登记处。只被 Spring 装配引用，业务代码不直接 import。
 */
package com.earlylearning.early_learning_server.ai.infrastructure;
