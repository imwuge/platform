package com.rampbot.cluster.platform.server.manager;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;


import com.rampbot.cluster.platform.client.controller.ClientController;
import com.rampbot.cluster.platform.network.tcp.manager.TcpManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Server extends UntypedActor {


    @Getter
    ActorRef tcpManager;

    @Getter
    ActorRef clientManager;

    public static Props props() {
        return Props.create(Server.class);
    }

    @Override
    public void onReceive(Object o) throws Throwable {

    }

    public void preStart(){


       this.tcpManager =  this.getContext().actorOf(Props.create(TcpManager.class,  this), "tcpManager" );


    }

    public ActorRef launchClientController(ActorRef tcpController, String equipmentId){
        ActorRef clientRef = this.getContext().actorOf(Props.create(ClientController.class, tcpController, equipmentId), "ClientController." + equipmentId + "." + System.currentTimeMillis());
        log.info("生成盒子{}的管理员{}", equipmentId, clientRef);
        return clientRef;

    }

    public void stopActor(ActorRef toBeStoppedClient){
        this.getContext().stop(toBeStoppedClient);
    }









}
