package com.rampbot.cluster.platform.domain;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
@Builder
@Getter
public class NoteServerClientStatus {
    final int is_wrzs; // 是否开启无人值守
    final int is_door_closed_two; // 门2逻辑状态
    final int is_door_closed_one;// 门1逻辑状态
    final int is_help_out;
    final int is_help_in;
    final int is_poweroff;
    final int is_door_real_close_one; // 门1检测状态
    final int is_door_real_close_two; // 门2检测状态
    final int firmwareVersion;
    final int is_out_human_detected;
    final int is_in_human_detected;
}
