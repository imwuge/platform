package com.rampbot.cluster.platform.domain;


import com.rampbot.cluster.platform.client.utils.DBHelper;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.util.List;


@Data
@Builder
@Getter
public class NoteControllerTask {
    private final List<Task> setWrzsStatusTask ; // 服务端生成的播放语音的任务
    private final List<Task> doorTask ; // 服务端生成的门状态改变的iot任务
    private final List<VoiceTask> downloadVoiceTask ; // 服务端生成的下载语音的任务
    private final List<Task> playVoiceTask ; // 服务端生成的播放语音的任务
    private final List<Task> noResponseTask ; // 服务端生成的不需要回复的任务
}
