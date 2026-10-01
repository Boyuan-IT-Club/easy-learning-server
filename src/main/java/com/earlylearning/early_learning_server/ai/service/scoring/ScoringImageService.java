package com.earlylearning.early_learning_server.ai.service.scoring;

import java.util.List;

import com.earlylearning.early_learning_server.ai.model.scoring.ImageRef;
import com.earlylearning.early_learning_server.ai.model.scoring.ScoringImage;

/**
 * 把提交里的图片引用解析成模型能直接使用的内容,供评分输入组装。
 *
 * <ul>
 *   <li>{@code SERVER_FETCH}:按 {@code file_code} 取回字节;先校验文件状态与大小,
 *       语义与签发/元数据接口一致。</li>
 *   <li>{@code INLINE_IMAGE}:解码请求携带的 base64 并校验字节上限。</li>
 *   <li>{@code CONFIRMED_DESCRIPTION}:教师确认过的说明,直接作为文字交给模型。</li>
 * </ul>
 *
 * <p>取回的字节只在内存中存在,交给模型后即丢弃;不落盘、不写日志。
 */
public interface ScoringImageService {

    List<ScoringImage> resolve(List<ImageRef> images);
}
