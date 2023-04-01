package com.rampbot.cluster.platform.network.tcp.manager;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.io.Tcp;
import akka.io.TcpMessage;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.rampbot.cluster.platform.network.tcp.controller.TcpController;
import com.rampbot.cluster.platform.server.manager.Server;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;

@Slf4j
public class TcpManager extends UntypedActor {


    @Getter
    private final Server server;
    private final ActorRef manager;
    private final static int TCP_PORT = 5000;
    private final InetSocketAddress boundAddress;




    public void preStart(){

    }






    private ActorRef pubSubMediator = DistributedPubSub.get(this.getContext().system()).mediator();
    public TcpManager(Server server) {
        this.server = server;
        this.manager = Tcp.get(this.getContext().system()).getManager();
        this.boundAddress = new InetSocketAddress(TCP_PORT);
        this.manager.tell(TcpMessage.bind(this.getSelf(), this.boundAddress, 300), this.getSelf());
        this.pubSubMediator.tell(new DistributedPubSubMediator.Put(this.getSelf()), this.getSelf());

    }

    @Override
    public void onReceive(Object msg) throws UnknownHostException {
        if (msg instanceof Tcp.Bound) {
            log.info("tcp路由器开始监听到发起链接 {}", msg);
        } else if (msg instanceof Tcp.Connected) {
            Tcp.Connected connected = (Tcp.Connected) msg;
            InetSocketAddress remoteAddress = connected.remoteAddress();
            String remoteIp = remoteAddress.getAddress().getHostAddress();
            log.info("收到远程链接的ip {}", remoteIp);
            final ActorRef tcpController =
                    getContext().actorOf(Props.create(TcpController.class, remoteAddress, this.getServer(), this.sender()));
            getSender().tell(TcpMessage.register(tcpController), getSelf()); // 告诉tcp的来源处，把消息发给谁
        }else {
            log.info("收到未知的消息{}", msg);
            this.unhandled(msg);
        }
    }
}
