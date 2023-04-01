package com.rampbot.cluster.platform.domain;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;


@Data
@Builder
@Getter
public class NotePlayVoiceTimeout {

    final int voiceId;

    final int player;

    final int playTimes;

    final int volume;

}