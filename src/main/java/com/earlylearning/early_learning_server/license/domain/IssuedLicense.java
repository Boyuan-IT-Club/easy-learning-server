package com.earlylearning.early_learning_server.license.domain;

/** 刚生成的一枚激活码：原码只在生成结果与短时重放中出现，不得写日志。新码状态恒为 UNUSED。 */
public record IssuedLicense(int id, String activationCode) {

    @Override
    public String toString() {
        return "IssuedLicense[id=" + id + ", activationCode=REDACTED]";
    }
}
