package com.rampbot.cluster.platform.domain;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
@Builder
@Getter
public class NoteTcpcontrollerStop {
    String equipmentId;
    // 服务端主动发起重启，当服务端认为需要发起重启，会通知tcp断开链接，
    // tcp断开链接后盒子失联，
    // 当盒子超过40s检测到断网后，进入断网状态
    // 进入断网状态，会在发起一次链接，新的链接会重新生成服务端的actor


    // 在这里备注一下，盒子主动重启
    // 盒子和主动断开链接
    // 盒子断开链接后，服务端会销毁该链接涉及的actor
    // 重新链接后会发起新的actor服务
}
