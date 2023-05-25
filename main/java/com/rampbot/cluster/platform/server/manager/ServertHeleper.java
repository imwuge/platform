package com.rampbot.cluster.platform.server.manager;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import com.rampbot.cluster.platform.client.utils.DBHelper;
import com.rampbot.cluster.platform.client.utils.Utils;
import com.rampbot.cluster.platform.domain.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import scala.concurrent.duration.FiniteDuration;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
@Slf4j
public class ServertHeleper extends UntypedActor {

    private final Server server;


    private Map<String, ActorRef> serverId2serverRefs;


    private String[] currentTime; // 当前时间 2023 03 08 19 40 55 星期三


    public ServertHeleper(@NonNull final Server server) {
        this.server = server;
    }


    public void preStart(){
        this.serverId2serverRefs = new HashMap<>();

        // 开启整点检测断网
        this.getTimeCurrent();

        /**
         * 需要龙哥配合修改redis
         */
//        int toNextHourMins = 60 - Integer.parseInt(currentTime[4]) + 1;
//        this.context().system().scheduler().scheduleOnce(
//                FiniteDuration.apply(toNextHourMins, TimeUnit.MINUTES),
//                this.getSelf(),
//                NoteCheckDisconnectionTimeout.builder().build(),
//                this.context().dispatcher(),
//                this.getSelf());

    }


    @Override
    public void onReceive(Object o) throws Throwable {
        if(o instanceof RegisterToServerHelper){
            RegisterToServerHelper registerToServerHelper = (RegisterToServerHelper) o;
            this.processRegisterToServerHelper(registerToServerHelper);
        }else if(o instanceof NoteCheckDisconnect){
            NoteCheckDisconnect noteCheckDisconnect = (NoteCheckDisconnect) o;
            this.processNoteCheckDisconnectTimeout(noteCheckDisconnect);
        }else if(o instanceof NoteCheckDisconnectTimeout){
            NoteCheckDisconnectTimeout noteCheckDisconnectTimeout = (NoteCheckDisconnectTimeout) o;
            log.info("最后在核对一次，门店{}是否失联{} ", noteCheckDisconnectTimeout.getStoreId(), noteCheckDisconnectTimeout);
            DBHelper.addNotifyV2(noteCheckDisconnectTimeout.getStoreId(), noteCheckDisconnectTimeout.getCompanyId(), "失联", noteCheckDisconnectTimeout.getStoreName());
        }else if(o instanceof NoteCheckDisconnectionTimeout){
            NoteCheckDisconnectionTimeout noteCheckDisconnectionTimeout = (NoteCheckDisconnectionTimeout) o;
            log.info("检测当日是否有失联门店 ");
            List<Map<String, Object>> disconnectStores = DBHelper.getDisconnectStores();
            if(disconnectStores != null && disconnectStores.size() > 0){
                log.info("今日失联门店有{}", disconnectStores );

            }


        }


    }




    private void processRegisterToServerHelper(RegisterToServerHelper registerToServerHelper){
        ActorRef serverRef = registerToServerHelper.getServerRef();
        Integer storeId = registerToServerHelper.getStoreId();
        log.info("收到门店{} {} 的注册服务actor信息", registerToServerHelper.getEquipmentId(), storeId);
        if(serverRef != null){
            this.serverId2serverRefs.put(storeId.toString(), serverRef);
        }

    }



    private void processNoteCheckDisconnectTimeout(NoteCheckDisconnect noteCheckDisconnect){
        this.context().system().scheduler().scheduleOnce(
                FiniteDuration.apply(noteCheckDisconnect.getNextChackTimeout(), TimeUnit.SECONDS),
                this.getSelf(),
                NoteCheckDisconnectTimeout.builder()
                        .companyId(noteCheckDisconnect.getCompanyId())
                        .storeId(noteCheckDisconnect.getStoreId())
                        .storeName(noteCheckDisconnect.getStoreName())
                        .build(),
                this.context().dispatcher(),
                this.getSelf());
    }


    private void processNoteCheckDisconnectionTimeout( ){
        this.getTimeCurrent();// 更新时间
        if(Integer.parseInt(this.currentTime[3]) >= 9 && Integer.parseInt(this.currentTime[3]) <= 21){
            List<Map<String, Object>> result = DBHelper.getDisconnectStores();
            if(result != null && result.size() >= 1){
                // TODO: 2023/5/1 生成集体失联订单 通知
            }
        }
    }



    /**
     * 检测是否有失联门店 间隔一小时触发一次
     */
    private void checkDisconnection(){
        this.context().system().scheduler().scheduleOnce(
                FiniteDuration.apply(1, TimeUnit.HOURS),
                this.getSelf(),
                NoteCheckDisconnectionTimeout.builder().build(),
                this.context().dispatcher(),
                this.getSelf());
    }

    private void getTimeCurrent(){
        // 当前全日期
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy MM dd HH mm ss EEEE");
        this.currentTime = dateFormat.format(new Date()).split(" ");
        this.currentTime[6] = Utils.getWeek();
    }
}


