package com.rampbot.cluster.platform.domain;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
@Builder
@Getter
public class NoteServerAssistantUpdateConfig {
    private final int companyId;
    private final int storeId;
    private final long id;
}
