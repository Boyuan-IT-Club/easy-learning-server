package com.earlylearning.early_learning_server.storage.dto;

import java.util.List;

import com.earlylearning.early_learning_server.storage.model.DownloadSignature;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 契约的签发响应体：{@code items} 顺序与请求一致、编号一一对应。
 *
 * <p>批次要么整体成功、要么整体失败，所以这里不存在"部分可用"的形态——列表长度与请求相同。
 */
public record DownloadSignatureBatchResponse(

        @JsonProperty("items") List<DownloadSignatureResponse> items) {

    public static DownloadSignatureBatchResponse from(List<DownloadSignature> signatures) {
        return new DownloadSignatureBatchResponse(
                signatures.stream().map(DownloadSignatureResponse::from).toList());
    }
}
