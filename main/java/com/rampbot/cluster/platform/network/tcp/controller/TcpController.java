package com.rampbot.cluster.platform.network.tcp.controller;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import akka.io.Tcp;
import akka.io.TcpMessage;
import akka.util.ByteString;
import com.alibaba.fastjson.JSON;
import com.mysql.jdbc.StringUtils;
import com.rampbot.cluster.platform.client.utils.BuildResponse;
import com.rampbot.cluster.platform.client.utils.DBHelper;
import com.rampbot.cluster.platform.client.utils.RedisHelper;
import com.rampbot.cluster.platform.domain.DownLoadVoiceData;
import com.rampbot.cluster.platform.domain.NoteClientControllerReloadConfig;
import com.rampbot.cluster.platform.domain.NoteStoreConfigStatus;
import com.rampbot.cluster.platform.domain.NoteTcpcontrollerStop;
import com.rampbot.cluster.platform.server.manager.Server;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import java.net.InetSocketAddress;

import java.util.Arrays;
import java.util.Map;


@Slf4j
public class TcpController extends UntypedActor {



    private final InetSocketAddress remoteAddress;
    private final ActorRef romoteTcpManager;  //tcp actor
//    private final TcpManager tcpManager;
    private final Server server;
    private String equipmentId = null;
    private  ActorRef clientRef = null;
    private  ActorRef tcpProvideer = null;
    private String fullResult = "";
    private int lastReplyLength = 0;
    private int serverAnswerNum = 0; // 服务端回复客户端心跳的次数，超过两次算作正常

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

            this.tcpProvideer = this.getSender();
            Tcp.Received received = (Tcp.Received) msg;

            String result = received.data().utf8String();

//            System.out.println("收到调试消息 " + result);
//
//            if(!result.startsWith("rev")){
//                this.sendMsg(result);
//            }
//
//            return;



            String resultCopy = result;
            this.fullResult = this.fullResult + resultCopy;
            if(this.isFullResult(fullResult)){
                String fullResultMsg = this.fullResult;
                this.fullResult = "";
                // 获取一次设备号

                // 去重
//                String[] msgSpilt = fullResultMsg.split("\r\n");
//                String sendMsg =  msgSpilt[0] + "\r\n";
                String sendMsg = this.getUsefulData(fullResultMsg);

                if(this.equipmentId == null){
                    this.equipmentId = this.getEquipmentId(sendMsg);

                    // 第一次获取equipmentId 说明是首次建立tcp 建立tcp后 更新tcpcontroller
                    if(this.equipmentId != null){
                        log.info("门店{}已正确获取设备号，门店IP为{}", this.equipmentId, remoteAddress.getAddress().getHostAddress());
                        this.server.getEquipmentId2TcpControllerRef().put(this.equipmentId, this.getSelf());
                    }
                }

                if(this.equipmentId == null || this.equipmentId.length() < 2){
                    log.info("门店{}收到不正常消息，不予以处理，消息内容{}",this.equipmentId, fullResultMsg);
                    return;
                }
//                // 创建该门店的actor
//                if(this.clientRef == null){
//                    log.info("门店{}第一次上报心跳，生成该门店管理员", this.equipmentId);
//                    this.clientRef = this.server.launchClientController(this.getSelf(), this.equipmentId);
//                }else {
//                    // 这里最终将数据交给clientRef
//                    this.clientRef.tell(sendMsg, this.getSelf());
//                }
                // 创建该门店的actor
                if(this.clientRef != null){
                    this.clientRef.tell(sendMsg, this.getSelf());
                }else if(this.server.getEquipmentId2ClientRef().containsKey(this.equipmentId) && this.server.getEquipmentId2ClientRef().get(this.equipmentId) != null){
                    // 这里最终将数据交给clientRef
                    log.info("门店{}重新建立了链接，在记录中找到了控制器", this.equipmentId);
                    this.clientRef = this.server.getEquipmentId2ClientRef().get(this.equipmentId);
                    this.clientRef.tell(sendMsg, this.getSelf());
                    this.clientRef.tell(NoteClientControllerReloadConfig.builder().build(), this.getSelf());

                }else {

                    log.info("门店{}第一次上报心跳，生成该门店管理员", this.equipmentId);
                    this.clientRef = this.server.launchClientController(this.equipmentId);
                }
            }else{
                this.clearMsg();
            }
        } else if (msg instanceof Tcp.ConnectionClosed) {
            // TODO: 2023/5/25 这里有个重要假设，服务端永远不主动停止tcp 
            log.info("收到门店{}主动发出的停止消息 {}", this.equipmentId, msg);
//            if(this.clientRef != null){
//                this.server.stopActor(this.clientRef);
//            }
            if(this.equipmentId != null){
                RedisHelper.deleteKey(this.equipmentId);
            }

            if(this.tcpProvideer != null ){
                this.tcpProvideer.tell(TcpMessage.close(), this.getSelf());
            }
            this.getContext().stop(getSelf());
        } else if(msg instanceof String){
            String o = (String) msg;
//            log.info("Reply {} msg {}",this.equipmentId, o);
            if(this.serverAnswerNum <= 2){
                this.serverAnswerNum++;
                if(this.serverAnswerNum == 3){
                    // 让clientRef更新门店状态为正常
                    this.clientRef.tell(NoteStoreConfigStatus.builder().status("正常").build(), this.getSelf());
                }
            }
            if(this.lastReplyLength != o.length() || o.contains("event")){
                log.info("Reply {} msg {}",this.equipmentId, o);
            }
            //log.info("Reply {} msg {}",this.equipmentId, o);

            this.lastReplyLength = o.length();
            this.sendMsg(o);
        }else if(msg instanceof DownLoadVoiceData){
            DownLoadVoiceData o = (DownLoadVoiceData) msg;
            log.info("Reply {} msg {}",this.equipmentId, o);
            this.sendMsg(o);
        }else if(msg instanceof NoteTcpcontrollerStop){
            NoteTcpcontrollerStop o = (NoteTcpcontrollerStop) msg;
            log.info("门店{} tcp controller 收到来自服务端的停止消息{}",this.equipmentId, o);
            if(this.clientRef != null){
                this.server.stopActor(this.equipmentId, this.clientRef);
            }
            if(this.tcpProvideer != null ){
                this.tcpProvideer.tell(TcpMessage.close(), this.getSelf());
            }
            this.getContext().stop(this.getSelf());
        }else {
            log.info("收到未知道的消息类型{}", msg);
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
        boolean isHasStart = false;
        boolean isHasEnd = false;
        int startIndex = 0;
        int endIndex = 1;
        if(resultArry.length < 3){return false;}
        for(int i = 0; i < resultArry.length; i++){
            if(resultArry[i].equals("{")){
                isHasStart = true;
                startIndex = i;
                break;
            }
        }
        if(isHasStart){
            for(int i = startIndex+1; i < resultArry.length; i++){
                if(resultArry[i].equals("}")){

                    isHasEnd = true;
                    endIndex = i;
                    break;
                }
            }
        }



        return isHasStart && isHasEnd;
        //return resultArry[0].equals("{") && resultArry[resultArry.length - 3].equals("}");
    }


    /**
     * 滤波数据
     * @param result
     * @return
     */
    private String getUsefulData(String result){
        String[] resultArry = result.split("");

        boolean isHasStart = false;
        boolean isHasEnd = false;
        int startIndex = 0;
        int endIndex = 1;
        //if(resultArry.length < 3){return false;}
        for(int i = 0; i < resultArry.length; i++){
            if(resultArry[i].equals("{")){
                isHasStart = true;
                startIndex = i;
                break;
            }
        }
        if(isHasStart){
            for(int i = startIndex+1; i < resultArry.length; i++){
                if(resultArry[i].equals("}")){

                    isHasEnd = true;
                    endIndex = i;
                    break;
                }
            }
        }
        String data = "";
        for(int i = startIndex; i <= endIndex; i++){
            data = data + resultArry[i];
        }

         data = data + "\r\n";
        return data;
        //return resultArry[0].equals("{") && resultArry[resultArry.length - 3].equals("}");
    }

    /***
     * 防止出现永远解析不出来内存泄漏
     */
    private void clearMsg(){
        String[] resultArry = this.fullResult.split("");
        if(resultArry.length > 300 && resultArry[resultArry.length - 3].equals("}")){
            log.info("门店{}收到错误数据数量超过300，且最后结尾是正确的，清空数据 {}",this.equipmentId, this.fullResult);
            this.fullResult = "";
            String msg = BuildResponse.buildResponseMsgError(9, "内容格式错误", "");
            this.sendMsg(msg);
            return;
        }

        if(resultArry.length > 500){
            log.info("门店{}收到错误数据数量超过500，清空数据 {}", this.equipmentId, this.fullResult);
            this.fullResult = "";
            String msg = BuildResponse.buildResponseMsgError(9, "内容格式错误", "");
            this.sendMsg(msg);
        }
    }

}
