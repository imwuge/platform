package com.rampbot.cluster.platform.server.manager;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;


import akka.io.TcpMessage;
import com.rampbot.cluster.platform.client.controller.ClientController;
import com.rampbot.cluster.platform.client.utils.DBHelper;
import com.rampbot.cluster.platform.client.utils.RedisHelper;
import com.rampbot.cluster.platform.domain.NoteTcpcontrollerStop;
import com.rampbot.cluster.platform.network.tcp.manager.TcpManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class Server extends UntypedActor {


    @Getter
    ActorRef tcpManager;

    @Getter
    ActorRef servertHeleper;

    @Getter
    public Map<String, ActorRef> equipmentId2ClientRef;

    public static Props props() {
        return Props.create(Server.class);
    }

    @Override
    public void onReceive(Object msg) throws Throwable {
        if(msg instanceof NoteTcpcontrollerStop){
            NoteTcpcontrollerStop o = (NoteTcpcontrollerStop) msg;
            String equipmentId = o.getEquipmentId();
            log.info("server收到门店{}收到来自服务端的停止消息{}",equipmentId, o);
            
            if(this.equipmentId2ClientRef.containsKey(equipmentId) && this.equipmentId2ClientRef.get(equipmentId) != null){
                ActorRef toBeRemove = this.getEquipmentId2ClientRef().get(equipmentId);
                this.stopActor(equipmentId, toBeRemove);
            }
            
            
            
           
        }else {
            log.info("收到未知道的消息类型{}", msg);
            this.unhandled(msg);
        }
    }

    public void preStart(){
        this.servertHeleper = this.getContext().actorOf(Props.create(ServertHeleper.class,  this), "servertHeleper" );

        this.tcpManager =  this.getContext().actorOf(Props.create(TcpManager.class,  this), "tcpManager" );


        this.equipmentId2ClientRef = new HashMap<>();
//       // 增加数据库测试
//        Integer testRestult = DBHelper.getStoreStatus(10794);
//        log.info("获取虚拟门店一的值守状态测试Mysql数据库的结果 {} ", testRestult);
//
//
//        boolean isHasSafeOrder = RedisHelper.isExistsOrder(10111,10794, "安防");
//        log.info("获取虚拟门店一的是否有安防订单测试redis数据库的结果 {} ", isHasSafeOrder);



    }

    public ActorRef launchClientController(ActorRef tcpController, String equipmentId){
        ActorRef clientRef = this.getContext().actorOf(Props.create(ClientController.class, tcpController, equipmentId, this.servertHeleper, this.getSelf()), "ClientController." + equipmentId + "." + System.currentTimeMillis());
        log.info("生成盒子{}的管理员{}", equipmentId, clientRef);
        this.equipmentId2ClientRef.put(equipmentId, clientRef);
        return clientRef;
    }

    public void stopActor(String equipmentId,  ActorRef toBeStoppedClient){
        log.info("销毁门店{}管理员{}", equipmentId,toBeStoppedClient);
        this.getEquipmentId2ClientRef().remove(equipmentId);
        this.getContext().stop(toBeStoppedClient);
    }









}
