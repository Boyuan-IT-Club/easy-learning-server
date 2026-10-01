package com.earlylearning.early_learning_server.ai.domain.scoring;

/** 一张图片的提交形态：真图、教师确认的说明、或只给编号由服务端取回。 */
public enum ImageKind {
    INLINE_IMAGE,
    CONFIRMED_DESCRIPTION,
    /** 只给编号，由服务端取回图片内容——本项目的主用形态（平板侧图片已在对象存储里）。 */
    SERVER_FETCH
}
