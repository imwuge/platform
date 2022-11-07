package com.rampbot.cluster.platform.network.tcp.controller;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import akka.io.Tcp;
import akka.io.TcpMessage;
import akka.util.ByteString;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.BiMap;
import com.mysql.jdbc.StringUtils;
import com.rampbot.cluster.platform.client.utils.BuildResponse;
import com.rampbot.cluster.platform.domain.DownLoadVoiceData;
import com.rampbot.cluster.platform.domain.NoteTcpcontrollerStop;
import com.rampbot.cluster.platform.network.tcp.manager.TcpManager;
import com.rampbot.cluster.platform.server.manager.Server;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class TcpController extends UntypedActor {



    private final InetSocketAddress remoteAddress;
    private final ActorRef romoteTcpManager;  //tcp actor
//    private final TcpManager tcpManager;
    private final Server server;
    private String equipmentId = null;
    private  ActorRef clientRef = null;

    public TcpController(@NonNull final InetSocketAddress remoteAddress,
                                @NonNull final Server server,
                                @NonNull final ActorRef romoteTcpManager) {
        this.remoteAddress = remoteAddress;
        this.romoteTcpManager = romoteTcpManager;
//        this.tcpManager = tcpManager;
        this.server = server;


    }




    @Override
    public void onReceive(Object msg) {
        if (msg instanceof Tcp.Received) {

            Tcp.Received received = (Tcp.Received) msg;
//            log.info("收到客户端消息 {}", received);
            String  result = received.data().utf8String();
            log.info("收到客户端消息 {}", result);


            // 获取一次设备号
            if(this.equipmentId == null){
                this.equipmentId = this.getEquipmentId(result);
            }

            if(this.equipmentId == null){
                return;
            }
            // 创建该门店的actor
            if(this.clientRef == null){
                log.info("盒子{}第一次上报心跳，生成该盒子管理员", this.equipmentId);
                this.clientRef = this.server.launchClientController(this.getSelf(), this.equipmentId);
            }else {
                this.clientRef.tell(result, this.getSelf());
            }


        } else if (msg instanceof Tcp.ConnectionClosed) {
            log.info("收到 {}", msg);
            if(this.clientRef != null){
                this.server.stopActor(this.clientRef);
            }
            this.getContext().stop(getSelf());
        } else if(msg instanceof String){
            String o = (String) msg;
            log.info("返回客户端消息{}", o);
            this.sendMsg(o);
        }else if(msg instanceof DownLoadVoiceData){
            DownLoadVoiceData o = (DownLoadVoiceData) msg;
            log.info("返回客户端消息{}", o);
            this.sendMsg(o);
        }else if(msg instanceof NoteTcpcontrollerStop){
            NoteTcpcontrollerStop o = (NoteTcpcontrollerStop) msg;
            log.info("tcp controller 收到来自服务端的停止消息{}", o);
            if(this.clientRef != null){
                this.server.stopActor(this.clientRef);
            }
            this.getContext().stop(getSelf());
        }else {
            this.unhandled(msg);
        }
    }







    private String getEquipmentId(String clientMsg){
        Map<String, Object> msgMap = null;

        String equipmentId = null;
        if (StringUtils.isNullOrEmpty(clientMsg)) {
            String msg = BuildResponse.buildResponseMsgError(-1, "内容为空", "");
            this.sendMsg(msg);
            return  null;
        }
        try {
            msgMap = JSON.parseObject(clientMsg, Map.class);
        } catch (Exception e) {
        }
        if (msgMap == null || msgMap.size() < 1) {

            String msg = BuildResponse.buildResponseMsgError(-2, "内容格式错误", "");
            this.sendMsg(msg);
            return  null;
        }
        if(msgMap.containsKey("sid")){
            equipmentId = msgMap.get("sid").toString();
        }else {
            String msg = BuildResponse.buildResponseMsgError(-3, "缺少序列号", "");
            this.sendMsg(msg);
            return null;
        }

        return equipmentId;
    }

    private void sendMsg(String clientMsg){
        this.romoteTcpManager.tell(TcpMessage.write(ByteString.fromString(clientMsg)), this.getSelf());
    }

    private void sendMsg(DownLoadVoiceData clientMsg){
        this.romoteTcpManager.tell(TcpMessage.write(ByteString.fromArray(clientMsg.getVoiceData())), getSelf());
    }

}
