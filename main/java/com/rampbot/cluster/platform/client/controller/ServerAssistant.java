package com.rampbot.cluster.platform.client.controller;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
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

    private final int IS_DOOR_MAY_BROKEN_WAIT_TIME = 30; // s 门锁状态运行最大时间，客户端配置
    private final int CONNECTION_TSET_TIME = 10; // s 失联检测周期，10秒触发一次，这个值不受客户配置
    private final long MAX_TIME_WITHOUT_CONNECTION = 1 * 60 * 1000 ; // ms 失联运行最大时间，客户配置，但是需要大于服务端的断网配置时间（40s）
    private int aotuCloseDoorTime = 5; // s
    long intervalMilliseconds = 10 * 1000;
    private int DISABLE_SAFE_ORDER = 15; // 失能安防订单时间 单位 min

    private boolean isGetProcessingTask = true;

    private final int storeId;
    private final int companyId;
    private final String equipmentId; // 门店序列号

    private int storeMode = 0; // 门店模式(0 严进严出、1 宽进宽出、2 宽进严出、3 严进宽出)
    private int orderTriggeredMode = 1; // 宽进严出模式下 订单触发模式（0 门外传感器触发，1 门内传感器触发）
    private int isWrzsServer = 1; // 服务端记录的无人值守状态 1 开启， 0未开启
    private int isDoorClosedOneServer = 1; // 服务端记录的1门状态 进店门 1 关门  0 未关门 ,拿到任务后更新
    private int isDoorClosedTwoServer = 1; // 服务端记录的2门状态 离店门
    private final ActorRef clientControllerRef;
    private String[] currentTime; // 当前时间
    private boolean isGetPlayVoiceTaskBeforOneMins = false;
    private boolean isDoorMayBroken = false;
    private long lastTimeGetClientMsg;


    private boolean isHasWaittingCloseOneDoorTask = false; // 宽进严出模式下，已经存在一个等待发布的进店关门，在此期间，不在生成进店关门，不在发布服务订单

    private boolean isCanHaveNewOrder = true; // 当用门内传感器触发订单时候  1 一开开门周期，只允许生成一条服务订单   2 新的开门周期后，可以出现多条服务订单

    private boolean isFristGetServerInfo = true;


    private int firmwareVersion = 0; // 固件版本号

    private int safePlayNum = 0; // 安防播报间隙

    private boolean isCanHaveSafeOrder = true; // 是否可以产生安防订单

    private boolean isPoweroff = false; // 是否上报了断电

    public ServerAssistant(final ActorRef clientControllerRef, final int storeId, final int companyId, final String equipmentId) {
        this.storeId = storeId;
        this.clientControllerRef = clientControllerRef;
        this.companyId = companyId;
        this.equipmentId = equipmentId;
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
            this.isCanHaveSafeOrder = true;
        }

    }

    public void postStop(){
        DBHelper.updateVoiceTask2Pending(this.storeId, this.companyId);
        DBHelper.updateIotask2Complete(this.storeId, this.companyId);

        DBHelper.addNotifyV2(this.storeId, this.companyId, "失联");
        log.info("门店{}服务端{} 停止任务，并增加失联订单",this.equipmentId, this.getSelf());
    }

    public void preStart() {
        this.lastTimeGetClientMsg = System.currentTimeMillis();
        this.getServerConfig();
        this.getServerInfo();
        this.isFristGetServerInfo = false;
        this.isGetProcessingTask = false; // 第一次启动也获取状态为1的任务,之后都不获取
        this.connectionTest();
        this.getServerInfoTimeout();

        Map<String, Object> storeMap = DBHelper.getStoreMap(this.storeId, this.companyId);
        this.storeMode = Utils.convertToInt(storeMap.get("mode"), 0);
        this.orderTriggeredMode = Utils.convertToInt(storeMap.get("order_triggered_mode"), 1);
        //this.storeMode = Utils.convertToInt(DBHelper.getStoreMap(this.storeId, this.companyId).get("mode"), 0);

    }

    /**
     * 首次启动获取一些服务端的配置
     */
    private void getServerConfig(){
        this.intervalMilliseconds = Utils.convertToLong(DBHelper.getConfigValue(104, this.companyId), 10000);
    }

    /**
     * 更新服务端心跳时间
     */
    private void processNoteServerUpdateHeartBeatTime(){
        this.lastTimeGetClientMsg = System.currentTimeMillis();
        DBHelper.updateStoreLastHeartBeatTime(this.storeId, this.companyId);
    }

    /**
     * 失联订单检测
     */
    private void connectionTest(){
        long now = System.currentTimeMillis();
        if(now - this.lastTimeGetClientMsg >= MAX_TIME_WITHOUT_CONNECTION){
            log.info("门店{}超过{}ms没有上报心跳，生成失联订单，并销毁服务该门店的actor", this.equipmentId, MAX_TIME_WITHOUT_CONNECTION);
            DBHelper.addNotifyV2(this.storeId, this.companyId, "失联");
            this.clientControllerRef.tell(NoteClientStop.builder().build(), this.getSelf());
        }

        if(now - this.lastTimeGetClientMsg >= 2 * MAX_TIME_WITHOUT_CONNECTION){
            log.info("门店{}超过{}ms没有上报心跳，此为经过两次自我销毁未成功，所以自毁吧", this.equipmentId, 2 * MAX_TIME_WITHOUT_CONNECTION);
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
            log.info("门店{}经过{}故障监视周期，门锁状态依旧为恢复，生成故障订单", this.equipmentId, IS_DOOR_MAY_BROKEN_WAIT_TIME);
            DBHelper.addMusOrders(storeId, this.companyId, "故障",  false);
        }
    }


    /**
     * 更新音频任务
     * @param noteServerVoiceTaskStatus
     */
    private void processNoteServerVoiceTaskStatus(NoteServerVoiceTaskStatus noteServerVoiceTaskStatus){
        DBHelper.updateVoiceTask(this.storeId, this.companyId, noteServerVoiceTaskStatus.getVoiceTask().getDownloadPlace(), 2);
    }

    /**
     * 处理任务状态更新
     * @param noteServerTaskStatus
     */
    private void processNoteServerTaskStatus(NoteServerTaskStatus noteServerTaskStatus){
        log.info("门店{}收到更新任务状态 {}",this.equipmentId, noteServerTaskStatus);
        Task updateTask = noteServerTaskStatus.getTask();
        if(updateTask.getTaskStatus().equals(TaskStatus.completed)){
            DBHelper.updateIotTaskById(updateTask.getTaskId(), 2, this.companyId); // 更新iot状态

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


        /**
         * 获取状态更新
         */
        Map<String, Object> storeConfig = DBHelper.getStoreMap(this.storeId, this.companyId);
        // 获取值守状态  1 正常营业 、 2 停业 、 3 远程值守中
        if(Utils.convertToInt(storeConfig.get("status"), -1) == 3){


            // TODO: 2023/1/13  增加开启无人值守后 一段时间内不产生安放订单
            if(this.isWrzsServer == 0 && this.isCanHaveSafeOrder){
                this.isCanHaveSafeOrder = false;

                log.info("门店{}禁止产生安防订单", this.equipmentId);

                this.context().system().scheduler().scheduleOnce(
                        FiniteDuration.apply(DISABLE_SAFE_ORDER, TimeUnit.MINUTES),
                        this.getSelf(),
                        NoteAbleSafeOrder.builder().build(),
                        this.context().dispatcher(),
                        this.getSelf());
            }

            this.isWrzsServer = 1;

        }else{
            this.isWrzsServer = 0;
            if(isFristGetServerInfo){
                this.isDoorClosedTwoServer = 0;
                this.isDoorClosedOneServer = 0;
            }
        }

        this.aotuCloseDoorTime = Utils.convertToInt(storeConfig.get("open_seconds"), 5);


        /**
         * 先判断是否有需要自动完成的任务
         */
        this.autoOpenDoor();


        /**
         * 处理iot任务
         */
        List<Map<String, Object>> taskList =  DBHelper.getPendingIotTask(this.storeId, this.companyId, this.isGetProcessingTask);
        if(taskList != null && taskList.size() > 0){
            log.info("门店{}从服务端获取的iot门任务 {}",this.equipmentId, taskList);
            for(int i = 0; i < taskList.size(); i++){
                Map<String, Object> task = taskList.get(i);
                int actionType = Integer.parseInt(String.valueOf(task.get("action_type")));
                long taskId = Long.parseLong(String.valueOf(task.get("id")));
                DBHelper.updateIotTaskById(taskId, 1, this.companyId); // 所有拿到的任务都更新未状态1，保证任务只拿一次
                this.setDoorLogicStatus(actionType); // 更新门的逻辑状态
                Task pendingTask = Task.builder()
                        .taskId(taskId)
                        .taskStatus(TaskStatus.pending)
                        .task(task)
                        .build();

                if((actionType == 903 || actionType == 905) && this.isWrzsServer == 1){
                    // 跟一个定时关门
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



                if(actionType == 901 || actionType == 902 || actionType == 903 || actionType == 904 || actionType == 905 || actionType == 906 ){
                    doorTask.add(pendingTask);
                }else if (actionType == 801  || actionType == 802 ){
                    setWrzsStatusTask.add(pendingTask);
                }
            }
        }

        /**
         * 处理音频下载任务
         * 若开启无人值守不下载语音
         * 第一次同步服务状态不使用
         */
        if(this.isWrzsServer != 1 && !this.isFristGetServerInfo && this.firmwareVersion < 4){
            List<Map<String, Object>> pendingVoiceTask = DBHelper.getPendingVoiceTask(storeId, this.companyId);
            if(pendingVoiceTask != null && pendingVoiceTask.size() > 0){
//                log.info("从服务端获取的音频下载任务 {}", pendingVoiceTask);
                for(int i = 0; i < pendingVoiceTask.size(); i++){
                    int version = -1;
                    String fileUrl = null;
                    byte[] voiceData = null;
                    int voiceId = Utils.convertToInt(pendingVoiceTask.get(i).get("voice_id"), -1);
                    int downloadPlace = Utils.convertToInt(pendingVoiceTask.get(i).get("sd_index"), -1);
                    DBHelper.updateVoiceTask(this.storeId, this.companyId, downloadPlace, 1); // 更新已经触发下载的任务状态
                    Map<String, Object> voiceMsg = DBHelper.getVoice(voiceId, this.companyId);
                    if(voiceMsg != null && voiceMsg.size() > 0){
                        version = Utils.convertToInt(voiceMsg.get("version"), -1);
                        fileUrl = voiceMsg.get("file_url").toString();
//                        voiceData = Utils.getContent("C:\\Users\\work\\Desktop\\2.mp3");
                        try {
                            voiceData = Utils.downloadVoiceFiles(fileUrl);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    if(voiceData == null){
                        log.info("门店{}需要下载的音频{} 内容获取失败", this.equipmentId, voiceId);
                        DBHelper.updateVoiceTask(this.storeId, this.companyId, downloadPlace, -2);
                    }else {
                        downloadVoiceTask.add(VoiceTask.builder()
                                .version(version)
                                .voiceData(voiceData)
                                .downloadPlace(downloadPlace)
                                .taskStatus(TaskStatus.pending)
                                .voiceId(voiceId)
                                .build());
                    }


                }
            }
        }

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
                List<Map<String, Object>> pendingVoiceIotTask = DBHelper.getPendingVoiceIotTask(this.storeId, this.companyId);
                if(pendingVoiceIotTask != null ){
                    for(Map<String, Object> taskMap : pendingVoiceIotTask){
                        int voiceId = Utils.convertToInt(taskMap.get("voice_id"), 1);
                        int player = Utils.convertToInt(taskMap.get("player"), 1);
                        long createTime = Utils.convertToLong(taskMap.get("update_time"), -1);
                        int enableTime = Utils.convertToInt(taskMap.get("enable_time"), 5000);
                        if(System.currentTimeMillis() - createTime > enableTime){
                            log.info("门店{}要播放的音频{}已过期，不予以播放", this.equipmentId, voiceId);
                            DBHelper.updateIotaskVoice2Complete(this.storeId, this.companyId, voiceId , player, 3);
                        }else {
                            Map<String, Object> taskMapForHelper = new HashMap<>();
                            taskMapForHelper.put("event", 705);
                            taskMapForHelper.put("update_voice_name", voiceId);
                            taskMapForHelper.put("play_count", Utils.convertToInt(taskMap.get("times"), 1));
                            taskMapForHelper.put("volume", 30);
                            taskMapForHelper.put("box_index", player);
                            taskMapForHelper.put("interval", 0);
                            playVoiceTask.add(Task.builder()
                                    .task(taskMapForHelper)
                                    .taskStatus(TaskStatus.pending)
                                    .build());
                            DBHelper.updateIotaskVoice2Complete(this.storeId, this.companyId, voiceId , player, 2);
                        }
                    }
                }
                // 安防订单播报
                if(this.isWrzsServer == 1 ){
                    if( RedisHelper.isExistsPendingSaftOrder(this.companyId, this.storeId)){
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

                        if(this.safePlayNum == 6){this.safePlayNum = 0;}
                    }

                }
            }

        }


        // 通知controller新的任务
        if(doorTask.size() > 0 || setWrzsStatusTask.size() > 0 || downloadVoiceTask.size() > 0 || playVoiceTask.size() > 0){
            NoteControllerTask noteControllerTask = NoteControllerTask.builder()
                    .doorTask(doorTask)
                    .setWrzsStatusTask(setWrzsStatusTask)
                    .downloadVoiceTask(downloadVoiceTask)
                    .playVoiceTask(playVoiceTask)
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
                    Integer event = Utils.convertToInt(task.get("event"), -1);
                    Integer interval = Utils.convertToInt(task.get("interval"), -1);
                    Integer playCount = Utils.convertToInt(task.get("play_count"), -1);
                    Integer volume = Utils.convertToInt(task.get("volume"), -1);
                    Integer boxIndex = Utils.convertToInt(task.get("box_index"), -1);


                    // event 705 系统通知板子播放某一个媒体（媒体编号、多少次、播放的音量：0~254、用那个播放器：1室内、2室外）
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
         * 宽进严出
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
                    DBHelper.addMusOrders(this.storeId, this.companyId, "购物", true);
                }
            }
            // 宽进严出模式下 订单触发模式（0 门外传感器触发，1 门内传感器触发）
            if(this.orderTriggeredMode == 1){
                if(this.isHasWaittingCloseOneDoorTask){
                    if(is_in_human_detected == 1){
                        log.info("门店{}宽进严出模式下，生成服务订单", this.equipmentId);
                        DBHelper.addMusOrders(this.storeId, this.companyId, "购物",  this.isCanHaveNewOrder);
                        this.isCanHaveNewOrder = false;
                    }
                }
            }

        }


        /**
         * 安防订单
         */
        if(this.isWrzsServer == 1 && this.isCanHaveSafeOrder && is_in_human_detected == 1){
        //if(this.isWrzsServer == 1 && is_in_human_detected == 1){
            log.info("门店{}触发安防订单", this.equipmentId);
            DBHelper.addMusOrders(storeId, this.companyId,"安防" ,  false);
        }

        /**
         * 店内求助
         */
        if(is_help_in == 1){
            log.info("门店{}触发求助订单", this.equipmentId);
            DBHelper.addMusOrders(storeId, this.companyId,"店内求助" ,  false);
        }

        /**
         * 断电订单
         */
        if(is_poweroff == 1){
            if(!this.isPoweroff){
                log.info("门店{}触发断电订单", this.equipmentId);
                DBHelper.addNotifyV2(storeId, this.companyId,"断电" );
                this.isPoweroff = true;
            }
        }else if (is_poweroff == 0 && this.isPoweroff){
            this.isPoweroff = false;
        }




        /**
         * 故障订单
         */
        if(is_door_closed_two != is_door_real_close_two ||  is_door_closed_one != is_door_real_close_one){
            if(this.isWrzsServer == 1 && !this.isDoorMayBroken){
                log.info("门店{}门锁状态异常，开始故障监视，门1逻辑状态{} 门1检测状态{} 门2逻辑状态{} 门2检测状态{}", this.equipmentId,
                        is_door_closed_one, is_door_real_close_one, is_door_closed_two, is_door_real_close_two);
                this.isDoorMayBroken = true;
                this.context().system().scheduler().scheduleOnce(
                        FiniteDuration.apply(IS_DOOR_MAY_BROKEN_WAIT_TIME, TimeUnit.SECONDS),
                        this.getSelf(),
                        NoteDoorMayBrokenTimeout.builder().build(),
                        this.context().dispatcher(),
                        this.getSelf());
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
        DBHelper.checkAutoOpenDoor(this.storeId, this.companyId, this.intervalMilliseconds);
    }
}
