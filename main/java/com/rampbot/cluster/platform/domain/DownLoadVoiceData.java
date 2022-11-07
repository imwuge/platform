package com.rampbot.cluster.platform.domain;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Builder
@Getter
@Setter
public class DownLoadVoiceData {
    private final byte[] voiceData; // 要下载语音的音频内容
}
