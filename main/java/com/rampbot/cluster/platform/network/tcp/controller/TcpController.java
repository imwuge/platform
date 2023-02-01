package com.rampbot.cluster.platform.network.tcp.controller;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import akka.io.Tcp;
import akka.io.TcpMessage;
import akka.util.ByteString;
import com.alibaba.fastjson.JSON;
import com.mysql.jdbc.StringUtils;
import com.rampbot.cluster.platform.client.utils.BuildResponse;
import com.rampbot.cluster.platform.domain.DownLoadVoiceData;
import com.rampbot.cluster.platform.domain.NoteTcpcontrollerStop;
import com.rampbot.cluster.platform.server.manager.Server;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import java.net.InetSocketAddress;

import java.util.Map;


@Slf4j
public class TcpController extends UntypedActor {



    private final InetSocketAddress remoteAddress;
    private final ActorRef romoteTcpManager;  //tcp actor
//    private final TcpManager tcpManager;
    private final Server server;
    private String equipmentId = null;
    private  ActorRef clientRef = null;
    private String fullResult = "";

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

            String result = received.data().utf8String();

            String resultCopy = result;
            this.fullResult = this.fullResult + resultCopy;
            if(this.isFullResult(fullResult)){
                String fullResultMsg = this.fullResult;
                this.fullResult = "";
                // 获取一次设备号

                // 去重
                String[] msgSpilt = fullResultMsg.split("\r\n");
                String sendMsg =  msgSpilt[0] + "\r\n";


                if(this.equipmentId == null){
                    this.equipmentId = this.getEquipmentId(sendMsg);
                }

                if(this.equipmentId == null){
                    return;
                }
                // 创建该门店的actor
                if(this.clientRef == null){
                    log.info("盒子{}第一次上报心跳，生成该盒子管理员", this.equipmentId);
                    this.clientRef = this.server.launchClientController(this.getSelf(), this.equipmentId);
                }else {
                    // 这里最终将数据交给clientRef
                    this.clientRef.tell(sendMsg, this.getSelf());
                }
            }else{
                this.clearMsg();
            }
        } else if (msg instanceof Tcp.ConnectionClosed) {
            log.info("收到客户端主动发出的停止消息 {}", msg);
            if(this.clientRef != null){
                this.server.stopActor(this.clientRef);
            }
            this.getContext().stop(getSelf());
        } else if(msg instanceof String){
            String o = (String) msg;
            log.info("Reply {} msg {}",this.equipmentId, o);
            this.sendMsg(o);
        }else if(msg instanceof DownLoadVoiceData){
            DownLoadVoiceData o = (DownLoadVoiceData) msg;
            log.info("Reply {} msg {}",this.equipmentId, o);
            this.sendMsg(o);
        }else if(msg instanceof NoteTcpcontrollerStop){
            NoteTcpcontrollerStop o = (NoteTcpcontrollerStop) msg;
            log.info("门店{} tcp controller 收到来自服务端的停止消息{}",this.equipmentId, o);
            if(this.clientRef != null){
                this.server.stopActor(this.clientRef);
            }
//            this.getContext().stop(getSelf());
            this.getContext().stop(this.getSelf());
        }else {
            this.unhandled(msg);
        }

    }







    private String getEquipmentId(String clientMsg){
        Map<String, Object> msgMap = null;

        String equipmentId = null;
        if (StringUtils.isNullOrEmpty(clientMsg)) {
            log.info("内容为空");
            String msg = BuildResponse.buildResponseMsgError(-1, "内容为空", "");
            this.sendMsg(msg);
            return  null;
        }
        try {
            msgMap = JSON.parseObject(clientMsg, Map.class);
        } catch (Exception e) {
        }
        if (msgMap == null || msgMap.size() < 1) {
            log.info("内容格式错误 {}", clientMsg);
            String msg = BuildResponse.buildResponseMsgError(9, "内容格式错误", "");
            this.sendMsg(msg);
            return  null;
        }
        if(msgMap.containsKey("sid")){
            equipmentId = msgMap.get("sid").toString();
        }else {
            log.info("内容缺少序列号 {}", clientMsg);
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

    /**
     * 校验是否是完整数据
     * @param result
     * @return
     */
    private boolean isFullResult(String result){
        String[] resultArry = result.split("");
//        log.info("客户端{}分解后的数量为{}",this.equipmentId, resultArry.length);
        //return resultArry[0].equals("{") && resultArry[resultArry.length - 3].equals("}");
//        if(!resultArry[0].equals("{") && resultArry[resultArry.length - 3].equals("}")){
//           log.info("错误数据 {} {} ", resultArry[0], resultArry[resultArry.length - 3]);
//        }
        return resultArry[0].equals("{") && resultArry[resultArry.length - 3].equals("}");
    }

    /***
     * 防止出现永远解析不出来内存泄漏
     */
    private void clearMsg(){
        String[] resultArry = this.fullResult.split("");
        if(resultArry.length > 300 && resultArry[resultArry.length - 3].equals("}")){
            log.info("收到错误数据数量超过300，且最后结尾是正确的，清空数据 {}", this.fullResult);
            this.fullResult = "";
            String msg = BuildResponse.buildResponseMsgError(9, "内容格式错误", "");
            this.sendMsg(msg);
            return;
        }

        if(resultArry.length > 500){
            log.info("收到错误数据数量超过500，清空数据 {}", this.fullResult);
            this.fullResult = "";
            String msg = BuildResponse.buildResponseMsgError(9, "内容格式错误", "");
            this.sendMsg(msg);
        }
    }

}
