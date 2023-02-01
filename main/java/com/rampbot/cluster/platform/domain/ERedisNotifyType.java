package com.rampbot.cluster.platform.domain;

import lombok.Getter;


public enum ERedisNotifyType {


    // *类型：0 为 mus_orders 表, 1 为 notify 表
    MUS_ORDERS(1),
    MUS_NOTIFY(2);

    @Getter
    private int statusCode;

    ERedisNotifyType(final int statusCode) {
        this.statusCode = statusCode;
    }



}
