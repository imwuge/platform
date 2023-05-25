package com.rampbot.cluster.platform.domain;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
@Builder
@Getter
public class NoteCheckDisconnectTimeout {
    String storeName;
    Integer storeId;
    Integer companyId;
}
