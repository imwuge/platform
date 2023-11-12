package com.rampbot.cluster.platform.domain;


import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
@Builder
@Getter
public class NoteServerVoiceTaskStatus {
    private final VoiceTask voiceTask;
    private final int status; // -1 下载失败、0 未下载、1 下载中、2 已下载
    private final String failedReason; // 失败原因
}
