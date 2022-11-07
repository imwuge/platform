package com.rampbot.cluster.platform.domain;


import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
@Builder
@Getter
public class NoteClientStop {
    // TODO: 2022/11/7 因为失联，销毁这个门店服务的actor
}
