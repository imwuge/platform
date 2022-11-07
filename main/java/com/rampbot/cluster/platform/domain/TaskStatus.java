package com.rampbot.cluster.platform.domain;

import lombok.Getter;

@Getter
public enum TaskStatus {
    pending, // 已就绪，可以发送
    sent, // 已发送
    completed, // 已完成
    failed, // 失败
}
