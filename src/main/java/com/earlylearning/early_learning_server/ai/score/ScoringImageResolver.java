package com.earlylearning.early_learning_server.ai.score;

import java.util.ArrayList;
import java.util.List;

import com.earlylearning.early_learning_server.ai.web.ImageContext;
import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.storage.CloudFile;
import com.earlylearning.early_learning_server.storage.CloudFileKind;
import com.earlylearning.early_learning_server.storage.CloudFileQueryService;
import com.earlylearning.early_learning_server.storage.ObjectStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 把请求里的图片解析成**模型能直接用的东西**。
 *
 * <p>两种形态各自处理：
 * <ul>
 *   <li>{@code SERVER_FETCH} —— 按 {@code file_code} 取回字节（这正是本次需求要的能力）；
 *       取之前校验状态与大小，校验语义与签发/元数据接口一致，避免"下载拿不到、评分却能拿到"；</li>
 *   <li>{@code CONFIRMED_DESCRIPTION} —— 教师确认过的说明，直接作为文字给模型。</li>
 * </ul>
 *
 * <p>数据边界：取回的字节只在内存里存在，交给模型后即丢弃；不落盘、不写日志。
 */
@Component
public class ScoringImageResolver {

    private static final Logger log = LoggerFactory.getLogger(ScoringImageResolver.class);

    private final CloudFileQueryService files;
    private final ObjectStorageService storage;
    private final ScoringLimits limits;

    public ScoringImageResolver(CloudFileQueryService files, ObjectStorageService storage, ScoringLimits limits) {
        this.files = files;
        this.storage = storage;
        this.limits = limits;
    }

    public List<ScoringImage> resolve(List<ImageContext> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        List<ScoringImage> resolved = new ArrayList<>(images.size());
        for (ImageContext image : images) {
            resolved.add(image.kind() == ImageContext.ImageKind.SERVER_FETCH
                    ? fetch(image.fileCode())
                    : new ScoringImage(image.fileCode(), null, null, image.description()));
        }
        return List.copyOf(resolved);
    }

    /** 按编号取回图片字节：先看状态与大小，再读内容。 */
    private ScoringImage fetch(String fileCode) {
        CloudFile file = files.requireReadable(fileCode);
        if (file.getFileKind() != CloudFileKind.IMAGE) {
            // 音频/PDF 喂给多模态模型没有意义，属于调用方用错了编号
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ApiErrorDetails.atFile(fileCode));
        }
        if (file.getSizeBytes() > limits.maxImageBytes()) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE,
                    ApiErrorDetails.ofLimit(ApiErrorDetails.LimitName.SIZE_BYTES, limits.maxImageBytes()));
        }
        byte[] content = storage.read(file.getObjectKey());
        log.info("为评分取回图片 fileCode={} mime={} bytes={}", fileCode, file.getMimeType(), content.length);
        return new ScoringImage(fileCode, file.getMimeType(), content, null);
    }
}
