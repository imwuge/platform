package com.rampbot.cluster.platform.domain;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;


@Data
@Builder
@Getter
public class NoteClientControllerDownloadVoice {

    private int downloadPlace;
    private int voiceId;
//    private int clientDownloadName;

}
