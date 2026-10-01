/**
 * 媒体检视共享能力：字节级 MIME 探测与音频时长读取。
 *
 * <p>storage 的上传校验与 ai 的转写准入共用这套口径，放在 common 保证两边的判断一致。
 * 整个子包都是对外 API（@NamedInterface）。
 */
@org.springframework.modulith.NamedInterface("media")
package com.earlylearning.early_learning_server.common.media;
