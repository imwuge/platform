package com.rampbot.cluster.platform.domain;


import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
@Builder
@Getter
public class NoteCheckDisconnect {
    Integer nextChackTimeout; // s
    Integer storeId;
    Integer companyId;
    String storeName;
}
