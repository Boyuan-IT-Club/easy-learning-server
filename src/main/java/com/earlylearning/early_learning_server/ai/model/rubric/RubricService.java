package com.earlylearning.early_learning_server.ai.model.rubric;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;

/**
 * 评分规则版本与条目。
 *
 * <p>契约的版本语义：首次提交可省略版本，服务端采用固定配置并在任务凭据里返回实际版本；
 * 指定了当前不可用的版本返回 503 {@code RUBRIC_UNAVAILABLE}，不静默换版——
 * 否则客户端会把不同标准的分数混在一起。
 */
@Service
public class RubricService {

    private static final Logger log = LoggerFactory.getLogger(RubricService.class);

    private final RubricProperties properties;

    public RubricService(RubricProperties properties) {
        this.properties = properties;
    }

    /**
     * 解析本次评分实际采用的版本。
     *
     * @param requestedVersion 请求携带的版本，可为空
     * @return 实际采用的版本；未指定时即当前固定版本
     * @throws BusinessException 指定版本与当前版本不一致（503）
     */
    public String resolveVersion(String requestedVersion) {
        String current = properties.version();
        if (requestedVersion == null || requestedVersion.isBlank()) {
            return current;
        }
        if (!current.equals(requestedVersion)) {
            log.info("请求了不可用的评分标准版本 requested={} current={}", requestedVersion, current);
            //  message 是「可展示的脱敏说明，不包含输入文本」，
            // 所以不回显请求里那个版本号；服务端当前版本是服务端自己的信息，可以给。
            throw new BusinessException(ErrorCode.RUBRIC_UNAVAILABLE,
                    "评分标准版本不可用；当前版本为 " + current);
        }
        return current;
    }

    public String currentVersion() {
        return properties.version();
    }

    /**
     * 重试时的版本：以任务记录的版本为准，不能因为配置或请求变了就换标准。
     *
     * <p>请求若指定了别的版本，按「不可用」拒绝而不是静默采用——否则同一道题的两次作答
     * 会落在不同标准下，分数不可比。
     *
     * @throws BusinessException 请求指定的版本与任务记录的不一致（503）
     */
    public String versionForRetry(String recordedVersion, String requestedVersion) {
        if (requestedVersion != null && !requestedVersion.isBlank()
                && !recordedVersion.equals(requestedVersion)) {
            log.info("重试时请求了与任务记录不同的版本 recorded={} requested={}",
                    recordedVersion, requestedVersion);
            // 同上：不回显请求里指定的版本号
            throw new BusinessException(ErrorCode.RUBRIC_UNAVAILABLE,
                    "本任务采用的评分标准版本为 " + recordedVersion + "，请求指定的版本不可用");
        }
        return recordedVersion;
    }

    /** 六个固定宏观维度，顺序即规则顺序。 */
    public List<MacroDimensionCode> macroDimensions() {
        return List.of(MacroDimensionCode.values());
    }

    /** 五个固定微观维度，顺序即规则顺序。 */
    public List<MicroDimensionCode> microDimensions() {
        return List.of(MicroDimensionCode.values());
    }
}
