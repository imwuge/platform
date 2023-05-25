package com.rampbot.cluster.platform.domain;


import akka.actor.ActorRef;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
@Builder
@Getter
public class RegisterToServerHelper {

    final String equipmentId;
    final int storeId;
    final int companyId;
    final String storeName; // 门店名称
    final ActorRef serverRef;
}
