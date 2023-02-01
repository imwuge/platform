package com.rampbot.cluster.platform.domain;


import lombok.Data;

/**
 * Redis 中 存储的通知订单
 */
@Data
public class Notify {

    /**
     * 模块主键 Id 或 单号
     */
    private long id;

    /**
     * 类型（类型：0 为 mus_orders 表, 1 为 notify 表)
     */
    private int type;

    /**
     * 标题
     * 【购物】新订单通知
     */
    private String title;

    /**
     * 通知类别，type=1时生效
     * 类型：1 离店通知，7 托管消息，8 断电，9 失联
     */
    private int notifyType;

    /**
     * 内容
     * 门店：{0}[{1}]\r\n会员用户：{2}\r\n信誉分：{3} {4} {5}\r\n\r\n手机号码：{6}\r\n累计单数：{7}次
     */
    private String content;

    /**
     *当前状态 （0 未读1,  以读取读取)
     *  */
    private Integer status;

    /**
     * 顾客编号
     */
    private Long clientId;
}
