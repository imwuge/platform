package com.rampbot.cluster.platform.client.controller;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import com.rampbot.cluster.platform.client.utils.ConfigHelper;
import com.rampbot.cluster.platform.client.utils.DBHelper;
import com.rampbot.cluster.platform.client.utils.RedisHelper;
import com.rampbot.cluster.platform.client.utils.Utils;
import com.rampbot.cluster.platform.domain.*;
import lombok.extern.slf4j.Slf4j;
import scala.concurrent.duration.FiniteDuration;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;


@Slf4j
public class ServerAssistant extends UntypedActor {

    /**
     * 服务端默认配置，
     */
    private final int storeId;
    private final int companyId;
    private final String equipmentId; // 门店序列号
    private  String storeName; // 门店名称
    private final int CONNECTION_TSET_TIME = 10; // s 失联检测周期，10秒触发一次，这个值不受客户配置
    private final ActorRef clientControllerRef;
    private final ActorRef servertHeleper;
    private LogAssistant logAssistant;
    private final Integer ENERGY_CONSERVATION_RELAY_2 = 2;
    private final Integer SMALL_SIGN_LIGHT_RELAY_3 = 3;

    /**
     * 服务端使用的配置
     */
    private int isDoorMayBrokenWaitTime = 30; // 门锁状态运行最大时间，客户端配置 单位s
    private long maxTimeWithoutConnection = 1 * 60 * 1000 ; // ms 失联运行最大时间，客户配置，但是需要大于服务端的断网配置时间（40s）
    private int aotuCloseDoorTime = 8; // 自动关门时间 单位s
    private int aotuPlayVoiceSecondTimes = 120; // 开启无人值守后，第二次自动播放迎宾语的间隔时间 单位s
    private int aotuPlayVoiceSecondNum = 12; // 开启无人值守后，第二次自动播放迎宾语的编号
    private int aotuPlayVoiceFristTimes = 3; // 开启无人值守后，第一次自动播放迎宾语的间隔时间 单位s
    private int aotuPlayVoiceFristNum = 1; // 开启无人值守后，第一次自动播放迎宾语的编号
    private long intervalMilliseconds = 10 * 1000; // 自动完成开关门审核时间 单位ms
    private int disableSafeOrder = 15; // 失能安防订单时间 单位 min
    private int autoDownInLightTime = 60;  // 收到关门指令后，延迟查询是否还有订单，若无，关灯，单位s
    private boolean isEnableInLightControl = false; // 有没有室内照明控制逻辑
    private boolean isEnableOutLightControl = false; // 有没有室外照明控制逻辑
    private int disableActionType = 0;  // 被禁止生效的action
    private int disableOrderType = 0;  // 禁止生成某类订单/通知 0 无禁止 1 故障订单 2 安防订单 3 求助订单 4 断电订单
    private int safeMaxPlayNum = 18; // 防播报周期时间，单位s，默认18s
    private int bodySensorDataCollectionTime = 600; //人体感应数据采集周期 s
    private int bodyDetectFilterTime = 3; // 单次人体感应滤波周期 s
    private Boolean isHasSmallSignLight = false; // 门店是否有小招牌灯

    /**
     * 服务端运行时配置，自动计算
     */
    private boolean isGetProcessingTask = true;
    private String[] currentTime; // 当前时间 2023 03 08 19 40 55 星期三
    private boolean isHasWaittingCloseOneDoorTask = false; // 宽进严出模式下，已经存在一个等待发布的进店关门，在此期间，不在生成进店关门，不在发布服务订单
    private boolean isCanHaveNewOrder = true; // 当用门内传感器触发订单时候  1 一开开门周期，只允许生成一条服务订单   2 新的开门周期后，可以出现多条服务订单
    private boolean isCanHaveSafeOrder = true; // 是否可以产生安防订单
    private boolean isFristGetServerInfo = true;
    private int safePlayNum = 0; // 安防播报间隙计数
    private boolean isGetPlayVoiceTaskBeforOneMins = false;
    private boolean isDoorMayBroken = false;
    private int isDoorClosedOneServer = 1; // 服务端记录的1门状态 进店门 1 关门  0 未关门 ,拿到任务后更新
    private int isDoorClosedTwoServer = 1; // 服务端记录的2门状态 进店门 1 关门  0 未关门 ,拿到任务后更新
    private int relay2 = 0; // 继电器共8个， 编号0 编号1 是门1 和 门2. 编号2是室内灯控控制器,编号3是控制小灯牌的
    private boolean isPoweroff = false; // 是否上报了断电， 保证一次断电只上报一次
    private boolean isFinishInit = false; // 是否完成初始化
    private boolean isCanHaveNewDoorBrokenOrder = true;
    private boolean isServiceTrust = true; // 是否开启托管模块
    private boolean isCanPlayBronkeDoorVoice = false; // 如果存在故障订单，是否运行门店播放门故障提示
    private boolean isCanDownloadVoice = false; //
    private boolean isCanPlayInScanCodeGuidance = true; // 是否可以播放两边扫码指引
    private int last_is_door_real_close_two = 0;
    private int last_is_door_real_close_one = 0;
    private int out_human_detected_count = 0;
    private int in_human_detected_count = 0;
    private int out_human_detected_count_actual = 0; // 外到内
    private int in_human_detected_count_actual = 0;// 内到外
    private boolean is_during_in_detect = false; // 是否已经开启了检测
    private boolean is_during_out_detect = false;
    private boolean is_can_count_during_in_detect = true; // 确保一次检测周期，只能计数一次
    private boolean is_can_count_during_out_detect = true;
    private int last_is_poweroff = 0;


    /**
     * 服务端更新状态
     */
    private int isWrzsServer = 1; // 服务端记录的无人值守状态 1 开启， 0未开启


    /**
     * 客户端使用配置
     */
    private int storeMode = 0; // 门店模式(0 严进严出、1 宽进宽出、2 宽进严出、3 严进宽出)
    private int orderTriggeredMode = 1; // 宽进严出模式下 订单触发模式（0 门外传感器触发，1 门内传感器触发）
    private int inVolume = 22; // 室内音量 默认30
    private int outVolume = 22; // 室内音量 默认30




    /**
     * 客户端更新状态
     */
    private int firmwareVersion = 0; // 固件版本号
    private long lastTimeGetClientMsg; // 最后获取心跳时间























    public ServerAssistant(final ActorRef clientControllerRef, final int storeId, final int companyId, final String equipmentId, ActorRef servertHeleper, LogAssistant logAssistant) {
        this.storeId = storeId;
        this.clientControllerRef = clientControllerRef;
        this.companyId = companyId;
        this.equipmentId = equipmentId;
        this.servertHeleper = servertHeleper;
        this.logAssistant = logAssistant;
        log.info("门店{}生成服务端", equipmentId);
    }


    @Override
    public void onReceive(Object o) {
        if(o instanceof  GetServerInfoTimeout){
            this.getServerInfo();
            this.getServerInfoTimeout();
        }else if(o instanceof NoteServerClientStatus){
            NoteServerClientStatus updateMsg = (NoteServerClientStatus) o;
            this.updateServerInfo(updateMsg);
        }else if(o instanceof NoteAutoCloseDoor){
            NoteAutoCloseDoor noteAutoCloseDoor = (NoteAutoCloseDoor) o;
            this.processNoteAutoCloseDoor(noteAutoCloseDoor);
        }else if(o instanceof NoteServerTaskStatus){
            NoteServerTaskStatus noteServerTaskStatus = (NoteServerTaskStatus) o;
            this.processNoteServerTaskStatus(noteServerTaskStatus);
        }else if(o instanceof NoteServerVoiceTaskStatus){
            NoteServerVoiceTaskStatus noteServerVoiceTaskStatus = (NoteServerVoiceTaskStatus) o;
            this.processNoteServerVoiceTaskStatus(noteServerVoiceTaskStatus);
        }else if(o instanceof LockGetPlayVoiceTimeout){
            LockGetPlayVoiceTimeout lockGetPlayVoiceTimeout = (LockGetPlayVoiceTimeout) o;
            this.isGetPlayVoiceTaskBeforOneMins = false; // 解锁
        }else if(o instanceof NoteDoorMayBrokenTimeout){
            NoteDoorMayBrokenTimeout noteDoorMayBrokenTimeout = (NoteDoorMayBrokenTimeout) o;
            this.NoteDoorMayBrokenTimeoutprocess(noteDoorMayBrokenTimeout);
        }else if(o instanceof NoteConnectTestTimeout){
            this.connectionTest();
        }else if(o instanceof NoteServerUpdateHeartBeatTime){
            this.processNoteServerUpdateHeartBeatTime();
        }else if(o instanceof NoteAbleSafeOrder){
            log.info("门店{}允许产生安防订单", this.equipmentId);
            logAssistant.addLog( "server", "N 允许产生安防订单" );
            this.isCanHaveSafeOrder = true;
        }else if(o instanceof NotePlayVoiceTimeout){
            NotePlayVoiceTimeout notePlayVoiceTimeout = (NotePlayVoiceTimeout) o;
            if(this.isWrzsServer == 1){
                log.info("门店{}播放延迟触发语音{}", this.equipmentId, o);
                this.processNotePlayVoiceTimeout(notePlayVoiceTimeout);
            }


        }else if(o instanceof NoteDownInLightTimeout){
            log.info("门店{}收到延迟关灯查询请求", this.equipmentId);
            NoteDownInLightTimeout noteDownInLightTimeout = (NoteDownInLightTimeout) o;
            this.NoteDoenInLightTimeoutProcess();

        }else if(o instanceof NoteAbleBrokenDoorDetect){
            log.info("门店{}收到允许生成故障订单", this.equipmentId);
            NoteAbleBrokenDoorDetect noteDownInLightTimeout = (NoteAbleBrokenDoorDetect) o;
            this.isCanHaveNewDoorBrokenOrder = true;

        }else if(o instanceof NoteAblePlayVoiceTimeout){
            log.info("门店{}收到允许下载音频", this.equipmentId);
            logAssistant.addLog( "server", "N 允许下载音频" );
            NoteAblePlayVoiceTimeout noteAblePlayVoiceTimeout = (NoteAblePlayVoiceTimeout) o;
            this.isCanDownloadVoice = true;
        }else if(o instanceof NotePlayScanCodeGuidanceTimeout){
            this.isCanPlayInScanCodeGuidance = true;
        }else if(o instanceof NoteServerAssistantReloadConfig){
            log.info("门店{}收到来自客户端重新加载设备请求", this.equipmentId);
            this.reloadConfigOnce();
        }else if(o instanceof NoteLight){
            if(this.isWrzsServer == 1 && this.isEnableInLightControl){
                log.info("门店{}根据订单状态，主动开灯", this.equipmentId);
                logAssistant.addLog("N", "根据订单状态，主动开灯");
                this.processLightStatus(ENERGY_CONSERVATION_RELAY_2, "开");
            }
        }else if(o instanceof NoteDown){
            if(this.isWrzsServer == 1 && this.isEnableInLightControl){
                log.info("门店{}根据订单状态，主动关灯", this.equipmentId);
                logAssistant.addLog("N", "根据订单状态，主动关灯");
                this.processLightStatus(ENERGY_CONSERVATION_RELAY_2, "关");
            }
        }else if(o instanceof NoteCheckBodySensorTimeout){
            // 上报数据
            DBHelper.updateSensor(this.companyId, this.storeId, "室内人体感应", 1, this.in_human_detected_count);
            DBHelper.updateSensor(this.companyId, this.storeId, "室外人体感应", 1, this.out_human_detected_count);
            DBHelper.updateSensor(this.companyId, this.storeId, "室内人体感应滤波处理后计数", 0, this.in_human_detected_count_actual);
            DBHelper.updateSensor(this.companyId, this.storeId, "室外人体感应滤波处理后计数", 0, this.out_human_detected_count_actual);


            // 清空
            this.out_human_detected_count = 0;
            this.in_human_detected_count = 0;
            this.out_human_detected_count_actual = 0;
            this.in_human_detected_count_actual = 0;
            // 开启新的定时
            this.checkBodySensorTimeout();
        }else if(o instanceof NoteVoiceTask){
            NoteVoiceTask noteVoiceTask = (NoteVoiceTask) o;
            log.info("门店{}收到即时音频触发任务{}", this.equipmentId, noteVoiceTask);
            this.processNoteVoiceTask(noteVoiceTask);
        } else if(o instanceof NoteCheckBodyInSensorFileterTimeout){
            NoteCheckBodyInSensorFileterTimeout noteCheckBodyInSensorFileterTimeout = (NoteCheckBodyInSensorFileterTimeout) o;
            log.info("门店{}收到结束内传感器检测周期", this.equipmentId);
            this.is_during_in_detect = false; // 结束检测
            this.is_can_count_during_in_detect = true; // 允许计数
        }else if(o instanceof NoteCheckBodyOutSensorFileterTimeout){
            NoteCheckBodyOutSensorFileterTimeout noteCheckBodyOutSensorFileterTimeout = (NoteCheckBodyOutSensorFileterTimeout) o;
            log.info("门店{}收到结束外传感器检测周期", this.equipmentId);
            this.is_during_out_detect = false; // 结束检测
            this.is_can_count_during_out_detect = true; // 允许计数
        }else if(o instanceof NoteServerAssisatantDownloadVoice){
            NoteServerAssisatantDownloadVoice noteServerAssisatantDownloadVoice = (NoteServerAssisatantDownloadVoice) o;
            log.info("门店{}收到音频下载任务{}", this.equipmentId, noteServerAssisatantDownloadVoice);
            this.processNoteServerAssisatantDownloadVoice(noteServerAssisatantDownloadVoice);

        }else if(o instanceof NoteServerAssistantUpdateConfig){
            NoteServerAssistantUpdateConfig noteServerAssistantUpdateConfig = (NoteServerAssistantUpdateConfig) o;
            log.info("门店{}收到重新加载设备配置一次{}", this.equipmentId, noteServerAssistantUpdateConfig);
            this.processNoteServerAssistantUpdateConfig(noteServerAssistantUpdateConfig);
        }else if(o instanceof NoteServerAssistantIotTask){
            NoteServerAssistantIotTask noteServerAssistantIotTask = (NoteServerAssistantIotTask) o;
            log.info("门店{}收到iot任务{}", this.equipmentId, noteServerAssistantIotTask);
            this.processNoteServerAssistantIotTask(noteServerAssistantIotTask);
        }








    }

    public void postStop(){
        DBHelper.updateVoiceTask2Pending(this.storeId, this.companyId);
        DBHelper.updateIotask2Complete(this.storeId, this.companyId);


//        DBHelper.addNotifyV2(this.storeId, this.companyId, "失联", this.storeName);
        this.servertHeleper.tell(NoteCheckDisconnect.builder()
                .companyId(this.companyId)
                .storeId(this.storeId)
                .storeName(this.storeName)
                .equipmentId(this.equipmentId)
                .nextChackTimeout(60).build(), this.getSelf());
//        log.info("门店{}服务端{} 停止任务，并增加失联订单",this.equipmentId, this.getSelf());
    }

    public void preStart() {
        this.initConfig();
        this.lastTimeGetClientMsg = System.currentTimeMillis();
//        this.getServerConfig();
        this.getServerInfo();
        this.isFristGetServerInfo = false;
        this.isGetProcessingTask = false; // 第一次启动也获取状态为1的任务,之后都不获取
        this.connectionTest();
        this.getServerInfoTimeout();
        this.register2ServerHelper();
        this.checkBodySensorTimeout();


        //this.storeMode = Utils.convertToInt(DBHelper.getStoreMap(this.storeId, this.companyId).get("mode"), 0);

    }

    /**
     * 处理门店下载音频任务
     */
    private void processNoteServerAssisatantDownloadVoice(NoteServerAssisatantDownloadVoice data){
        int downloadPlace = data.getDownloadPlace();
        int voiceId= data.getVoiceId();
        if(this.isWrzsServer != 1 && !this.isFristGetServerInfo && this.firmwareVersion != 4 && this.firmwareVersion != 5 && this.firmwareVersion != 0 && this.isCanDownloadVoice){ // //  测试代码，需要恢复 临时取消三分钟禁止
            this.clientControllerRef.tell(NoteClientControllerDownloadVoice.builder()
                    .downloadPlace(downloadPlace)
                    .voiceId(voiceId)
                    .build(), this.getSelf());
        }else{
            log.info("门店{}当前不支持下载，失败下载任务，值守状态{}， 是否处于取消值守后禁止下载期间{}", this.equipmentId, this.isWrzsServer, this.isCanDownloadVoice);
            String failedReason = this.isWrzsServer == 1 ? "门店处于值守状态中，失败此次下载, 请取消托管三分钟后在尝试" : "门店处于取消值守后，禁止下载音频三分钟内，失败此次下载，请稍后尝试";
            DBHelper.updateVoiceTask(storeId, companyId, voiceId, downloadPlace, -1, failedReason);
        }
    }
//    /**
//     * 首次启动获取一些服务端的配置
//     */
//    private void getServerConfig(){
//        this.intervalMilliseconds = Utils.convertToLong(DBHelper.getConfigValue(104, this.companyId), 10000);
//    }

    /**
     * 更新服务端心跳时间
     */
    private void processNoteServerUpdateHeartBeatTime(){
        this.lastTimeGetClientMsg = System.currentTimeMillis();
        DBHelper.updateStoreLastHeartBeatTime(this.storeId, this.companyId);
    }


    /**
     * 播放延迟播放的语音
     */
    private void processNotePlayVoiceTimeout(NotePlayVoiceTimeout notePlayVoiceTimeout){
        List<Task> playVoiceTask = new LinkedList<>();
        Map<String, Object> mapPlayVoice = new HashMap<>();
        mapPlayVoice.put("event", 705);
        mapPlayVoice.put("update_voice_name", notePlayVoiceTimeout.getVoiceId());
        mapPlayVoice.put("play_count", notePlayVoiceTimeout.getPlayTimes());
        mapPlayVoice.put("volume", notePlayVoiceTimeout.getVolume());
        mapPlayVoice.put("box_index", notePlayVoiceTimeout.getPlayer());
        mapPlayVoice.put("interval", 0);
        playVoiceTask.add(Task.builder()
                .task(mapPlayVoice)
                .taskStatus(TaskStatus.pending)
                .build());

        NoteControllerTask noteControllerTask = NoteControllerTask.builder()
                .playVoiceTask(playVoiceTask)
                .build();
        this.clientControllerRef.tell(noteControllerTask, this.getSelf());
    }


    /**
     * 给clientcontroller 发送播放音频任务
     * @param name
     * @param times
     * @param volume
     * @param player
     */
    private void noteControllerPlayVoice(int name, int times, int volume, int player){
        List<Task> playVoiceTask = new LinkedList<>();
        Map<String, Object> mapPlayVoice = new HashMap<>();
        mapPlayVoice.put("event", 705);
        mapPlayVoice.put("update_voice_name", name);
        mapPlayVoice.put("play_count", times);
        mapPlayVoice.put("volume", volume);
        mapPlayVoice.put("box_index", player);
        mapPlayVoice.put("interval", 0);
        playVoiceTask.add(Task.builder()
                .task(mapPlayVoice)
                .taskStatus(TaskStatus.pending)
                .build());

        NoteControllerTask noteControllerTask = NoteControllerTask.builder()
                .playVoiceTask(playVoiceTask)
                .build();
        this.clientControllerRef.tell(noteControllerTask, this.getSelf());
    }

    /**
     * 失联订单检测
     */
    private void connectionTest(){
        long now = System.currentTimeMillis();
        if(now - this.lastTimeGetClientMsg >= maxTimeWithoutConnection){
            log.info("门店{}超过{}ms没有上报心跳，生成失联订单，并销毁服务该门店的actor", this.equipmentId, maxTimeWithoutConnection);
            //DBHelper.addNotifyV2(this.storeId, this.companyId, "失联", this.storeName);
            String title = "";
            String content = "";
            if(this.isWrzsServer == 1){
                title = "【第一次失联】新消息通知";
                content = this.storeName +  ": 已开启无人值守，第一次失联消息通知";
            }else{
                title = "【第一次失联】新消息通知";
                content = this.storeName +  ": 未开启无人值守，第一次失联消息通知";
            }
            DBHelper.addNotifyV3(this.storeId, this.companyId, "失联", this.storeName, title, content);
            DBHelper.setConfigStatus(this.companyId, this.storeId, "失联");
            this.clientControllerRef.tell(NoteClientStop.builder().build(), this.getSelf());
        }

        if(now - this.lastTimeGetClientMsg >= 2 * maxTimeWithoutConnection){
            log.info("门店{}超过{}ms没有上报心跳，此为经过两次自我销毁未成功，所以自毁吧", this.equipmentId, 2 * maxTimeWithoutConnection);
            this.getContext().stop(getSelf());
        }

        this.context().system().scheduler().scheduleOnce(
                FiniteDuration.apply(CONNECTION_TSET_TIME, TimeUnit.SECONDS),
                this.getSelf(),
                NoteConnectTestTimeout.builder().build(),
                this.context().dispatcher(),
                this.getSelf());
    }

    /**
     * 判断是否生成故障订单
     * @param noteDoorMayBrokenTimeout
     */
    private void NoteDoorMayBrokenTimeoutprocess(NoteDoorMayBrokenTimeout noteDoorMayBrokenTimeout){
        if(this.isDoorMayBroken){
            this.isDoorMayBroken = false; // 允许触发新的监控周期
            log.info("门店{}经过{}故障监视周期，门锁状态依旧为恢复，生成故障订单", this.equipmentId, isDoorMayBrokenWaitTime);
            DBHelper.addMusOrders(storeId, this.companyId, "故障",  false, this.storeName);
        }
    }


    /**
     * 更新音频任务
     * @param noteServerVoiceTaskStatus
     */
    private void processNoteServerVoiceTaskStatus(NoteServerVoiceTaskStatus noteServerVoiceTaskStatus){
        DBHelper.updateVoiceTask(this.storeId, this.companyId, noteServerVoiceTaskStatus.getVoiceTask().getVoiceId(),
                noteServerVoiceTaskStatus.getVoiceTask().getDownloadPlace(), noteServerVoiceTaskStatus.getStatus(), noteServerVoiceTaskStatus.getFailedReason());
    }

    /**
     * 处理任务状态更新
     * @param noteServerTaskStatus
     */
    private void processNoteServerTaskStatus(NoteServerTaskStatus noteServerTaskStatus){
        log.info("门店{}收到更新任务状态 {}",this.equipmentId, noteServerTaskStatus);
        Task updateTask = noteServerTaskStatus.getTask();
        if(updateTask.getTaskStatus().equals(TaskStatus.completed)){
            DBHelper.updateIotTaskById(updateTask.getTaskId(), 2, this.companyId, this.storeId); // 更新iot状态

            // 更新门店端记录的门的状态
            int actionType = Utils.convertToInt(updateTask.getTask().get("action_type"), -1);
            if(actionType == 901 || actionType == 902 || actionType == 903 || actionType == 904 || actionType == 905 || actionType == 906 ){
                int updateDoorStatus = this.getDoorUpdateStatus(actionType);
                if(updateDoorStatus != -1){
                    DBHelper.updateStoreDoorStatus(this.storeId, updateDoorStatus, this.companyId);
                }

            }


        }
    }

    /**
     * 自动关门：自动生成关门任务
     * @param noteAutoCloseDoor
     */
    private void processNoteAutoCloseDoor(NoteAutoCloseDoor noteAutoCloseDoor){
        log.info("门店{}收到自动关门{}",this.equipmentId, noteAutoCloseDoor);
        logAssistant.addLog( "server", "N 收到自动关门");
        int actionType = noteAutoCloseDoor.getActionType();
        DBHelper.addIotTasksWithoutProcessingOrPending(storeId, actionType, 0, this.companyId);

        this.isHasWaittingCloseOneDoorTask = false;
        this.isCanHaveNewOrder = true;
    }

    /**
     * 开启定时（实时：1s/次）获取数据库信息
     */
    private void getServerInfoTimeout(){
        this.context().system().scheduler().scheduleOnce(
                FiniteDuration.apply(1, TimeUnit.SECONDS),
                this.getSelf(),
                GetServerInfoTimeout.builder().build(),
                this.context().dispatcher(),
                this.getSelf());
    }


    /**
     * 开启定时 获取人体感应传感器数据
     */
    private void checkBodySensorTimeout(){
        this.context().system().scheduler().scheduleOnce(
                FiniteDuration.apply(this.bodySensorDataCollectionTime, TimeUnit.SECONDS),
                this.getSelf(),
                NoteCheckBodySensorTimeout.builder().build(),
                this.context().dispatcher(),
                this.getSelf());
    }



    /**
     * 获取、更新数据库数据
     */
    private void getServerInfo(){
        /*
        1 获取值守状态
        2 获取iot任务
        3 获取音频下载任务
        4 获取音频播放任务
         */

        List<Task> setWrzsStatusTask = new LinkedList<>(); //
        List<Task> doorTask = new LinkedList<>(); //
        List<VoiceTask> downloadVoiceTask = new LinkedList<>(); //
        List<Task> playVoiceTask = new LinkedList<>(); //
        List<Task> noResponseTask = new LinkedList<>(); //不需要客户端反馈的任务

        /**
         * 获取状态更新
         */
        Map<String, Object> storeConfig = DBHelper.getStoreMap(this.storeId, this.companyId);
        // 获取值守状态  1 正常营业 、 2 停业 、 3 远程值守中
        if(Utils.convertToInt(storeConfig.get("status"), -1) == 3){
            // TODO: 2023/1/13  增加开启无人值守后 一段时间内不产生安放订单
            if(this.isWrzsServer == 0 && this.isCanHaveSafeOrder){
                this.isCanHaveSafeOrder = false;

                log.info("门店{}开启托管，禁止产生安防订单", this.equipmentId);
                logAssistant.addLog( "server", "N 开启托管，禁止产生安防订单");
                this.context().system().scheduler().scheduleOnce(
                        FiniteDuration.apply(disableSafeOrder, TimeUnit.MINUTES),
                        this.getSelf(),
                        NoteAbleSafeOrder.builder().build(),
                        this.context().dispatcher(),
                        this.getSelf());
            }
            this.isWrzsServer = 1;
        }else{

            if(this.isWrzsServer == 1){
                log.info("门店{}取消托管, 禁止音频下载3分钟", this.equipmentId);
                this.isCanDownloadVoice = false;
                logAssistant.addLog( "server", "N 取消托管后禁止3分钟之内下载音频");
                this.context().system().scheduler().scheduleOnce(
                        FiniteDuration.apply(3, TimeUnit.MINUTES),
                        this.getSelf(),
                        NoteAblePlayVoiceTimeout.builder().build(),
                        this.context().dispatcher(),
                        this.getSelf());
            }

            this.isWrzsServer = 0;

            if(isFristGetServerInfo){
                this.isDoorClosedTwoServer = 0;
                this.isDoorClosedOneServer = 0;
            }
        }

        //this.aotuCloseDoorTime = Utils.convertToInt(storeConfig.get("open_seconds"), 8);


        /**
         * 先判断是否有需要自动完成的任务
         */
        //this.autoOpenDoor();

        /**
         * 处理iot任务
         */
//        List<Map<String, Object>> taskList =  DBHelper.getPendingIotTask(this.storeId, this.companyId, this.isGetProcessingTask);
//        if(taskList != null && taskList.size() > 0){
//            log.info("门店{}从服务端获取的iot门任务 {}",this.equipmentId, taskList);
//            for(int i = 0; i < taskList.size(); i++){
//                Map<String, Object> task = taskList.get(i);
//                int actionType = Integer.parseInt(String.valueOf(task.get("action_type")));
//                long taskId = Long.parseLong(String.valueOf(task.get("id")));
//                if(this.disableActionType == actionType){
//                    log.info("门店{}获取到任务{}，被禁止执行！",this.equipmentId, this.disableActionType);
//                    DBHelper.updateIotTaskById(taskId, -2, this.companyId, this.storeId); // 被禁止执行的任务类型，不参与后续逻辑
//                }else{
//
//                    if(actionType == 907){
//                        log.info("门店{}收到一次立即关门，取消自动审核", this.equipmentId);
//                        actionType = 902;
//                        task.remove("action_type");
//                        task.put("action_type", 902);
//                        DBHelper.cancelAutoOpenDoor(this.companyId,this.storeId);
//                    }
//
//                    DBHelper.updateIotTaskById(taskId, 1, this.companyId, this.storeId); // 所有拿到的任务都更新未状态1，保证任务只拿一次
//                    this.setDoorLogicStatus(actionType); // 更新门的逻辑状态
//                    Task pendingTask = Task.builder()
//                            .taskId(taskId)
//                            .taskStatus(TaskStatus.pending)
//                            .task(task)
//                            .build();
//
//                    // 增加一个定时 播放店内提示
//                    if(this.isWrzsServer == 1 && this.firmwareVersion >= 4 && actionType == 903){
//                        this.context().system().scheduler().scheduleOnce(
//                                FiniteDuration.apply(this.aotuPlayVoiceSecondTimes, TimeUnit.SECONDS),
//                                this.getSelf(), NotePlayVoiceTimeout.builder()
//                                        .player(1)
//                                        .playTimes(1)
//                                        .voiceId(this.aotuPlayVoiceSecondNum)
//                                        .volume(30)
//                                        .build(),
//                                this.context().dispatcher(),
//                                this.getSelf());
//                    }
//
//                    // 由服务端主动播放迎宾语
//                    if(this.isWrzsServer == 1 && this.firmwareVersion >= 8 && actionType == 903){
//                        this.context().system().scheduler().scheduleOnce(
//                                FiniteDuration.apply(this.aotuPlayVoiceFristTimes, TimeUnit.SECONDS),
//                                this.getSelf(), NotePlayVoiceTimeout.builder()
//                                        .player(1)
//                                        .playTimes(1)
//                                        .voiceId(this.aotuPlayVoiceFristNum)
//                                        .volume(30)
//                                        .build(),
//                                this.context().dispatcher(),
//                                this.getSelf());
//                    }
//
//
//                    // 处理室内照明逻辑
//                    if(this.isEnableInLightControl){ // 继电器常闭合 给1关 给0开
//                        // 增加一个灯控 relay2 开门开启灯
////                        if(this.isWrzsServer == 1  && actionType == 903   ){
////                            // 开启了无人值守、固件版本、进店开门、使能室内灯控、继电器没有开
////                            log.info("门店{}有新订单主动开灯", this.equipmentId);
////                            logAssistant.addLog( "server", "N 有新订单主动开灯");
////                            Map<String, Object> mapRelayOpen = new HashMap<>();
////                            mapRelayOpen.put("event", 601);
////                            mapRelayOpen.put("relay", 2);
////                            mapRelayOpen.put("relay_stats",  Utils.getRelayStats("开", 2));
////                            noResponseTask.add(Task.builder()
////                                    .task(mapRelayOpen)
////                                    .taskStatus(TaskStatus.pending)
////                                    .build());
////
////                        }
////                        // 增加一个灯控 relay2 关门关闭灯
////                        if(this.isWrzsServer == 1  &&  actionType == 906 ){
////
////                            log.info("门店{}在开启无人值守情况下，收到906离店关门，开启关灯延时", this.equipmentId);
////                            this.context().system().scheduler().scheduleOnce(
////                                    FiniteDuration.apply(this.autoDownInLightTime, TimeUnit.SECONDS),
////                                    this.getSelf(),
////                                    NoteDownInLightTimeout.builder().build(),
////                                    this.context().dispatcher(),
////                                    this.getSelf());
////
////                        }
//
//
//                        // 增加一个灯控 relay2 开启托管关灯
//                        if( actionType == 801 ){
//                            log.info("门店{}开启托管主动关灯", this.equipmentId);
//                            logAssistant.addLog( "server", "N 开启托管主动关灯");
//                            Map<String, Object> mapRelayOpen = new HashMap<>();
//                            mapRelayOpen.put("event", 601);
//                            mapRelayOpen.put("relay", ENERGY_CONSERVATION_RELAY_2);
//                            mapRelayOpen.put("relay_stats", Utils.getRelayStats("关", ENERGY_CONSERVATION_RELAY_2));
//                            noResponseTask.add(Task.builder()
//                                    .task(mapRelayOpen)
//                                    .taskStatus(TaskStatus.pending)
//                                    .build());
//
//                        }
//
//                        // 增加一个灯控 relay2 关闭托管开灯
//                        if( actionType == 802 ){
//                            log.info("门店{}关闭托管主动开灯", this.equipmentId);
//                            logAssistant.addLog( "server", "N 关闭托管主动开灯");
//                            Map<String, Object> mapRelayOpen = new HashMap<>();
//                            mapRelayOpen.put("event", 601);
//                            mapRelayOpen.put("relay", ENERGY_CONSERVATION_RELAY_2);
//                            mapRelayOpen.put("relay_stats", Utils.getRelayStats("开", ENERGY_CONSERVATION_RELAY_2));
//                            noResponseTask.add(Task.builder()
//                                    .task(mapRelayOpen)
//                                    .taskStatus(TaskStatus.pending)
//                                    .build());
//
//                        }
//                    }
//
//                    // 处理小灯牌逻辑
//                    if(this.isHasSmallSignLight){
//                        if( actionType == 801 ){
//                            log.info("门店{}开启托管打开小灯牌", this.equipmentId);
//                            logAssistant.addLog( "server", "N 开启托管自动打开小灯牌");
//                            Map<String, Object> mapRelayOpen3 = new HashMap<>();
//                            mapRelayOpen3.put("event", 601);
//                            mapRelayOpen3.put("relay", SMALL_SIGN_LIGHT_RELAY_3);
//                            mapRelayOpen3.put("relay_stats", Utils.getRelayStats("开", SMALL_SIGN_LIGHT_RELAY_3));
//                            noResponseTask.add(Task.builder()
//                                    .task(mapRelayOpen3)
//                                    .taskStatus(TaskStatus.pending)
//                                    .build());
//
//                        }
//
//                        // 增加一个灯控 relay2 关闭托管开灯
//                        if( actionType == 802 ){
//                            log.info("门店{}关闭托管关闭小灯牌", this.equipmentId);
//                            logAssistant.addLog( "server", "N 关闭托管自动关闭小灯牌");
//                            Map<String, Object> mapRelayOpen = new HashMap<>();
//                            mapRelayOpen.put("event", 601);
//                            mapRelayOpen.put("relay", SMALL_SIGN_LIGHT_RELAY_3);
//                            mapRelayOpen.put("relay_stats", Utils.getRelayStats("关", SMALL_SIGN_LIGHT_RELAY_3));
//                            noResponseTask.add(Task.builder()
//                                    .task(mapRelayOpen)
//                                    .taskStatus(TaskStatus.pending)
//                                    .build());
//
//                        }
//                    }
//
//
//                    // 跟一个定时关门
//                    if((actionType == 903 || actionType == 905) && this.isWrzsServer == 1){
//                        int autpActionTpye = 0;
//                        if(actionType == 903){
//                            autpActionTpye = 904;
//                        }else {
//                            autpActionTpye = 906;
//                        }
//                        this.context().system().scheduler().scheduleOnce(
//                                FiniteDuration.apply(aotuCloseDoorTime, TimeUnit.SECONDS),
//                                this.getSelf(),
//                                NoteAutoCloseDoor.builder().actionType(autpActionTpye).build(),
//                                this.context().dispatcher(),
//                                this.getSelf());
//                    }
//
//                    // 新增任务907 表示即要全关门，又要取消一次自动审核
//                    if(actionType == 901 || actionType == 902 || actionType == 903 || actionType == 904 || actionType == 905 || actionType == 906  || actionType == 907){
//
////                        if(actionType == 907){
////                            log.info("门店{}收到一次立即关门，取消自动审核", this.equipmentId);
////                            //actionType = 902;
////                            DBHelper.cancelAutoOpenDoor(this.companyId,this.storeId);
////                        }
//
//                        doorTask.add(pendingTask);
//                        this.isCanHaveNewDoorBrokenOrder = false;// 收到iot后，失能一段时间故障检测。
//                        this.context().system().scheduler().scheduleOnce(
//                                FiniteDuration.apply(isDoorMayBrokenWaitTime, TimeUnit.SECONDS),
//                                this.getSelf(),
//                                NoteAbleBrokenDoorDetect.builder().build(),
//                                this.context().dispatcher(),
//                                this.getSelf());
//                    }else if (actionType == 801  || actionType == 802 ){
//                        setWrzsStatusTask.add(pendingTask);
//                    }
////                    // 运行播放一次关门提示
//                    if(actionType == 904){
//                        this.isCanPlayBronkeDoorVoice = true;
//                    }
//                }
//
//
//            }
//        }

        /**
         * 处理音频下载任务
         * 若开启无人值守不下载语音
         * 第一次同步服务状态不使用
         * 4 是6 不支持下载固件
         * 5 是6 不支持下载支持部分config
         */
//        if(this.isWrzsServer != 1 && !this.isFristGetServerInfo && this.firmwareVersion != 4 && this.firmwareVersion != 5 && this.firmwareVersion != 0 && this.isCanDownloadVoice){
////        if(this.isWrzsServer != 1 && !this.isFristGetServerInfo){
//            List<Map<String, Object>> pendingVoiceTask = DBHelper.getPendingVoiceTask(storeId, this.companyId);
//            if(pendingVoiceTask != null && pendingVoiceTask.size() > 0){
//                for(int i = 0; i < pendingVoiceTask.size(); i++){
//                    int version = -1;
//                    String fileUrl = null;
//                    byte[] voiceData = null;
//                    int voiceId = Utils.convertToInt(pendingVoiceTask.get(i).get("voice_id"), -1);
//                    int downloadPlace = Utils.convertToInt(pendingVoiceTask.get(i).get("sd_index"), -1);
//                    DBHelper.updateVoiceTask(this.storeId, this.companyId, downloadPlace, 1); // 更新已经触发下载的任务状态
//                    Map<String, Object> voiceMsg = DBHelper.getVoice(voiceId, this.companyId);
//                    if(voiceMsg != null && voiceMsg.size() > 0){
//                        version = Utils.convertToInt(voiceMsg.get("version"), -1);
//                        fileUrl = voiceMsg.get("file_url").toString();
//                        voiceData = Utils.getContent("C:\\Users\\work\\Desktop\\test.mp3");  //测试代码 需要恢复
////                        try {
////                            voiceData = Utils.downloadVoiceFiles(fileUrl);
////                        } catch (IOException e) {
////                            e.printStackTrace();
////                        }
//                    }
//
//                    if(voiceData == null){
//                        log.info("门店{}需要下载的音频{} 内容获取失败", this.equipmentId, voiceId);
//                        DBHelper.updateVoiceTask(this.storeId, this.companyId, downloadPlace, -2);
//                    }else {
//                        if(this.firmwareVersion >= 6){
//                            downloadVoiceTask.add(VoiceTask.builder()
//                                    .version(version)
//                                    .voiceData(voiceData)
//                                    .downloadPlace(downloadPlace)
//                                    .taskStatus(TaskStatus.pending)
//                                    .voiceId(voiceId)
//                                    .build());
//                        }else {
//                            downloadVoiceTask.add(VoiceTask.builder()
//                                    .version(version)
//                                    .voiceData(voiceData)
//                                    .downloadPlace(downloadPlace)
//                                    .taskStatus(TaskStatus.pending)
//                                    .voiceId(voiceId)
//                                    .build());
//                        }
//
//                    }
//
//
//                }
//            }
//        }

        /**
         * 处理音频播放任务
         * 注意：日期格式用'-'号分开,如配置多个则英文','逗号分开。如果只配置每月或每日则可以不填写年份或月份(例如：2022-05-01,06-01,04...) 每年06月01播放配置06-01,每月01号播放配置01
         * 注意：日期格式用中文,如配置多个则英文','逗号分开 (例如：星期六,星期天)
         * 注意：格式用英文':'分开,如配置多个时间段，则英文','逗号分开（例如：07:00,15:00）
         */
        if(!this.isFristGetServerInfo){
            List<Map<String, Object>> pendingPlayVoiceTask = DBHelper.getPlayVoiceTask(this.storeId, this.companyId);
            if(pendingPlayVoiceTask != null && pendingPlayVoiceTask.size() > 0){
//            log.info("从服务端获取的音频播放任务 {}", pendingPlayVoiceTask);
                playVoiceTask.addAll(this.getPlayVoiceTask(pendingPlayVoiceTask));
            }

            if(this.firmwareVersion >= 4){

                // 客服触发订单播报
                // TODO: 2023/6/30 改为走redis 
//                List<Map<String, Object>> pendingVoiceIotTask = DBHelper.getPendingVoiceIotTask(this.storeId, this.companyId);
//                if(pendingVoiceIotTask != null ){
//                    for(Map<String, Object> taskMap : pendingVoiceIotTask){
//                        int voiceId = Utils.convertToInt(taskMap.get("voice_id"), 1);
//                        int player = Utils.convertToInt(taskMap.get("player"), 1);
//                        long createTime = Utils.convertToLong(taskMap.get("update_time"), -1);
//                        int enableTime = Utils.convertToInt(taskMap.get("enable_time"), 5000);
//                        if(System.currentTimeMillis() - createTime > enableTime){
//                            log.info("门店{}要播放的音频{}已过期，不予以播放", this.equipmentId, voiceId);
//                            DBHelper.updateIotaskVoice2Complete(this.storeId, this.companyId, voiceId , player, -1);
//                        }else {
//                            Map<String, Object> taskMapForHelper = new HashMap<>();
//                            taskMapForHelper.put("event", 705);
//                            taskMapForHelper.put("update_voice_name", voiceId);
//                            taskMapForHelper.put("play_count", Utils.convertToInt(taskMap.get("times"), 1));
//                            taskMapForHelper.put("volume", 30);
//                            taskMapForHelper.put("box_index", player);
//                            taskMapForHelper.put("interval", 0);
//                            playVoiceTask.add(Task.builder()
//                                    .task(taskMapForHelper)
//                                    .taskStatus(TaskStatus.pending)
//                                    .build());
//                            DBHelper.updateIotaskVoice2Complete(this.storeId, this.companyId, voiceId , player, 2);
//                        }
//                    }
//                }
                // 安防订单播报
                if(this.isWrzsServer == 1 ){
                    if( RedisHelper.isExistsPendingSaftOrderV2(this.companyId, this.storeId)){
                        if(this.safePlayNum == 0){
                            log.info("门店{}存在未处理的安防订单，触发安防播报", this.equipmentId);
                            Map<String, Object> mapSafty = new HashMap<>();
                            mapSafty.put("event", 705);
                            mapSafty.put("update_voice_name", 6);
                            mapSafty.put("play_count", 1);
                            mapSafty.put("volume", 30);
                            mapSafty.put("box_index", 1);
                            mapSafty.put("interval", 0);
                            playVoiceTask.add(Task.builder()
                                    .task(mapSafty)
                                    .taskStatus(TaskStatus.pending)
                                    .build());
                        }

                        this.safePlayNum++;

                        if(this.safePlayNum == this.safeMaxPlayNum){this.safePlayNum = 0;}
                    }

                }
            }


        }

        /**
         * 5版本支持部分配置表
         */
        if(this.firmwareVersion > 0 && !this.isFinishInit){
            Map<String, Object> configs = DBHelper.getUpdateStoreconfigsOnce(this.storeId, this.companyId);
            if(configs != null){

                this.isFinishInit = true; // 初始化临时配置方案

                log.info("门店{}收到的新配置列表{}", this.equipmentId,configs);
                logAssistant.addLog( "server", "N 收到的新配置列表");

                //1 判断内外灯光控制
                this.isEnableInLightControl = Utils.convertToInt(configs.get("is_enable_in_light_control"), 0) == 1;
                this.isEnableOutLightControl = Utils.convertToInt(configs.get("is_enable_out_light_control"), 0) == 1;

                //2 判断继电器 relay_control_stats 1130:7:1_1930:7:0
                String relaysStats = Utils.convertToStr(configs.get("relay_control_stats"));
                String[] relaysStatsArray = relaysStats.split("_");
                for(String realyStats : relaysStatsArray){
                    String[] relaysStatsarray = realyStats.split(":");
                    this.getTimeCurrent();
                    String now = this.currentTime[3]  + this.currentTime[4];
                    if(relaysStatsarray.length == 3 && relaysStatsarray[0].equals(now)){
                        log.info("门店{}需要控制继电器{}状态为{}", this.equipmentId,relaysStatsarray[1], relaysStatsarray[2] );
                        Map<String, Object> mapRelay = new HashMap<>();
                        mapRelay.put("event", 601);
                        mapRelay.put("relay", Utils.convertToInt(relaysStatsarray[1], 8));
                        mapRelay.put("relay_stats", Utils.convertToInt(relaysStatsarray[2], 0));
                        noResponseTask.add(Task.builder()
                                .task(mapRelay)
                                .taskStatus(TaskStatus.pending)
                                .build());
                    }
                }
                // 判断小灯牌
                this.isHasSmallSignLight =   Utils.convertToInt(configs.get("is_has_small_sign_light"), 0) == 1;




                //3 判断内外音响音量
                int inVolumeNew = Utils.convertToInt(configs.get("in_volume"), 22);
                int outVolumeNew = Utils.convertToInt(configs.get("out_volume"), 22);
                if(this.inVolume != inVolumeNew){
                    log.info("门店{}增加室内音量配置任务，新音量为{}，原音量为{}", this.equipmentId, inVolumeNew, this.inVolume );
                    logAssistant.addLog( "server", "N 增加室内音量配置任务，新音量为" + inVolumeNew + "，原音量为" + this.inVolume);

                    this.inVolume = Utils.convertToInt(configs.get("in_volume"), 22);
                    Map<String, Object> mapInVolume= new HashMap<>();
                    mapInVolume.put("event", 707);
                    mapInVolume.put("box_index", 1);
                    mapInVolume.put("volume", this.inVolume);
                    noResponseTask.add(Task.builder()
                            .task(mapInVolume)
                            .taskStatus(TaskStatus.pending)
                            .build());

                }
                if(this.outVolume != outVolumeNew){
                    log.info("门店{}增加室外音量配置任务，新音量为{}，原音量为{}", this.equipmentId, outVolumeNew, this.outVolume );
                    logAssistant.addLog( "server", "N 增加室外音量配置任务，新音量为" + outVolumeNew + "，原音量为" + this.outVolume);
                    this.outVolume = Utils.convertToInt(configs.get("out_volume"), 22);
                    Map<String, Object> mapOutVolume = new HashMap<>();
                    mapOutVolume.put("event", 707);
                    mapOutVolume.put("box_index", 2);
                    mapOutVolume.put("volume", this.outVolume);
                    noResponseTask.add(Task.builder()
                            .task(mapOutVolume)
                            .taskStatus(TaskStatus.pending)
                            .build());
                }


                //4 判断是否需要重启
                if(Utils.convertToInt(configs.get("restart_stm"), 0) == 1){

                    DBHelper.updateConfigRestart(this.storeId, this.companyId);

                    Map<String, Object> mapRestart = new HashMap<>();
                    mapRestart.put("event", 602);
                    noResponseTask.add(Task.builder()
                            .task(mapRestart)
                            .taskStatus(TaskStatus.pending)
                            .build());
                }

                // 5 判断是否需要禁止某一类action type、 或者生成某一类订单
                this.disableActionType = Utils.convertToInt(configs.get("disable_actiong_type"), this.disableActionType);
                log.info("门店{}禁止执行的任务类型为{}", this.equipmentId, this.disableActionType);
                this.disableOrderType = Utils.convertToInt(configs.get("disable_order_type"), this.disableOrderType);
                log.info("门店{}禁止生成的订单类型为{}", this.equipmentId, this.disableOrderType);


                /**
                 *     private int IS_DOOR_MAY_BROKEN_WAIT_TIME = 30; // 门锁状态运行最大时间，客户端配置 单位s
                 *     private long MAX_TIME_WITHOUT_CONNECTION = 1 * 60 * 1000 ; // ms 失联运行最大时间，客户配置，但是需要大于服务端的断网配置时间（40s）
                 *     private int aotuCloseDoorTime = 5; // 自动关门时间 单位s
                 *     private int aotuPlayVoice = 2; // 开启无人值守后，第二次自动播放迎宾语的间隔时间 单位min
                 *     private long intervalMilliseconds = 10 * 1000; // 自动完成开关门审核时间 单位ms
                 *     private int DISABLE_SAFE_ORDER = 15; // 失能安防订单时间 单位 min
                 *     private int autoDownInLightTime = 60;  // 收到关门指令后，延迟查询是否还有订单，若无，关灯，单位s
                 *     safty_alarm_voice_play_interval_secons this.safeMaxPlayNum
                 */

                // 6 一些系统使用的时间判断
                this.isDoorMayBrokenWaitTime = Utils.convertToInt(configs.get("door_may_broken_wait_secons"), this.isDoorMayBrokenWaitTime);
                this.aotuPlayVoiceSecondTimes = Utils.convertToInt(configs.get("open_seconds_welcome_second"), this.aotuPlayVoiceSecondTimes);
                this.aotuPlayVoiceSecondNum = Utils.convertToInt(configs.get("open_seconds_welcome_second_num"), this.aotuPlayVoiceSecondNum);
                this.aotuPlayVoiceFristTimes = Utils.convertToInt(configs.get("open_seconds_welcome_frist"), this.aotuPlayVoiceFristTimes);
                this.aotuPlayVoiceFristNum = Utils.convertToInt(configs.get("open_seconds_welcome_frist_num"), this.aotuPlayVoiceFristNum);
                this.disableSafeOrder = Utils.convertToInt(configs.get("disable_safty_order_mins"), this.disableSafeOrder);
                this.autoDownInLightTime = Utils.convertToInt(configs.get("in_light_turn_off_time"), this.autoDownInLightTime);
                this.safeMaxPlayNum = Utils.convertToInt(configs.get("safty_alarm_voice_play_interval_secons"), this.safeMaxPlayNum);
                this.aotuCloseDoorTime = Utils.convertToInt(configs.get("open_seconds"), this.aotuCloseDoorTime);
                this.intervalMilliseconds = Utils.convertToLong(configs.get("interval_milliseconds"), this.intervalMilliseconds);

                ConfigHelper.setStoreConfig(this.companyId, this.storeId, "interval_milliseconds", this.intervalMilliseconds);
                this.bodySensorDataCollectionTime = Utils.convertToInt(configs.get("body_sensor_data_collection_time"), this.bodySensorDataCollectionTime);
                this.bodyDetectFilterTime = Utils.convertToInt(configs.get("body_detect_filter_time"), this.bodyDetectFilterTime);


                // 判断宽进严出的门店触发订单模式
                this.orderTriggeredMode = Utils.convertToInt(configs.get("order_triggered_mode"), 1);
            }
        }


        // 通知controller新的任务
        if(doorTask.size() > 0 || setWrzsStatusTask.size() > 0 || downloadVoiceTask.size() > 0 || playVoiceTask.size() > 0 || noResponseTask.size() > 0){
            NoteControllerTask noteControllerTask = NoteControllerTask.builder()
                    .doorTask(doorTask)
                    .setWrzsStatusTask(setWrzsStatusTask)
                    .downloadVoiceTask(downloadVoiceTask)
                    .playVoiceTask(playVoiceTask)
                    .noResponseTask(noResponseTask)
                    .build();
            this.clientControllerRef.tell(noteControllerTask, this.getSelf());
        }



    }

    /**
     * 重新加载一次设备，目前包括音量
     */
    private void reloadConfigOnce(){

        Map<String, Object> configs = DBHelper.getUpdateStoreconfigs(this.storeId, this.companyId, false);

        if(configs != null){
            this.inVolume = Utils.convertToInt(configs.get("in_volume"), 22);
            this.outVolume = Utils.convertToInt(configs.get("out_volume"), 22);

            List<Task> setWrzsStatusTask = new LinkedList<>(); //
            List<Task> doorTask = new LinkedList<>(); //
            List<VoiceTask> downloadVoiceTask = new LinkedList<>(); //
            List<Task> playVoiceTask = new LinkedList<>(); //
            List<Task> noResponseTask = new LinkedList<>(); //不需要客户端反馈的任务

            Map<String, Object> mapInVolume= new HashMap<>();
            mapInVolume.put("event", 707);
            mapInVolume.put("box_index", 1);
            mapInVolume.put("volume", this.inVolume);
            noResponseTask.add(Task.builder()
                    .task(mapInVolume)
                    .taskStatus(TaskStatus.pending)
                    .build());



            Map<String, Object> mapOutVolume = new HashMap<>();
            mapOutVolume.put("event", 707);
            mapOutVolume.put("box_index", 2);
            mapOutVolume.put("volume", this.outVolume);
            noResponseTask.add(Task.builder()
                    .task(mapOutVolume)
                    .taskStatus(TaskStatus.pending)
                    .build());


            // 通知controller新的任务
            if(doorTask.size() > 0 || setWrzsStatusTask.size() > 0 || downloadVoiceTask.size() > 0 || playVoiceTask.size() > 0 || noResponseTask.size() > 0){
                NoteControllerTask noteControllerTask = NoteControllerTask.builder()
                        .doorTask(doorTask)
                        .setWrzsStatusTask(setWrzsStatusTask)
                        .downloadVoiceTask(downloadVoiceTask)
                        .playVoiceTask(playVoiceTask)
                        .noResponseTask(noResponseTask)
                        .build();
                this.clientControllerRef.tell(noteControllerTask, this.getSelf());
            }


        }

    }

    private void processNoteServerAssistantUpdateConfig(NoteServerAssistantUpdateConfig noteServerAssistantUpdateConfig){
        if(this.firmwareVersion > 0){
            Map<String, Object> configs = DBHelper.getUpdateStoreconfigsOnce(this.storeId, this.companyId);
            if(configs != null){

                List<Task> noResponseTask = new LinkedList<>(); //不需要客户端反馈的任务
                Map<String, Object> mapRelayOpen = new HashMap<>();



                this.isFinishInit = true; // 初始化临时配置方案

                log.info("门店{}收到的新配置列表{}", this.equipmentId,configs);
                logAssistant.addLog( "server", "N 收到的新配置列表");

                //1 判断内外灯光控制
                this.isEnableInLightControl = Utils.convertToInt(configs.get("is_enable_in_light_control"), 0) == 1;
                this.isEnableOutLightControl = Utils.convertToInt(configs.get("is_enable_out_light_control"), 0) == 1;

                //2 判断继电器 relay_control_stats 1130:7:1_1930:7:0
                String relaysStats = Utils.convertToStr(configs.get("relay_control_stats"));
                String[] relaysStatsArray = relaysStats.split("_");
                for(String realyStats : relaysStatsArray){
                    String[] relaysStatsarray = realyStats.split(":");
                    this.getTimeCurrent();
                    String now = this.currentTime[3]  + this.currentTime[4];
                    if(relaysStatsarray.length == 3 && relaysStatsarray[0].equals(now)){
                        log.info("门店{}需要控制继电器{}状态为{}", this.equipmentId,relaysStatsarray[1], relaysStatsarray[2] );
                        Map<String, Object> mapRelay = new HashMap<>();
                        mapRelay.put("event", 601);
                        mapRelay.put("relay", Utils.convertToInt(relaysStatsarray[1], 8));
                        mapRelay.put("relay_stats", Utils.convertToInt(relaysStatsarray[2], 0));
                        noResponseTask.add(Task.builder()
                                .task(mapRelay)
                                .taskStatus(TaskStatus.pending)
                                .build());
                    }
                }
                // 判断小灯牌
                this.isHasSmallSignLight =   Utils.convertToInt(configs.get("is_has_small_sign_light"), 0) == 1;




                //3 判断内外音响音量
                int inVolumeNew = Utils.convertToInt(configs.get("in_volume"), 22);
                int outVolumeNew = Utils.convertToInt(configs.get("out_volume"), 22);
                if(this.inVolume != inVolumeNew){
                    log.info("门店{}增加室内音量配置任务，新音量为{}，原音量为{}", this.equipmentId, inVolumeNew, this.inVolume );
                    logAssistant.addLog( "server", "N 增加室内音量配置任务，新音量为" + inVolumeNew + "，原音量为" + this.inVolume);

                    this.inVolume = Utils.convertToInt(configs.get("in_volume"), 22);
                    Map<String, Object> mapInVolume= new HashMap<>();
                    mapInVolume.put("event", 707);
                    mapInVolume.put("box_index", 1);
                    mapInVolume.put("volume", this.inVolume);
                    noResponseTask.add(Task.builder()
                            .task(mapInVolume)
                            .taskStatus(TaskStatus.pending)
                            .build());

                }
                if(this.outVolume != outVolumeNew){
                    log.info("门店{}增加室外音量配置任务，新音量为{}，原音量为{}", this.equipmentId, outVolumeNew, this.outVolume );
                    logAssistant.addLog( "server", "N 增加室外音量配置任务，新音量为" + outVolumeNew + "，原音量为" + this.outVolume);
                    this.outVolume = Utils.convertToInt(configs.get("out_volume"), 22);
                    Map<String, Object> mapOutVolume = new HashMap<>();
                    mapOutVolume.put("event", 707);
                    mapOutVolume.put("box_index", 2);
                    mapOutVolume.put("volume", this.outVolume);
                    noResponseTask.add(Task.builder()
                            .task(mapOutVolume)
                            .taskStatus(TaskStatus.pending)
                            .build());
                }


                //4 判断是否需要重启
                if(Utils.convertToInt(configs.get("restart_stm"), 0) == 1){

                    DBHelper.updateConfigRestart(this.storeId, this.companyId);

                    Map<String, Object> mapRestart = new HashMap<>();
                    mapRestart.put("event", 602);
                    noResponseTask.add(Task.builder()
                            .task(mapRestart)
                            .taskStatus(TaskStatus.pending)
                            .build());
                }

                // 5 判断是否需要禁止某一类action type、 或者生成某一类订单
                this.disableActionType = Utils.convertToInt(configs.get("disable_actiong_type"), this.disableActionType);
                log.info("门店{}禁止执行的任务类型为{}", this.equipmentId, this.disableActionType);
                this.disableOrderType = Utils.convertToInt(configs.get("disable_order_type"), this.disableOrderType);
                log.info("门店{}禁止生成的订单类型为{}", this.equipmentId, this.disableOrderType);


                /**
                 *     private int IS_DOOR_MAY_BROKEN_WAIT_TIME = 30; // 门锁状态运行最大时间，客户端配置 单位s
                 *     private long MAX_TIME_WITHOUT_CONNECTION = 1 * 60 * 1000 ; // ms 失联运行最大时间，客户配置，但是需要大于服务端的断网配置时间（40s）
                 *     private int aotuCloseDoorTime = 5; // 自动关门时间 单位s
                 *     private int aotuPlayVoice = 2; // 开启无人值守后，第二次自动播放迎宾语的间隔时间 单位min
                 *     private long intervalMilliseconds = 10 * 1000; // 自动完成开关门审核时间 单位ms
                 *     private int DISABLE_SAFE_ORDER = 15; // 失能安防订单时间 单位 min
                 *     private int autoDownInLightTime = 60;  // 收到关门指令后，延迟查询是否还有订单，若无，关灯，单位s
                 *     safty_alarm_voice_play_interval_secons this.safeMaxPlayNum
                 */

                // 6 一些系统使用的时间判断
                this.isDoorMayBrokenWaitTime = Utils.convertToInt(configs.get("door_may_broken_wait_secons"), this.isDoorMayBrokenWaitTime);
                this.aotuPlayVoiceSecondTimes = Utils.convertToInt(configs.get("open_seconds_welcome_second"), this.aotuPlayVoiceSecondTimes);
                this.aotuPlayVoiceSecondNum = Utils.convertToInt(configs.get("open_seconds_welcome_second_num"), this.aotuPlayVoiceSecondNum);
                this.aotuPlayVoiceFristTimes = Utils.convertToInt(configs.get("open_seconds_welcome_frist"), this.aotuPlayVoiceFristTimes);
                this.aotuPlayVoiceFristNum = Utils.convertToInt(configs.get("open_seconds_welcome_frist_num"), this.aotuPlayVoiceFristNum);
                this.disableSafeOrder = Utils.convertToInt(configs.get("disable_safty_order_mins"), this.disableSafeOrder);
                this.autoDownInLightTime = Utils.convertToInt(configs.get("in_light_turn_off_time"), this.autoDownInLightTime);
                this.safeMaxPlayNum = Utils.convertToInt(configs.get("safty_alarm_voice_play_interval_secons"), this.safeMaxPlayNum);
                this.aotuCloseDoorTime = Utils.convertToInt(configs.get("open_seconds"), this.aotuCloseDoorTime);
                this.intervalMilliseconds = Utils.convertToLong(configs.get("interval_milliseconds"), this.intervalMilliseconds);

                ConfigHelper.setStoreConfig(this.companyId, this.storeId, "interval_milliseconds", this.intervalMilliseconds);
                this.bodySensorDataCollectionTime = Utils.convertToInt(configs.get("body_sensor_data_collection_time"), this.bodySensorDataCollectionTime);
                this.bodyDetectFilterTime = Utils.convertToInt(configs.get("body_detect_filter_time"), this.bodyDetectFilterTime);


                // 判断宽进严出的门店触发订单模式
                this.orderTriggeredMode = Utils.convertToInt(configs.get("order_triggered_mode"), 1);



                NoteControllerTask noteControllerTask = NoteControllerTask.builder()
                        .noResponseTask(noResponseTask)
                        .build();
                this.clientControllerRef.tell(noteControllerTask, this.getSelf());
            }
        }
    }

    /**
     * 接受总分下来的iot
     * @param noteServerAssistantIotTask
     */
    private void processNoteServerAssistantIotTask(NoteServerAssistantIotTask noteServerAssistantIotTask){
        Map<String, Object> task = new HashMap<>();
        List<Task> noResponseTask = new LinkedList<>();
        List<Task> setWrzsStatusTask = new LinkedList<>(); //
        List<Task> doorTask = new LinkedList<>(); //
        int actionType = noteServerAssistantIotTask.getActionType();
        long taskId = noteServerAssistantIotTask.getId();
        if(this.disableActionType == actionType){
            log.info("门店{}获取到任务{}，被禁止执行！",this.equipmentId, this.disableActionType);
            DBHelper.updateIotTaskById(taskId, -2, this.companyId, this.storeId); // 被禁止执行的任务类型，不参与后续逻辑
        }else{
            task.put("action_type", actionType);
            task.put("id", taskId);
            if(actionType == 907){
                log.info("门店{}收到一次立即关门，取消自动审核", this.equipmentId);
                actionType = 902;
                task.put("action_type", 902);
                DBHelper.cancelAutoOpenDoor(this.companyId,this.storeId);
            }

            DBHelper.updateIotTaskByIdWithoutRedis(taskId, 1); // 所有拿到的任务都更新未状态1，保证任务只拿一次
            this.setDoorLogicStatus(actionType); // 更新门的逻辑状态
            Task pendingTask = Task.builder()
                    .taskId(taskId)
                    .taskStatus(TaskStatus.pending)
                    .task(task)
                    .build();

            // 增加一个定时 播放店内提示
            if(this.isWrzsServer == 1 && this.firmwareVersion >= 4 && actionType == 903){
                this.context().system().scheduler().scheduleOnce(
                        FiniteDuration.apply(this.aotuPlayVoiceSecondTimes, TimeUnit.SECONDS),
                        this.getSelf(), NotePlayVoiceTimeout.builder()
                                .player(1)
                                .playTimes(1)
                                .voiceId(this.aotuPlayVoiceSecondNum)
                                .volume(30)
                                .build(),
                        this.context().dispatcher(),
                        this.getSelf());
            }

            // 由服务端主动播放迎宾语
            if(this.isWrzsServer == 1 && this.firmwareVersion >= 8 && actionType == 903){
                this.context().system().scheduler().scheduleOnce(
                        FiniteDuration.apply(this.aotuPlayVoiceFristTimes, TimeUnit.SECONDS),
                        this.getSelf(), NotePlayVoiceTimeout.builder()
                                .player(1)
                                .playTimes(1)
                                .voiceId(this.aotuPlayVoiceFristNum)
                                .volume(30)
                                .build(),
                        this.context().dispatcher(),
                        this.getSelf());
            }


            // 处理室内照明逻辑
            if(this.isEnableInLightControl){ // 继电器常闭合 给1关 给0开

                // 增加一个灯控 relay2 开启托管关灯
                if( actionType == 801 ){
                    log.info("门店{}开启托管主动关灯", this.equipmentId);
                    logAssistant.addLog( "server", "N 开启托管主动关灯");
                    Map<String, Object> mapRelayOpen = new HashMap<>();
                    mapRelayOpen.put("event", 601);
                    mapRelayOpen.put("relay", ENERGY_CONSERVATION_RELAY_2);
                    mapRelayOpen.put("relay_stats", Utils.getRelayStats("关", ENERGY_CONSERVATION_RELAY_2));
                    noResponseTask.add(Task.builder()
                            .task(mapRelayOpen)
                            .taskStatus(TaskStatus.pending)
                            .build());

                }

                // 增加一个灯控 relay2 关闭托管开灯
                if( actionType == 802 ){
                    log.info("门店{}关闭托管主动开灯", this.equipmentId);
                    logAssistant.addLog( "server", "N 关闭托管主动开灯");
                    Map<String, Object> mapRelayOpen = new HashMap<>();
                    mapRelayOpen.put("event", 601);
                    mapRelayOpen.put("relay", ENERGY_CONSERVATION_RELAY_2);
                    mapRelayOpen.put("relay_stats", Utils.getRelayStats("开", ENERGY_CONSERVATION_RELAY_2));
                    noResponseTask.add(Task.builder()
                            .task(mapRelayOpen)
                            .taskStatus(TaskStatus.pending)
                            .build());

                }
            }

            // 处理小灯牌逻辑
            if(this.isHasSmallSignLight){
                if( actionType == 801 ){
                    log.info("门店{}开启托管打开小灯牌", this.equipmentId);
                    logAssistant.addLog( "server", "N 开启托管自动打开小灯牌");
                    Map<String, Object> mapRelayOpen3 = new HashMap<>();
                    mapRelayOpen3.put("event", 601);
                    mapRelayOpen3.put("relay", SMALL_SIGN_LIGHT_RELAY_3);
                    mapRelayOpen3.put("relay_stats", Utils.getRelayStats("开", SMALL_SIGN_LIGHT_RELAY_3));
                    noResponseTask.add(Task.builder()
                            .task(mapRelayOpen3)
                            .taskStatus(TaskStatus.pending)
                            .build());

                }

                // 增加一个灯控 relay2 关闭托管开灯
                if( actionType == 802 ){
                    log.info("门店{}关闭托管关闭小灯牌", this.equipmentId);
                    logAssistant.addLog( "server", "N 关闭托管自动关闭小灯牌");
                    Map<String, Object> mapRelayOpen = new HashMap<>();
                    mapRelayOpen.put("event", 601);
                    mapRelayOpen.put("relay", SMALL_SIGN_LIGHT_RELAY_3);
                    mapRelayOpen.put("relay_stats", Utils.getRelayStats("关", SMALL_SIGN_LIGHT_RELAY_3));
                    noResponseTask.add(Task.builder()
                            .task(mapRelayOpen)
                            .taskStatus(TaskStatus.pending)
                            .build());

                }


                if(doorTask.size() > 0 || setWrzsStatusTask.size() > 0 || noResponseTask.size() > 0){
                    NoteControllerTask noteControllerTask = NoteControllerTask.builder()
                            .doorTask(doorTask)
                            .setWrzsStatusTask(setWrzsStatusTask)
                            .noResponseTask(noResponseTask)
                            .build();
                    this.clientControllerRef.tell(noteControllerTask, this.getSelf());
                }
            }


            // 跟一个定时关门
            if((actionType == 903 || actionType == 905) && this.isWrzsServer == 1){
                int autpActionTpye = 0;
                if(actionType == 903){
                    autpActionTpye = 904;
                }else {
                    autpActionTpye = 906;
                }
                this.context().system().scheduler().scheduleOnce(
                        FiniteDuration.apply(aotuCloseDoorTime, TimeUnit.SECONDS),
                        this.getSelf(),
                        NoteAutoCloseDoor.builder().actionType(autpActionTpye).build(),
                        this.context().dispatcher(),
                        this.getSelf());
            }

            // 新增任务907 表示即要全关门，又要取消一次自动审核
            if(actionType == 901 || actionType == 902 || actionType == 903 || actionType == 904 || actionType == 905 || actionType == 906  || actionType == 907){

//                        if(actionType == 907){
//                            log.info("门店{}收到一次立即关门，取消自动审核", this.equipmentId);
//                            //actionType = 902;
//                            DBHelper.cancelAutoOpenDoor(this.companyId,this.storeId);
//                        }

                doorTask.add(pendingTask);
                this.isCanHaveNewDoorBrokenOrder = false;// 收到iot后，失能一段时间故障检测。
                this.context().system().scheduler().scheduleOnce(
                        FiniteDuration.apply(isDoorMayBrokenWaitTime, TimeUnit.SECONDS),
                        this.getSelf(),
                        NoteAbleBrokenDoorDetect.builder().build(),
                        this.context().dispatcher(),
                        this.getSelf());
            }else if (actionType == 801  || actionType == 802 ){
                setWrzsStatusTask.add(pendingTask);
            }
//                    // 运行播放一次关门提示
            if(actionType == 904){
                this.isCanPlayBronkeDoorVoice = true;
            }
        }

        if(doorTask.size() > 0 || setWrzsStatusTask.size() > 0  || noResponseTask.size() > 0){
            NoteControllerTask noteControllerTask = NoteControllerTask.builder()
                    .doorTask(doorTask)
                    .setWrzsStatusTask(setWrzsStatusTask)
                    .noResponseTask(noResponseTask)
                    .build();
            this.clientControllerRef.tell(noteControllerTask, this.getSelf());
        }
    }

    /**
     * 获取播放任务
     * @param pendingPlayVoiceTask
     * @return
     */
    private List<Task> getPlayVoiceTask(List<Map<String, Object>> pendingPlayVoiceTask){

        List<Task> tasks = new LinkedList<>();
        if(this.isGetPlayVoiceTaskBeforOneMins){ // 一分钟内已经获取到了播放任务, 不在继续处理播放任务
            return tasks;
        }
        // 更新当前时间
        this.getTimeCurrent();

        for (Map<String, Object> task : pendingPlayVoiceTask) {

            String playYmd = task.get("play_ymd") == null ? "" : task.get("play_ymd").toString().trim();
            String playWeek = task.get("play_week") == null ? "" : task.get("play_week") .toString().trim();

            if(this.isTodayNeedPlay(playYmd, playWeek)){ // 当天是否需要播放
//                log.info("查看 当天需要播放");
                String playTime = task.get("play_time") == null ? "" : task.get("play_time").toString().trim();
                if(this.isNowNeedPlay(playTime)){ // 此时是否需要播放
//                    log.info("查看此时需要播放");
                    this.isGetPlayVoiceTaskBeforOneMins = true;


                    Integer storeVoiceId = Utils.convertToInt(task.get("id"), -1);
                    Integer event = 0;
                    Integer volume = 30;
                    if(this.firmwareVersion < 3){
                        event = Utils.convertToInt(task.get("event"), -1);
                        volume = Utils.convertToInt(task.get("volume"), -1);
                        if(volume <= 30){ volume = volume * 8;}
                        if(volume <= 30){ volume = 240;}
                    }else {
                        event = Utils.convertToInt(task.get("voice_id"), -1);
                        volume = Utils.convertToInt(task.get("volume"), -1);
                        if(volume > 30){ volume = volume/8; }
                        if(volume > 30){ volume = 30; }
                    }

                    Integer interval = Utils.convertToInt(task.get("interval"), -1);
                    Integer playCount = Utils.convertToInt(task.get("play_count"), -1);


                    Integer boxIndex = Utils.convertToInt(task.get("box_index"), -1);


                    // event 705 系统通知板子播放某一个媒体（媒体编号、多少次、播放的音量：0~254、用那个播放器：1室内、2室外）
                    if(playCount > 0){
                        Map<String, Object> map = new HashMap<>();
                        map.put("event", 705);
                        map.put("update_voice_name", event);
                        map.put("play_count", playCount);
                        map.put("volume", volume);
                        map.put("box_index", boxIndex);
                        map.put("interval", interval);
                        tasks.add(Task.builder()
                                .task(map)
                                .taskStatus(TaskStatus.pending)
                                .build());
                    }

                }
            }
        }

        if(this.isGetPlayVoiceTaskBeforOneMins){ // 一分钟后解锁
            this.context().system().scheduler().scheduleOnce(
                    FiniteDuration.apply(60, TimeUnit.SECONDS),
                    this.getSelf(),
                    LockGetPlayVoiceTimeout.builder().build(),
                    this.context().dispatcher(),
                    this.getSelf());
        }

        return tasks;
    }


    /**
     * 判断此刻是否需要播放
     * @return
     */
    private boolean isNowNeedPlay(String playTime){
        String now = this.currentTime[3] + ":" + this.currentTime[4];


//        log.info("查看当前时间点{}", now);
        if(playTime != null){
            String[] time =  playTime.split(","); // 所有需要播放的时间点
//            log.info("查看要求播放时间点 {}", time);
            return Arrays.asList(time).contains(now);
        }
        return false;
    }

    /**
     * 判断当天是否需要播放
     * @param playYmd
     * @return
     */
    private boolean isTodayNeedPlay(String playYmd, String playWeek ){
//        log.info("查看播放年月日 {}  星期 {}", playYmd, playWeek);
        if(playYmd != null && playYmd.equals("")){
            playYmd = null;
        }
        if(playWeek != null && playWeek.equals("")){
            playWeek = null;
        }
        if(playYmd == null && playWeek ==null){ return true;} // 没有配置具体播放日志，默认每天都播放

        if(playYmd != null && playWeek == null){  // 配置了日期，没有配置星期
            String[] dates =  playYmd.split(","); // 所有需要播放的天
            // 规约所有日志格式如 2022-11-01
            String year = this.currentTime[0];
            String today = this.currentTime[0] + "-" + this.currentTime[1] + "-" + this.currentTime[2];

            for (int i = 0; i < dates.length; i++){
                if(dates[i].split("-").length == 2){
                    dates[i] = year + "-" + dates[i];
                }
            }
            return Arrays.asList(dates).contains(today);
        }

        if(playYmd == null && playWeek != null){  //  没配置日期，配置了星期
            String today = currentTime[6];
            String[] dates =  playWeek.split(","); // 所有需要播放的天
            return Arrays.asList(dates).contains(today);
        }

        if(playYmd != null && playWeek != null){  //  即配置日期，也配置了星期

            return this.isTodayNeedPlay(playYmd, null) && this.isTodayNeedPlay(null, playWeek);
        }

        return false;

    }

    private void getTimeCurrent(){
        // 当前全日期
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy MM dd HH mm ss EEEE");
        this.currentTime = dateFormat.format(new Date()).split(" ");
        this.currentTime[6] = Utils.getWeek();
    }

    /**
     * 根据盒子的状态更新服务端状态，并生成相应订单与任务
     * @param updateMsg
     */
    private void updateServerInfo(NoteServerClientStatus updateMsg){
//        log.info("收到更新状态消息 {}", updateMsg);

        this.lastTimeGetClientMsg = System.currentTimeMillis();
        /*
        常规更新
            1 心跳时间
            2 检测门状态
            3 固件版本号，有变化更新

        iottask任务
            1 无人值守
            2 开关门

        订单任务
            1 失联订单
            2 故障订单
         */
        final int is_wrzs = updateMsg.getIs_wrzs(); // 是否开启无人值守
        final int is_door_closed_two = updateMsg.getIs_door_closed_two(); // 门2逻辑状态
        final int is_door_closed_one = updateMsg.getIs_door_closed_one(); // 门1逻辑状态
        final int is_help_out = updateMsg.getIs_help_out();
        final int is_help_in = updateMsg.getIs_help_in();
        final int is_poweroff = updateMsg.getIs_poweroff();
        final int is_door_real_close_one = updateMsg.getIs_door_real_close_one(); // 门1检测状态
        final int is_door_real_close_two = updateMsg.getIs_door_real_close_two(); // 门2检测状态
        final int is_out_human_detected = updateMsg.getIs_out_human_detected(); //
        final int is_in_human_detected = updateMsg.getIs_in_human_detected(); //
        final int firmwareVersion = updateMsg.getFirmwareVersion();


        /**
         * 记录传感器的值
         */
        if(is_out_human_detected==1){
            this.out_human_detected_count++;
            //DBHelper.updateSensor(this.companyId, this.storeId, "室外人体感应", 1, 1);

            // 判断是否需要开启滤波
            if(!this.is_during_out_detect){
                // 开启一次滤波计数,为期 bodyDetectFilterTime
                this.is_during_out_detect = true; // 开始检测

                this.context().system().scheduler().scheduleOnce(
                        FiniteDuration.apply(this.bodyDetectFilterTime, TimeUnit.SECONDS),
                        this.getSelf(),
                        NoteCheckBodyOutSensorFileterTimeout.builder().build(),
                        this.context().dispatcher(),
                        this.getSelf());
            }

            // 判断是否已经开启了内传感器的滤波
            if(this.is_during_in_detect){
                if( this.is_can_count_during_in_detect ){
                    this.in_human_detected_count_actual++;
                    log.info("门店{}检测到一次由内到外的客流，当前周期客流总数为{}", this.equipmentId , this.in_human_detected_count_actual);
                    logAssistant.addLog("server", "N 检测到一次由内到外的客流，当前周期客流总数为" + this.in_human_detected_count_actual);
                    this.is_can_count_during_in_detect = false; // 禁止再次计数
                }

            }
        }
        if(is_in_human_detected==1){
            this.in_human_detected_count++;
            //DBHelper.updateSensor(this.companyId, this.storeId, "室内人体感应", 1,1);

            // 判断是否需要开启滤波
            if(!this.is_during_in_detect){
                // 开启一次滤波计数,为期 bodyDetectFilterTime
                this.is_during_in_detect = true; // 开始检测

                this.context().system().scheduler().scheduleOnce(
                        FiniteDuration.apply(this.bodyDetectFilterTime, TimeUnit.SECONDS),
                        this.getSelf(),
                        NoteCheckBodyInSensorFileterTimeout.builder().build(),
                        this.context().dispatcher(),
                        this.getSelf());
            }

            // 判断是否已经开启了外传感器的滤波
            if(this.is_during_out_detect){
                if( this.is_can_count_during_out_detect ){
                    this.out_human_detected_count_actual++;
                    log.info("门店{}检测到一次由外入内的客流，当前周期客流总数为{}", this.equipmentId , this.out_human_detected_count_actual);
                    logAssistant.addLog("server", "N 检测到一次由外入内的客流，当前周期客流总数为" + this.out_human_detected_count_actual);
                    this.is_can_count_during_out_detect = false;
                }

            }
        }
        if(is_help_in==1){
            DBHelper.updateSensor(this.companyId, this.storeId, "室内求助", 1,1);
        }
        if(is_help_out==1){
            DBHelper.updateSensor(this.companyId, this.storeId, "室外求助", 1,1);
        }
        if(is_poweroff != this.last_is_poweroff){
            DBHelper.updateSensor(this.companyId, this.storeId, "断电检测传感器", this.last_is_poweroff,1);
            this.last_is_poweroff = is_poweroff;
        }
        if(is_door_real_close_two != this.last_is_door_real_close_two){
            DBHelper.updateSensor(this.companyId, this.storeId, "门2传感器", is_door_real_close_two,1);
            this.last_is_door_real_close_two = is_door_real_close_two;
        }
        if(is_door_real_close_one != this.last_is_door_real_close_one){
            DBHelper.updateSensor(this.companyId, this.storeId, "门1传感器", is_door_real_close_one,1);
            this.last_is_door_real_close_one = is_door_real_close_one;
        }



        if(this.isServiceTrust){
            /**
             * 宽进严出收到自动关门
             * 1 自动生成进店订单
             * 2 更改门状态
             * 3 自动生成关门任务
             */
            if(this.storeMode == 2 && this.isWrzsServer == 1){
                // 一个开门周期内，门外第一次触发了自动开门，且离店开门时候检测到的不算
                if(is_out_human_detected == 1  && !this.isHasWaittingCloseOneDoorTask && this.isDoorClosedTwoServer != 0){
                    // 更改门的状态
                    log.info("门店{}宽进严出模式下，修改门状态为进店开门，开启进店关门倒计时{}s", this.equipmentId, aotuCloseDoorTime);
                    this.isDoorClosedOneServer = 0;
                    DBHelper.updateStoreDoorStatus(this.storeId, 11, this.companyId);
                    DBHelper.addIotTasksComplete(storeId, 903, 0, this.companyId);

                    this.context().system().scheduler().scheduleOnce(
                            FiniteDuration.apply(aotuCloseDoorTime, TimeUnit.SECONDS),
                            this.getSelf(),
                            NoteAutoCloseDoor.builder().actionType(904).build(),
                            this.context().dispatcher(),
                            this.getSelf());
                    this.isHasWaittingCloseOneDoorTask = true;

                /*
                门外传感器触发，如果顾客离店，也会触发一次，添加条件，如果
                 */
                    if(this.orderTriggeredMode == 0){
                        log.info("门店{}宽进严出模式下，生成服务订单", this.equipmentId);
                        DBHelper.addMusOrders(this.storeId, this.companyId, "购物", true, this.storeName);
                    }
                }
                // 宽进严出模式下 订单触发模式（0 门外传感器触发，1 门内传感器触发）
                if(this.orderTriggeredMode == 1){
                    if(this.isHasWaittingCloseOneDoorTask){
                        if(is_in_human_detected == 1){
                            log.info("门店{}宽进严出模式下，生成服务订单", this.equipmentId);
                            DBHelper.addMusOrders(this.storeId, this.companyId, "购物",  this.isCanHaveNewOrder, this.storeName);
                            this.isCanHaveNewOrder = false;
                        }
                    }
                }

            }


            /**
             * 安防订单
             */
            if(this.isWrzsServer == 1 && this.isCanHaveSafeOrder && is_in_human_detected == 1){
                if(this.disableOrderType != 2){
                    log.info("门店{}触发安防订单", this.equipmentId);
                    DBHelper.addMusOrders(storeId, this.companyId,"安防" ,  false, this.storeName);
                }else {
                    log.info("门店{}触发安防订单，但是禁止生成", this.equipmentId);
                }

            }

            /**
             * 店内求助
             */
            if(is_help_in == 1){
                if(this.disableOrderType != 3){
                    log.info("门店{}触发求助订单", this.equipmentId);
                    logAssistant.addLog( "server", "N 触发求助订单");
                    DBHelper.addMusOrders(storeId, this.companyId,"店内求助" ,  false, this.storeName);
                }else{
                    log.info("门店{}触发求助订单，但是禁止生成", this.equipmentId);
                    logAssistant.addLog( "server", "N 触发求助订单，但是禁止生成");
                }

            }

            /**
             * 断电订单
             */
            if(is_poweroff == 1){
                if(this.disableOrderType != 4){
                    if(!this.isPoweroff){ // 是否上报了断电， 保证一次断电只上报一次
                        log.info("门店{}触发断电订单", this.equipmentId);
                        DBHelper.addNotifyV2(storeId, this.companyId,"断电" , this.storeName);
                        this.isPoweroff = true;
                    }
                }else{
                    log.info("门店{}触发断电订单，但是禁止生成", this.equipmentId);
                }

            }else if (is_poweroff == 0 && this.isPoweroff){
                this.isPoweroff = false;
            }

            /**
             * 故障订单
             */
            if(is_door_closed_two != is_door_real_close_two ||  is_door_closed_one != is_door_real_close_one){
                if(this.isWrzsServer == 1 && !this.isDoorMayBroken ){
                    if(this.disableOrderType != 1){
                        log.info("门店{}门锁状态异常，开始故障监视，门1逻辑状态{} 门1检测状态{} 门2逻辑状态{} 门2检测状态{}", this.equipmentId,
                                is_door_closed_one, is_door_real_close_one, is_door_closed_two, is_door_real_close_two);

                        if(this.isCanHaveNewDoorBrokenOrder){
                            log.info("门店{}生成故障订单", this.equipmentId);
                            if(this.firmwareVersion >= 4 && this.isCanPlayBronkeDoorVoice){
                                this.isCanPlayBronkeDoorVoice = false;
                                this.noteControllerPlayVoice(16, 1, 20, 1); // 在客户端发出语音提示
                            }

                            DBHelper.addMusOrders(storeId, this.companyId, "故障",  false, this.storeName);
                        }else {
                            log.info("门店{}门锁状态异常，但是处于iot失能期间，不生成故障订单，门1逻辑状态{} 门1检测状态{} 门2逻辑状态{} 门2检测状态{}", this.equipmentId,
                                    is_door_closed_one, is_door_real_close_one, is_door_closed_two, is_door_real_close_two);
                        }
//                    this.isDoorMayBroken = true;
//                    this.context().system().scheduler().scheduleOnce(
//                            FiniteDuration.apply(isDoorMayBrokenWaitTime, TimeUnit.SECONDS),
//                            this.getSelf(),
//                            NoteDoorMayBrokenTimeout.builder().build(),
//                            this.context().dispatcher(),
//                            this.getSelf());
                    }else {
                        log.info("门店{}门锁状态异常，但是禁止生成故障订单，门1逻辑状态{} 门1检测状态{} 门2逻辑状态{} 门2检测状态{}", this.equipmentId,
                                is_door_closed_one, is_door_real_close_one, is_door_closed_two, is_door_real_close_two);
                    }

//                    // 查看是否需要播放门未关严语音
//                    if( this.isCanPlayBronkeDoorVoice ){
//                        this.isCanPlayBronkeDoorVoice = false;
//                        this.noteControllerPlayVoice(16, 3, 20, 1);
//
//                    }

                }
            }
        }

        // 如果已经开始记录门状态不对，期间任何时候状态恢复都可以修正记录
        if(this.isDoorMayBroken && is_door_closed_two == is_door_real_close_two &&  is_door_closed_one == is_door_real_close_one  ){
            this.isDoorMayBroken = false;
        }


        this.firmwareVersion = firmwareVersion;
        DBHelper.updateStoreLastHeartBeatTime(this.storeId, this.companyId, firmwareVersion, is_door_real_close_one, is_door_real_close_two);


        //801 开启无人值守
        //802 关闭无人值守
        //803 板子完成了初始化
        if(this.isWrzsServer != is_wrzs){
            if(this.isWrzsServer == 1){
                DBHelper.addIotTasksWithoutProcessingOrPending(storeId, 801, 0, this.companyId);
                DBHelper.addIotTasksWithoutProcessingOrPending(storeId, 902, 0, this.companyId);
            }else if(this.isWrzsServer == 0){
                DBHelper.addIotTasksWithoutProcessingOrPending(storeId, 802, 0, this.companyId);
                DBHelper.addIotTasksWithoutProcessingOrPending(storeId, 901, 0, this.companyId);
            }
            return;
        }

        // 执行动作 (901 全开门 902 全关门，903 进店开门 904 进店关门，905 离店开门 906 离店关门)
        if(this.isDoorClosedOneServer != is_door_closed_one){
            if(this.isDoorClosedOneServer == 1){
                if(!DBHelper.isHasProcessingOrPendingAction(storeId, 902, this.companyId)){
                    DBHelper.addIotTasksWithoutProcessingOrPending(storeId, 904, 0, this.companyId);
                }
            }else if(this.isDoorClosedOneServer == 0){
                if(!DBHelper.isHasProcessingOrPendingAction(storeId, 901, this.companyId)){
                    DBHelper.addIotTasksWithoutProcessingOrPending(storeId, 903, 0, this.companyId);
                }
            }
        }

        if(this.isDoorClosedTwoServer != is_door_closed_two){
            if(this.isDoorClosedTwoServer == 1){
                if(!DBHelper.isHasProcessingOrPendingAction(storeId, 902, this.companyId)){
                    DBHelper.addIotTasksWithoutProcessingOrPending(storeId, 906, 0, this.companyId);
                }
            }else if(this.isDoorClosedTwoServer == 0){
                if(!DBHelper.isHasProcessingOrPendingAction(storeId, 901, this.companyId)){
                    DBHelper.addIotTasksWithoutProcessingOrPending(storeId, 905, 0, this.companyId);
                }
            }
        }


        // 如果开启了无人值守，外传感器触发，播放两边扫码指引,仅仅对硬件为6以上的版本生效
        if(this.isWrzsServer == 1 && this.firmwareVersion > 3 && is_out_human_detected == 1 && this.isCanPlayInScanCodeGuidance){
           // this.noteControllerPlayVoice(2, 2, 22, 2);
            this.isCanPlayInScanCodeGuidance = false;

            // 一定时间内不重复播放
            // 三遍总需要篇12s 上报反馈扣掉2s延迟
            this.context().system().scheduler().scheduleOnce(
                    FiniteDuration.apply(10, TimeUnit.SECONDS),
                    this.getSelf(),
                    NotePlayScanCodeGuidanceTimeout.builder().build(),
                    this.context().dispatcher(),
                    this.getSelf());

            this.context().system().scheduler().scheduleOnce(
                    FiniteDuration.apply(2, TimeUnit.SECONDS),
                    this.getSelf(),
                    NotePlayVoiceTimeout.builder()
                            .volume(22)
                            .voiceId(2)
                            .playTimes(2)
                            .player(2)
                            .build(),
                    this.context().dispatcher(),
                    this.getSelf());
        }


    }

    /**
     * 设置门的逻辑状态，逻辑状态由iot任务决定
     * 执行动作 (901 全开门、  902 全关门、  903 进店开门、 904 进店关门、  905 离店开门、 906 离店关门)
     * @param actionType
     */
    private void setDoorLogicStatus(Integer actionType){
        switch (actionType){
            case 901:
                this.isDoorClosedOneServer = 0;
                this.isDoorClosedTwoServer = 0;
                break;
            case 902:
                this.isDoorClosedOneServer = 1;
                this.isDoorClosedTwoServer = 1;
                break;
            case 903:
                this.isDoorClosedOneServer = 0;
//                this.isDoorClosedTwoServer = 0;
                break;
            case 904:
                this.isDoorClosedOneServer = 1;
//                this.isDoorClosedTwoServer = 0;
                break;
            case 905:
//                this.isDoorClosedOneServer = 0;
                this.isDoorClosedTwoServer = 0;
                break;
            case 906:
//                this.isDoorClosedOneServer = 0;
                this.isDoorClosedTwoServer = 1;
                break;
        }
    }


    /**
     * 根据action更新门的状态
     * @param actionType
     */
    private int getDoorUpdateStatus(Integer actionType){
        // TODO: 2022/11/7  这个门状态只是更新状态，不是门的逻辑输入状态，由iot任务作为输出，决定门逻辑状态
        // 0. 获取门店锁状态 (门锁状态 (0 全关门 1 全开门， 10 进店关门 11 进店开门， 20 离店关门 21 离店开门))
        // 执行动作 (901 全开门、  902 全关门、  903 进店开门、 904 进店关门、  905 离店开门、 906 离店关门)
        switch (actionType){
            case 901:
                return 1;
            case 902:
                return 0;
            case 903:
                return 11;
            case 904:
                return 10;
            case 905:
                return 21;
            case 906:
                return 20;
            default:
                return -1;
        }
    }


    /**
     * 判断是否需要自动完成
     */
    private void autoOpenDoor(){
        DBHelper.checkAutoOpenDoor( this.companyId, this.storeId, this.intervalMilliseconds);
    }


    /**
     * 获取配置
     */
    private void initConfig(){

        // 加载stores表
        Map<String, Object> storeMap = DBHelper.getStoreMap(this.storeId, this.companyId);
        if(storeMap != null){ // `status`, `mode`, `name`, `order_triggered_mode`, `power`, `door_status`, `serial_number`, `private_key`, `open_seconds`, `is_service_trust`
            this.storeName = storeMap.get("name").toString();
            this.storeMode = Utils.convertToInt(storeMap.get("mode"), 0);
//            this.orderTriggeredMode = Utils.convertToInt(storeMap.get("order_triggered_mode"), 1);
            this.isServiceTrust = Utils.convertToInt(storeMap.get("is_service_trust"), 1) == 1;
        }

        // 加载configs表
//        Map<String, Object> storeconfigs = DBHelper.getStoreconfigs(this.storeId, this.companyId);
//        if(storeconfigs != null){
//            this.isEnableInLightControl = Utils.convertToInt(storeconfigs.get("is_enable_in_light_control"), 0) == 1;
//            this.isEnableOutLightControl = Utils.convertToInt(storeconfigs.get("is_enable_out_light_control"), 0) == 1;
//            this.inVolume = Utils.convertToInt(storeconfigs.get("in_volume"), 30);
//            this.outVolume = Utils.convertToInt(storeconfigs.get("out_volume"), 30);
//        }




    }


    /**
     * 离店开门后 关灯延迟
     */
    private void NoteDoenInLightTimeoutProcess(){

        Set<String> keys = RedisHelper.isExistsOrderCannotCloseInLight(this.companyId, this.storeId);

        if(keys == null || keys.size() == 0){
            // 开启了无人值守、固件版本、进店开门、使能室内灯控、继电器没有开
            log.info("门店{}无订单主动关灯", this.equipmentId);
            List<Task> noResponseTask = new LinkedList<>(); //不需要客户端反馈的任务
            Map<String, Object> mapRelayOpen = new HashMap<>();
            mapRelayOpen.put("event", 601);
            mapRelayOpen.put("relay", ENERGY_CONSERVATION_RELAY_2);
            mapRelayOpen.put("relay_stats",  Utils.getRelayStats("关", ENERGY_CONSERVATION_RELAY_2));
            noResponseTask.add(Task.builder()
                    .task(mapRelayOpen)
                    .taskStatus(TaskStatus.pending)
                    .build());

            NoteControllerTask noteControllerTask = NoteControllerTask.builder()
                    .noResponseTask(noResponseTask)
                    .build();
            this.clientControllerRef.tell(noteControllerTask, this.getSelf());
        }else{
            log.info("本次关门，门店无法{}主动关灯，存在订单{}", this.equipmentId, keys);
        }




    }


    /**
     * 向erverHelper注册
     */
    private void register2ServerHelper(){
        this.servertHeleper.tell(RegisterToServerHelper.builder()
                .serverRef(this.getSelf())
                .companyId(this.companyId)
                .equipmentId(this.equipmentId)
                .storeId(this.storeId)
                .storeName(this.storeName)
                .build(), this.getSelf());
    }


    /**
     *
     * @param relay
     * @param status "关"    "开"
     */
    private void processLightStatus(int relay, String status){

        List<Task> noResponseTask = new LinkedList<>(); //不需要客户端反馈的任务
        Map<String, Object> mapRelayOpen = new HashMap<>();
        mapRelayOpen.put("event", 601);
        mapRelayOpen.put("relay", relay);
        mapRelayOpen.put("relay_stats",  Utils.getRelayStats(status, relay));
        noResponseTask.add(Task.builder()
                .task(mapRelayOpen)
                .taskStatus(TaskStatus.pending)
                .build());

        NoteControllerTask noteControllerTask = NoteControllerTask.builder()
                .noResponseTask(noResponseTask)
                .build();
        this.clientControllerRef.tell(noteControllerTask, this.getSelf());

    }


    /**
     * 处理即时音频任务
     */
    private void processNoteVoiceTask(NoteVoiceTask noteVoiceTask){

        int voiceId = noteVoiceTask.getVoiceId();
        int player = noteVoiceTask.getPlayer();
        long id = noteVoiceTask.getId();
        long createTime = noteVoiceTask.getCreateTime();
        int enableTime = noteVoiceTask.getEnableTime();
        // 发送播放任务
        List<Task> playVoiceTask = new LinkedList<>();
        Map<String, Object> mapPlayVoice = new HashMap<>();
        mapPlayVoice.put("event", 705);
        mapPlayVoice.put("update_voice_name", voiceId);
        mapPlayVoice.put("play_count", noteVoiceTask.getTimes());
        mapPlayVoice.put("volume", noteVoiceTask.getVolume());
        mapPlayVoice.put("box_index", player);
        mapPlayVoice.put("interval", noteVoiceTask.getInterval());
        playVoiceTask.add(Task.builder()
                .task(mapPlayVoice)
                .taskStatus(TaskStatus.pending)
                .build());
        NoteControllerTask noteControllerTask = NoteControllerTask.builder()
                .playVoiceTask(playVoiceTask)
                .build();
        this.clientControllerRef.tell(noteControllerTask, this.getSelf());


        // 处理mysql

        if(this.firmwareVersion >= 4){
            if(System.currentTimeMillis() - createTime > enableTime){
                log.info("门店{}要播放的音频{}已过期，不予以播放", this.equipmentId, voiceId);
                DBHelper.updateIotaskVoice2Complete(id, -1);
            }else {
                DBHelper.updateIotaskVoice2Complete(id, 2);
            }
        }
 
    }

}
