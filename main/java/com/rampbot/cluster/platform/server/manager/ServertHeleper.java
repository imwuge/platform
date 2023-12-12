package com.rampbot.cluster.platform.server.manager;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import com.alibaba.fastjson.JSON;
import com.rampbot.cluster.platform.client.utils.DBHelper;
import com.rampbot.cluster.platform.client.utils.DownloadVoiceHelper;
import com.rampbot.cluster.platform.client.utils.RedisHelper;
import com.rampbot.cluster.platform.client.utils.Utils;
import com.rampbot.cluster.platform.domain.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import scala.concurrent.duration.FiniteDuration;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class ServertHeleper extends UntypedActor {

    private final Server server;


    private Map<String, ActorRef> storeId2serverRefs;


    private String[] currentTime; // 当前时间 2023 03 08 19 40 55 星期三

    private  Set<String> activeStores;// 当前有订单的门店


    public ServertHeleper(@NonNull final Server server) {
        this.server = server;
    }


    public void preStart(){
        log.info("启动ServertHeleper");
        this.activeStores = new HashSet<>();
        this.storeId2serverRefs = new HashMap<>();

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


        // 开启订单状态查询定时
        this.context().system().scheduler().scheduleOnce(
                FiniteDuration.apply(1, TimeUnit.SECONDS),
                this.getSelf(),
                NoteCheckStatusOneSecondTimeout.builder().build(),
                this.context().dispatcher(),
                this.getSelf());

        this.context().system().scheduler().scheduleOnce(
                FiniteDuration.apply(10, TimeUnit.MINUTES),
                this.getSelf(),
                NoteCheckDownloadVoiceDataTimeout.builder().build(),
                this.context().dispatcher(),
                this.getSelf());

    }


    @Override
    public void onReceive(Object o) throws Throwable {
        if(o instanceof RegisterToServerHelper){
            RegisterToServerHelper registerToServerHelper = (RegisterToServerHelper) o;
            this.processRegisterToServerHelper(registerToServerHelper);
        }else if(o instanceof NoteCheckDisconnect){
            NoteCheckDisconnect noteCheckDisconnect = (NoteCheckDisconnect) o;
            this.processNoteCheckDisconnectTimeout(noteCheckDisconnect);
            // 此时服务端的actor已经销毁了，所以需要清掉服务端的ref，重新等待注册
            log.info("清掉门店{}服务端ref", noteCheckDisconnect.getStoreId());
            this.storeId2serverRefs.remove(Utils.getKeyFromInteger(noteCheckDisconnect.getCompanyId(), noteCheckDisconnect.getStoreId()));
        }else if(o instanceof NoteCheckDisconnectTimeout){
            NoteCheckDisconnectTimeout noteCheckDisconnectTimeout = (NoteCheckDisconnectTimeout) o;
            log.info("最后在核对一次，门店{}是否失联{} ", noteCheckDisconnectTimeout.getStoreId(), noteCheckDisconnectTimeout);
//            DBHelper.addNotifyV2(noteCheckDisconnectTimeout.getStoreId(), noteCheckDisconnectTimeout.getCompanyId(), "失联", noteCheckDisconnectTimeout.getStoreName());

            int storeId = noteCheckDisconnectTimeout.getStoreId();
            int companyId = noteCheckDisconnectTimeout.getCompanyId();
            String storeName = noteCheckDisconnectTimeout.getStoreName();
            Map<String, Object> storeConfig = DBHelper.getStoreMap(storeId, companyId);
            // 获取值守状态  1 正常营业 、 2 停业 、 3 远程值守中
            String title = "";
            String content = "";
            if(Utils.convertToInt(storeConfig.get("status"), -1) == 3){
                title = "【第二次失联】新消息通知";
                content = storeName +  ": 已开启无人值守，第二次失联消息通知";
            }else{
                title = "【第二次失联】新消息通知";
                content = storeName +  ": 未开启无人值守，第二次失联消息通知";
            }

            DBHelper.addNotifyV3(storeId, companyId, "失联", storeName, title, content);
            DBHelper.setConfigStatus(companyId, storeId, "失联");
            DBHelper.addWorkStatusLog(companyId, storeId, storeName,noteCheckDisconnectTimeout.getEquipmentId(),-1);

        }else if(o instanceof NoteCheckDisconnectionTimeout){
            NoteCheckDisconnectionTimeout noteCheckDisconnectionTimeout = (NoteCheckDisconnectionTimeout) o;
            log.info("检测当日是否有失联门店 ");
            List<Map<String, Object>> disconnectStores = DBHelper.getDisconnectStores();
            if(disconnectStores != null && disconnectStores.size() > 0){
                log.info("今日失联门店有{}", disconnectStores );
            }
        }else if(o instanceof NoteCheckStatusOneSecondTimeout){ // 一秒一次查询
            NoteCheckStatusOneSecondTimeout noteCheckStatusOneSecondTimeout = (NoteCheckStatusOneSecondTimeout) o;

            // 处理iot任务查询
            this.processPendingIotTask();

            // 处理订单状态
            this.processNoteCheckOrderStatusTimeout();
            // 处理音频播放任务
            this.processNoteCheckVoiceTaskTimeout();

            // 处理配置更新查询
            this.processUpdataConfig();

            // 处理音频下载任务
            this.processNoteCheckDownloadVoiceTaskTimeout();
            this.context().system().scheduler().scheduleOnce(
                    FiniteDuration.apply(1, TimeUnit.SECONDS),
                    this.getSelf(),
                    NoteCheckStatusOneSecondTimeout.builder().build(),
                    this.context().dispatcher(),
                    this.getSelf());
        }else if(o instanceof NoteCheckDownloadVoiceDataTimeout){ // 一秒一次查询
            NoteCheckDownloadVoiceDataTimeout noteCheckDownloadVoiceDataTimeout = (NoteCheckDownloadVoiceDataTimeout) o;

            DownloadVoiceHelper.inspectVoice();

            // 处理音频下载任务

            this.context().system().scheduler().scheduleOnce(
                    FiniteDuration.apply(10, TimeUnit.MINUTES),
                    this.getSelf(),
                    NoteCheckDownloadVoiceDataTimeout.builder().build(),
                    this.context().dispatcher(),
                    this.getSelf());
        }



    }


    /**
     * 处理订单状态查询
     */
    private void processNoteCheckOrderStatusTimeout(){

        Set<String> newActiveStoresKeys = RedisHelper.getStoreIdWithOrders();
        if(newActiveStoresKeys != null && newActiveStoresKeys.size() > 0){
            Set<String> newActiveStores = newActiveStoresKeys.stream().map(key -> (Utils.getKeyFromString(key.split("\\:")[1], key.split("\\:")[2]))).collect(Collectors.toSet());
            // 过滤出需要开灯的门店，即newActiveStores中有，但是this.activeStores中没有，说明是新增门店
            Set<String> lightStores = newActiveStores.stream().filter(s -> !this.activeStores.contains(s)).collect(Collectors.toSet());
            // 过滤出需要关灯的门店，即this.activeStores中存在，但是newActiveStores中不存在，说明该门店已经没有订单了
            Set<String> downStores = this.activeStores.stream().filter(s -> !newActiveStores.contains(s)).collect(Collectors.toSet());
            if(lightStores.size() > 0){
                lightStores.forEach(storeId -> {
                    if(this.storeId2serverRefs.containsKey(storeId)){
                        this.storeId2serverRefs.get(storeId).tell(NoteLight.builder().build(), this.getSelf());
                    }
                });
            }
            if(downStores.size() > 0){
                downStores.forEach(storeId -> {
                    if(this.storeId2serverRefs.containsKey(storeId)){
                        this.storeId2serverRefs.get(storeId).tell(NoteDown.builder().build(), this.getSelf());
                    }
                });
            }
            // 更新有订单的门店
            this.activeStores = newActiveStores;
        }


    }

    private void processRegisterToServerHelper(RegisterToServerHelper registerToServerHelper){
        ActorRef serverRef = registerToServerHelper.getServerRef();
        Integer storeId = registerToServerHelper.getStoreId();
        int companyId = registerToServerHelper.getCompanyId();
        log.info("收到门店 {} 的注册服务actor的信息{}", registerToServerHelper.getEquipmentId(), storeId, registerToServerHelper);
        if(serverRef != null){
            this.storeId2serverRefs.put(Utils.getKeyFromInteger(companyId, storeId), serverRef);
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
                        .equipmentId(noteCheckDisconnect.getEquipmentId())
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


    /**
     * 处理音频下载任务
     */
    private void processNoteCheckDownloadVoiceTaskTimeout(){

        // 获取需要下载的音频
        List<Map<String, Object>> pendingVoiceTasks =  DBHelper.getPendingVoiceTask();

        if(pendingVoiceTasks != null && pendingVoiceTasks.size() > 0){
            // 逐个处理下载任务   storeId2serverRefs
            for(Map<String, Object> pendingVoiceTask:pendingVoiceTasks){
                // id, store_id, company_id, voice_id, sd_index, sd_version
                int storeId = Utils.convertToInt(pendingVoiceTask.get("store_id"), -1);
                int companyId = Utils.convertToInt(pendingVoiceTask.get("company_id"), -1);
                int voiceId = Utils.convertToInt(pendingVoiceTask.get("voice_id"), -1);
                int downloadPlace = Utils.convertToInt(pendingVoiceTask.get("sd_index"), -1);
                boolean isUpdate = Utils.convertToInt(pendingVoiceTask.get("is_update"), 0) == 0 ? false : true;
                String pendingKey = Utils.getKeyFromInteger(companyId, storeId);
                if(this.storeId2serverRefs.containsKey(pendingKey)){
                    // 准备下载数据
                    DownloadVoiceHelper.addVoice( voiceId, companyId,  isUpdate);
                    // 更新下载状态
                    DBHelper.updateVoiceTask(storeId, companyId, voiceId, downloadPlace, 1, "下载中");
                    // 通知actor生成下载任务
                    this.storeId2serverRefs.get(pendingKey).tell(NoteServerAssisatantDownloadVoice.builder()
                            .downloadPlace(downloadPlace)
                            .voiceId(voiceId)
                            .build(), this.getSelf());
                }else {
                    // 获取到音频下载任务时刻没有心跳，取消该门店下载
                    log.info("门店{}没有心跳，失败当前音频{}下载任务", storeId, voiceId);
                    DBHelper.updateVoiceTask(storeId, companyId, voiceId, downloadPlace, -1,"当前门店没有心跳上报，取消此次下载");
                }
            }

        }
    }

    /**
     * 查询是否有音频播放任务
     */
    private void processNoteCheckVoiceTaskTimeout(){
        Set<String> newActiveStoresKeys = RedisHelper.getStoreVoiceTask();
        if(newActiveStoresKeys != null && newActiveStoresKeys.size() > 0){
            newActiveStoresKeys.forEach(acticeKey -> {
                String[] acticeKeyArray = acticeKey.split("\\:");
                String companyId = acticeKeyArray[1];
                String storeId = acticeKeyArray[2];
                String storeKey = Utils.getKeyFromString(companyId, storeId);
                int voiceId = Integer.parseInt(acticeKeyArray[3]);
                int helperId = Integer.parseInt(acticeKeyArray[4]);
                long id = Long.parseLong(acticeKeyArray[5]);
                int status = Integer.parseInt(acticeKeyArray[6]);
                String value = RedisHelper.getVoiceValue(acticeKey);
                Map<String, Object> msgMap = null;
                if(value != null ){
                    msgMap = JSON.parseObject(value, Map.class);
                }

                //<模块名>:<商户编号>:<门店编号>:<音频ID>:<坐席编号>:<任务主键ID>:<状态>
                if(this.storeId2serverRefs.containsKey(storeKey)){
                    this.storeId2serverRefs.get(storeKey).tell(
                            NoteVoiceTask.builder()
                                    .companyId(companyId)
                                    .storeId(storeId)
                                    .helperId(helperId)
                                    .id(id)
                                    .voiceId(voiceId)
                                    .status(status)
                                    .times(Utils.convertToInt(msgMap.get("times"), 1))
                                    .volume(Utils.convertToInt(msgMap.get("volume"), 22))
                                    .interval(Utils.convertToInt(msgMap.get("interval"), 0))
                                    .player(Utils.convertToInt(msgMap.get("player"), 1))
                                    .createTime(Utils.convertToLong(msgMap.get("create_time"), 0))
                                    .enableTime(Utils.convertToInt(msgMap.get("enable_time"), 5000))
                                    .build(), this.getSelf());

                }
                // 清掉redis
                RedisHelper.delVoiceKey(acticeKey);
            });
        }
    }

    /**
     * 获取待处理的开关门任务
     */
    private void processPendingIotTask(){
        List<RedisIotTaskGet> pengdingTask = RedisHelper.checkPendingIotTaskAndAutoOpenDoor();
        Map<String, List<NoteServerAssistantIotTask>> storeId2PengdingTask= new HashMap<>();
        if(pengdingTask != null && pengdingTask.size() > 0){
            pengdingTask.forEach(task -> {
                int companyId = task.getCompanyId();
                int storeId = task.getStoreId();
                int status = task.getStatus();
                long id = task.getId();
                String storeKey = Utils.getKeyFromInteger(companyId, storeId);
                if(status == 9) {
                    // 更新mysql
                    DBHelper.updateIotTaskByIdWithoutRedis(id, 1);
                }

                NoteServerAssistantIotTask noteServerAssistantIotTask = NoteServerAssistantIotTask.builder()
                        .companyId(companyId)
                        .storeId(storeId)
                        .helperId(task.getHelperId())
                        .id(id)
                        .actionType(task.getActionType())
                        .status(status).build();
                if(storeId2PengdingTask.containsKey(storeKey)){
                    storeId2PengdingTask.get(storeKey).add(noteServerAssistantIotTask);
                }else {
                    List<NoteServerAssistantIotTask> pendingTasks = new LinkedList<>();
                    pendingTasks.add(noteServerAssistantIotTask);
                    storeId2PengdingTask.put(storeKey, pendingTasks);
                }
            });

            if(storeId2PengdingTask.keySet().size() > 0){
                for(Map.Entry<String, List<NoteServerAssistantIotTask>> store2tasks : storeId2PengdingTask.entrySet()){
                    store2tasks.getValue().sort(Comparator.comparing(NoteServerAssistantIotTask::getId)); // 升序排序 保证id大的在排在后边处理
                        store2tasks.getValue().forEach(e -> {
                            if(this.storeId2serverRefs.containsKey(store2tasks.getKey())){
                                this.storeId2serverRefs.get(store2tasks.getKey()).tell(e, this.getSelf());
                            }else{
                                log.info("门店{}在ServertHeleper中没有找到server assistan, 失败iot任务{}", e.getStoreId() ,e.getId());
                                DBHelper.updateIotTaskByIdWithoutRedis(e.getId(), -1);
                            }
                        });
                }
            }
//            if(this.storeId2serverRefs.containsKey(storeKey)){
//                this.storeId2serverRefs.get(storeKey).tell(NoteServerAssistantIotTask.builder()
//                        .companyId(companyId)
//                        .storeId(storeId)
//                        .helperId(task.getHelperId())
//                        .id(id)
//                        .actionType(task.getActionType())
//                        .status(status).build(), this.getSelf());
//            }else{
//                log.info("门店{}在ServertHeleper中没有找到server assistan, 失败iot任务{}", storeId, id);
//                DBHelper.updateIotTaskByIdWithoutRedis(id, -1);
//            }
        }

    }

    private void processUpdataConfig(){
        List<Map<String, Object>> needUpdateStore = DBHelper.getNeedUpdateStore();
        if(needUpdateStore != null && needUpdateStore.size() > 0){
            for(Map<String, Object> config : needUpdateStore){ //         String sql = "select ,  store_id , id from stores_stm_config  WHERE is_reload_config = 1" ;

                int companyId = Utils.convertToInt(config.get("company_id"), -1);
                int storeId = Utils.convertToInt(config.get("store_id"), -1);
                long id = Utils.convertToLong(config.get("id"), -1);
                String storeKey = Utils.getKeyFromInteger(companyId, storeId);
                if(this.storeId2serverRefs.containsKey(storeKey)){
                    this.storeId2serverRefs.get(storeKey).tell(NoteServerAssistantUpdateConfig.builder()
                            .companyId(companyId)
                            .storeId(storeId)
                            .id(id)
                            .build(), this.getSelf());
                }else{
                    log.info("门店{}在ServertHeleper中没有找到server assistan, 失败更新配置任务{}", storeId, id);

                }

            }
        }
    }

    private void getTimeCurrent(){
        // 当前全日期
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy MM dd HH mm ss EEEE");
        this.currentTime = dateFormat.format(new Date()).split(" ");
        this.currentTime[6] = Utils.getWeek();
    }

//    private String getKey(int companyId, int storeId){
//        return companyId + "_" + storeId;
//    }
//    private String getKey(String companyId, String storeId){
//        return companyId + "_" + storeId;
//    }
}


