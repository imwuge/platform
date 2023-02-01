package com.rampbot.cluster.platform.domain;


import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
@Builder
@Getter
public class NoteAutoCloseDoor {
    //执行动作 (901 全开门、  902 全关门、  903 进店开门、 904 进店关门、  905 离店开门、 906 离店关门)
    private final Integer actionType;
}
