package com.rampbot.cluster.platform.server.manager;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;


import com.rampbot.cluster.platform.client.controller.ClientController;
import com.rampbot.cluster.platform.client.utils.DBHelper;
import com.rampbot.cluster.platform.client.utils.RedisHelper;
import com.rampbot.cluster.platform.network.tcp.manager.TcpManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Server extends UntypedActor {


    @Getter
    ActorRef tcpManager;

    @Getter
    ActorRef servertHeleper;

    public static Props props() {
        return Props.create(Server.class);
    }

    @Override
    public void onReceive(Object o) throws Throwable {

    }

    public void preStart(){

        this.servertHeleper = this.getContext().actorOf(Props.create(ServertHeleper.class,  this), "servertHeleper" );

       this.tcpManager =  this.getContext().actorOf(Props.create(TcpManager.class,  this), "tcpManager" );

       // 增加数据库测试
        Integer testRestult = DBHelper.getStoreStatus(10794);
        log.info("获取虚拟门店一的值守状态测试Mysql数据库的结果 {} ", testRestult);


        boolean isHasSafeOrder = RedisHelper.isExistsOrder(10111,10794, "安防");
        log.info("获取虚拟门店一的是否有安防订单测试redis数据库的结果 {} ", isHasSafeOrder);



    }

    public ActorRef launchClientController(ActorRef tcpController, String equipmentId){
        ActorRef clientRef = this.getContext().actorOf(Props.create(ClientController.class, tcpController, equipmentId, this.servertHeleper), "ClientController." + equipmentId + "." + System.currentTimeMillis());
        log.info("生成盒子{}的管理员{}", equipmentId, clientRef);
        return clientRef;
    }

    public void stopActor(ActorRef toBeStoppedClient){
        log.info("销毁管理员{}", toBeStoppedClient);
        this.getContext().stop(toBeStoppedClient);
    }









}
