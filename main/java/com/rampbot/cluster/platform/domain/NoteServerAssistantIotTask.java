package com.rampbot.cluster.platform.domain;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;


@Data
@Builder
@Getter
public class NoteServerAssistantIotTask {
    private final int companyId;
    private final int storeId;
    private final int helperId;
    private final long id;
    private final int status;
    private final int actionType;

}