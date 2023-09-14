package com.rampbot.cluster.platform.domain;

import afu.org.checkerframework.checker.igj.qual.I;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
@Builder
@Getter
public class NoteVoiceTask {

    private final String  storeId;
    private final String companyId;
    private final Integer voiceId ;
    private final Integer helperId ;
    private final Long id ;
    private final Integer status ;
    private final Integer player ;
    private final Integer volume ;
    private final Integer times ;
    private final Integer interval;
    private final Long createTime ;
    private final Integer enableTime ;
}
