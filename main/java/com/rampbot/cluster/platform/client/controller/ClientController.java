package com.rampbot.cluster.platform.client.controller;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import com.alibaba.fastjson.JSON;
import com.mysql.jdbc.StringUtils;
import com.rampbot.cluster.platform.client.utils.BuildResponse;
import com.rampbot.cluster.platform.client.utils.DBHelper;
import com.rampbot.cluster.platform.client.utils.DownloadVoiceHelper;
import com.rampbot.cluster.platform.client.utils.Utils;
import com.rampbot.cluster.platform.domain.*;
import com.rampbot.cluster.platform.server.manager.Server;
import lombok.extern.slf4j.Slf4j;

import java.util.*;


@Slf4j
public class ClientController extends UntypedActor {

    private final ActorRef serverRef;
    private final ActorRef servertHeleper;
    private Long baseTaskId = 10L;
    private final Server server;
    private ActorRef serverAssistantRef;
    private final String equipmentId; // 门店序列号
    private int storeId; // 门店编号
    private String key; // 密钥
    private  String storeName; // 门店名称
    private int companyId;
    private int firmwareVersion = 0;
    private int retryDownloadTimes = 0;
    private LogAssistant logAssistant;

//    {"log":"2023-04-11 23:17:16,265 INFO  c.r.c.p.c.c.ClientController   - Receive 2202270710241 msg 100/195/2/966/226235251\n","stream":"stdout","time":"2023-04-11T15:17:16.265324408Z"}
//    {"log":"2023-04-11 23:17:16,265 INFO  c.r.c.p.n.t.controller.TcpController   - Reply 2202270710241 msg {\"msg\":\"\",\"code\":0,\"data\":\"[]\",\"time\":226236265}\n","stream":"stdout","tim

    private int lastStatus = 0;
    private int lastAction = 0;
    private int lastMaxVoiceVersion = 0;
    private int lastFirmwareVersion = 0;
    private int numOfErrorStats = 0;   // 本来有下载任务，但是客户端一直发送100，说明是错误的，记录错误数量




    private int receiveStopTimes = 0; // 收到停止的的次数，大于等于2，就自我刹车

    List<Task> setWrzsStatusTask = new LinkedList<>(); // 服务端生成的播放语音的任务
    List<Task> doorTask = new LinkedList<>(); // 服务端生成的门状态改变的iot任务
    List<VoiceTask> downloadVoiceTask = new LinkedList<>(); // 服务端生成的下载语音的任务
    List<Task> playVoiceTask = new LinkedList<>(); // 服务端生成的播放语音的任务
    List<Task> otherTask = new LinkedList<>(); // 1 703因为客户端不需要回复暂不加入 2 目前加入704下载音频结束
    List<Task> noResponseTask = new LinkedList<>(); //不需要客户端反馈的任务

    public ClientController(Server server, String equipmentId, ActorRef servertHeleper, ActorRef serverRef){
        this.equipmentId = equipmentId;
        this.serverRef = serverRef;
        this.servertHeleper = servertHeleper;
        this.server = server;
    }

//    List<Map<String, Object>> iotTaskList = SQLHelper.executeQueryTable(sql);


    @Override
    public void onReceive(Object o) throws Throwable {
        if(o instanceof String){
            String msg = (String) o;
//            log.info("管理员{}收到消息{}", equipmentId, msg);
            this.processClientMsg(msg);
         }else if(o instanceof NoteControllerTask){
            NoteControllerTask msg = (NoteControllerTask) o;
            this.processServerTask(msg);
        }else if(o instanceof NoteClientStop){
            NoteClientStop msg = (NoteClientStop) o;
            this.sendStopToTcpcontroller();
        }else if(o instanceof NoteClientControllerReloadConfig){
            NoteClientControllerReloadConfig msg = (NoteClientControllerReloadConfig) o;
            this.serverAssistantRef.tell(NoteServerAssistantReloadConfig.builder().build(), this.getSelf());
        }else if(o instanceof NoteStoreConfigStatus){
            NoteStoreConfigStatus msg = (NoteStoreConfigStatus) o;
            log.info("门店{}控制器收到消息{}", this.equipmentId, msg);
            DBHelper.setConfigStatus(this.companyId, this.storeId, msg.getStatus());
            DBHelper.addWorkStatusLog(this.companyId, this.storeId, this.storeName, this.equipmentId, 1);
        }else if(o instanceof NoteClientControllerDownloadVoice){
            NoteClientControllerDownloadVoice noteClientControllerDownloadVoice = (NoteClientControllerDownloadVoice) o;
            log.info("门店{}收到音频下载任务{}", this.equipmentId, noteClientControllerDownloadVoice);
            this.processnoteClientControllerDownloadVoice(noteClientControllerDownloadVoice);

        }

    }

    public void postStop(){
        log.info("门店{}客户端 {} 停止任务",this.equipmentId, this.getSelf());
    }

    public void preStart() {
        // TODO: 2022/10/14 获取门店号、key、公司号
        // company_id=10050, private_key=d8987a72374346809ee0925cba75629c, id=10011
//        this.storeId = DBHelper.getStoreIdBySerialNumber(this.equipmentId);
// store_id,  private_key, company_id,
        Map<String, Object> storeMsgMap = DBHelper.getStoreIdAndCompanyIdBySerialNumber(this.equipmentId);
        if(storeMsgMap == null || storeMsgMap.size() == 0){
            log.info("门店{}未在数据库查到配置",this.equipmentId);
            String msg = BuildResponse.buildResponseMsgError(-101, "设备序列号不存在", "");
            this.sendMsg(msg);
            return;
        }
        this.storeId = Utils.convertToInt(storeMsgMap.get("store_id"), -1);
        this.key = storeMsgMap.get("private_key").toString();
        this.companyId = Utils.convertToInt(storeMsgMap.get("company_id").toString(), -1);

        Map<String, Object> storeMap = DBHelper.getStoreMap(this.storeId, this.companyId);
        if(storeMap != null && storeMap.size() > 0){
            this.storeName = storeMap.get("name").toString();
        }


        log.info("门店编号 {}  密钥 {}  公司编号 {}", storeId, key, companyId);
        // 生成actor阶段校验，校验门店号
        if (this.storeId < 1) {
            String msg = BuildResponse.buildResponseMsgError(-101, "设备序列号不存在", "");
            this.sendMsg(msg);
        }else {
            // 回复一个空心跳
            this.sendMsg(BuildResponse.buildResponseMsg(0,"",""));
        }
        log.info("盒子{}门店的编号为{} 密钥为{}", this.equipmentId, this.storeId, this.key);

        long logId = DBHelper.getStoreLogsId(this.storeId, this.companyId);
        this.logAssistant = new LogAssistant(this.companyId, this.storeId, logId);

        this.serverAssistantRef =  this.getContext().actorOf(Props.create(ServerAssistant.class, this.getSelf(), this.storeId, this.companyId, this.equipmentId, this.servertHeleper, this.logAssistant), "ServerAssistant." +  this.storeId + "." + System.currentTimeMillis());
    }


    private void getRemainTaskSize(){
//        List<Task> setWrzsStatusTask = new LinkedList<>(); // 服务端生成的播放语音的任务
//        List<Task> doorTask = new LinkedList<>(); // 服务端生成的门状态改变的iot任务
//        List<VoiceTask> downloadVoiceTask = new LinkedList<>(); // 服务端生成的下载语音的任务
//        List<Task> playVoiceTask = new LinkedList<>(); // 服务端生成的播放语音的任务
//        List<Task> otherTask = new LinkedList<>(); // 1 703因为客户端不需要回复暂不加入 2 目前加入704下载音频结束
//        log.info("门店{}剩余任务数量为 {} {} {} {} {}",this.equipmentId, setWrzsStatusTask.size(), doorTask.size(), downloadVoiceTask.size(), playVoiceTask.size(), otherTask.size());
    }

    /**
     * 接受处理下载音频任务
     */
    private void processnoteClientControllerDownloadVoice(NoteClientControllerDownloadVoice noteClientControllerDownloadVoice){
        int downloadPlace = noteClientControllerDownloadVoice.getDownloadPlace();
        int voiceId= noteClientControllerDownloadVoice.getVoiceId();
        int clientDownloadName = DownloadVoiceHelper.getVoiceClientDownloadName(voiceId);
        if(DownloadVoiceHelper.isContainVoice(voiceId) && clientDownloadName != -1){
            VoiceTask voiceTask = VoiceTask.builder()
                    .voiceId(voiceId)
                    .clientDownloadName(clientDownloadName)
                    .taskStatus(TaskStatus.pending)
                    .downloadPlace(downloadPlace)
                    .taskId(this.newTaskId())
                    .downloadIndex(0)
                    .version(DownloadVoiceHelper.getVersion(voiceId))
                    .build();
            this.downloadVoiceTask.add(voiceTask);
        }else {
            log.info("门店{}无法在缓存中获取音频{}数据, 客户端下载名 {}，失败该下载", this.equipmentId, voiceId, clientDownloadName);
            DBHelper.updateVoiceTask(this.storeId, this.companyId, voiceId, downloadPlace, -1, "无法在缓存中获取音频数据，取消此次下载");

        }
    }

    /**
     * 处理服务端任务
     * @param msg
     */
    private void processServerTask(NoteControllerTask msg ){
        log.info("门店{}收到服务端新发的任务{}",this.equipmentId, msg);
        if(msg.getDoorTask() != null && msg.getDoorTask().size() > 0){
            log.info("门店{}添加开关门任务 {}",this.equipmentId, msg.getDoorTask());
            this.doorTask.addAll(msg.getDoorTask());
        }

        if(msg.getSetWrzsStatusTask() != null && msg.getSetWrzsStatusTask().size() > 0){
            log.info("门店{}添加无人值守任务 {}",this.equipmentId, msg.getSetWrzsStatusTask());
            this.setWrzsStatusTask.addAll(msg.getSetWrzsStatusTask());
        }

        if(msg.getPlayVoiceTask() != null && msg.getPlayVoiceTask().size() > 0){
            List<Task> playVoiceTask = msg.getPlayVoiceTask();
//            log.info("查看 新增加的播放任务{}", playVoiceTask);
            for(Task task : playVoiceTask){
                Long taskId = this.newTaskId();
                task.setTaskId(taskId);
                task.getTask().put("task_id", taskId);
//                log.info("查看 新增加的播放任务{}", task);
            }
            log.info("门店{}添加加的播放任务{}", equipmentId, playVoiceTask);
            this.playVoiceTask.addAll(playVoiceTask);
        }

        // 下载任务不从这里触发了
//        if(msg.getDownloadVoiceTask() != null && msg.getDownloadVoiceTask().size() > 0){
//
//
//            // 为每一个音频下载任务赋值taskId
//            List<VoiceTask> downloadTask = msg.getDownloadVoiceTask();
//            for(int i = 0; i < downloadTask.size(); i++){
//                downloadTask.get(i).setTaskId(this.newTaskId());
//            }
//            this.downloadVoiceTask.addAll(downloadTask);
//        }

        if(msg.getNoResponseTask() != null && msg.getNoResponseTask().size() > 0){
            List<Task> noResponseTask = msg.getNoResponseTask();
            for(int i = 0; i < noResponseTask.size(); i++){
                noResponseTask.get(i).setTaskId(this.newTaskId());
            }log.info("门店{}添加不需要监控反馈的任务{}", equipmentId, noResponseTask);
            this.noResponseTask.addAll(noResponseTask);
        }


    }


    /**
     * 处理客户端上报信息
     * @param clientMsg
     */
    private void processClientMsg(String clientMsg){
        // TODO: 2022/10/14 解析、安全验证
        // TODO: 2022/10/15 获取数据库内容：无人值守状态、门状态、iot任务
        // TODO: 2022/10/14 心跳处理
        // TODO: 2022/10/14 给客户端返回结果
        // TODO: 2022/10/15 跟新数据库状态


        // 测试代码，查看剩余任务数量
         this.getRemainTaskSize();

        // 1 解析数据
        Map<String, Object> msgMap = this.getMsgMap(clientMsg);

//        log.info("查看解析数据{}", msgMap);
        // 2 校验安全
        boolean isOk = false;
        if(msgMap != null){
            isOk = this.verificaMsg(msgMap);
        }

        // 3 处理数据
        if(isOk){
            this.processClientAction(msgMap);
        }



    }

    /**
     * 解析心跳内容
     * @param clientMsg
     * @return
     */
    private  Map<String, Object> getMsgMap(String clientMsg){
        Map<String, Object> msgMap = null;

        if (StringUtils.isNullOrEmpty(clientMsg)) {
            String msg = BuildResponse.buildResponseMsgError(-1, "内容为空", "");
            this.sendMsg(msg);
            return  null;
        }




        try {
            msgMap = JSON.parseObject(this.getOneHeart(clientMsg), Map.class);
//            msgMap = JSON.parseObject(clientMsg, Map.class);
        } catch (Exception e) {
        }
        if (msgMap == null || msgMap.size() < 1) {
            String msg = BuildResponse.buildResponseMsgError(-2, "内容格式错误2", "");
            log.info("收到的消息为{}", clientMsg);
            this.sendMsg(msg);
            return  null;
        }
        return msgMap;
    }



    /**
     * 处理盒子上报的action
     * 这里处理tcp上报的数据
     *
     * @param msgMap
     */
    private void processClientAction(Map<String, Object> msgMap){
        Integer action = Utils.convertToInt(msgMap.get("action"), -1);



        // 缩少日志打印
        int lastStatus = Utils.convertToInt(msgMap.get("status"), 0);
        int lastAction = Utils.convertToInt(msgMap.get("action"), 0);
        int lastMaxVoiceVersion = Utils.convertToInt(msgMap.get("maxVoiceVersion"), 0);
        int lastFirmwareVersion = Utils.convertToInt(msgMap.get("firmwareVersion"), 0);

        if( this.lastStatus != lastStatus || this.lastAction != lastAction || this.lastMaxVoiceVersion != lastMaxVoiceVersion || this.lastFirmwareVersion != lastFirmwareVersion ){

            if(msgMap.containsKey("action") && msgMap.containsKey("status")){
                String statusTen = msgMap.get("status").toString();

                logAssistant.addLog("client", "R " +  msgMap.get("action").toString() + "/" + Integer.toBinaryString(Integer.parseInt(statusTen)));

            }

            log.info("Receive {} msg {}/{}/{}/{}/{}",this.equipmentId, msgMap.get("action"),msgMap.get("maxVoiceVersion"), msgMap.get("firmwareVersion"),msgMap.get("status"), msgMap.get("time"));
        }
        //log.info("Receive {} msg {}/{}/{}/{}/{}",this.equipmentId, msgMap.get("action"),msgMap.get("maxVoiceVersion"), msgMap.get("firmwareVersion"),msgMap.get("status"), msgMap.get("time"));


        this.lastStatus = lastStatus;
        this.lastAction = lastAction;
        this.lastMaxVoiceVersion = lastMaxVoiceVersion;
        this.lastFirmwareVersion = lastFirmwareVersion;


        switch (action) {
            case 100:   // action 100 心跳包

//                this.getLog();

                int status = -1;
                int maxVoiceVersion = -1;
                int firmwareVersion = -1;


                if (msgMap.containsKey("maxVoiceVersion")) {
                    maxVoiceVersion = Utils.convertToInt(msgMap.get("maxVoiceVersion"), -1);
                }
                if (msgMap.containsKey("firmwareVersion")) {
                    firmwareVersion = Utils.convertToInt(msgMap.get("firmwareVersion"), -1);
                    this.firmwareVersion = firmwareVersion;
                }
                byte[] staByteAry = Utils.intToByteArray(status);
                if (staByteAry == null || staByteAry.length < 1) {
//                    Utils.writeDebugLog("[action:100] 收到状态转byte失败：" + status);
                }

                if (msgMap.containsKey("status")) {
                    status = Utils.convertToInt(msgMap.get("status"), -1);
                }

                String statusStr = Utils.standardStatus(status);
                int statusStrLength = statusStr.length();

                // is_door_real_close_two << 9  门2检测状态                          服务端使用
                // is_door_real_close_one << 8  门1检测状态                          服务端使用
                // is_init << 7                 系统是否对该盒子完成了初始化             门店端使用
                // is_wrzs << 6                 是否开启了无人值守                     门店端使用
                // is_poweroff << 5             是否断电                             服务端使用
                // is_help_in << 4              是否发出门内救助信号                   服务端使用
                // is_help_out << 3             是否发出门外求助信号                   服务端使用
                // is_door_closed_one << 2      门1逻辑状态                          门店端使用
                // is_door_closed_two << 1      门2逻辑状态                          门店端使用

                // 获取盒子状态
                int is_door_closed_two = Integer.parseInt(statusStr.substring(statusStrLength-2, statusStrLength-1));
                int is_door_closed_one = Integer.parseInt(statusStr.substring(statusStrLength-3, statusStrLength-2));
                int is_help_out = Integer.parseInt(statusStr.substring(statusStrLength-4, statusStrLength-3));
                int is_help_in = Integer.parseInt(statusStr.substring(statusStrLength-5, statusStrLength-4));
                int is_poweroff = Integer.parseInt(statusStr.substring(statusStrLength-6, statusStrLength-5));
                int is_wrzs = Integer.parseInt(statusStr.substring(statusStrLength-7, statusStrLength-6));
                int is_init = Integer.parseInt(statusStr.substring(statusStrLength-8, statusStrLength-7));
                int is_door_real_close_one = Integer.parseInt(statusStr.substring(statusStrLength-9, statusStrLength-8));
                int is_door_real_close_two = Integer.parseInt(statusStr.substring(statusStrLength-10, statusStrLength-9));
                int is_out_human_detected = Integer.parseInt(statusStr.substring(statusStrLength-11, statusStrLength-10));
                int is_in_human_detected = Integer.parseInt(statusStr.substring(statusStrLength-12, statusStrLength-11));


                /*
                盒子汇报状态的处理过程
                1 将必要的状态同步给server assistant
                2 优先处理逻辑：初始化、值守状态、服务端门iot、汇报门状态、下载任务、播放任务
                 */


                // 先处理服务端状态
                NoteServerClientStatus noteServerClientStatus = NoteServerClientStatus.builder()
                        .is_wrzs(is_wrzs)
                        .is_door_closed_one(is_door_closed_one)
                        .is_door_closed_two(is_door_closed_two)
                        .is_door_real_close_one(is_door_real_close_one)
                        .is_door_real_close_two(is_door_real_close_two)
                        .is_poweroff(is_poweroff)
                        .is_help_in(is_help_in)
                        .is_help_out(is_help_out)
                        .firmwareVersion(firmwareVersion)
                        .is_in_human_detected(is_in_human_detected)
                        .is_out_human_detected(is_out_human_detected)
                        .build();
                this.serverAssistantRef.tell(noteServerClientStatus, this.getSelf());


                // 处理客户端
                // 未开启初始化
                if(is_init != 1){
                    // 不维护反馈结果
                    Map<String, Object> map = new HashMap<>();
                    map.put("event", 803);
                    map.put("task_id", this.newTaskId());
                    List<Map> iotTaskList = new ArrayList<>();
                    iotTaskList.add(map);
                    String msg = BuildResponse.buildResponseMsg(0, "", iotTaskList);
                    this.sendMsg(msg);
                    logAssistant.addLog("server", "S " + "803");
                    return;
                }


                // 是否值守状态不一致
                if(this.setWrzsStatusTask.size() > 0){


                    Task task = this.setWrzsStatusTask.get(0);
                    log.info("有开关无人值守任务要处理 {}", task);
                    task.setTaskStatus(TaskStatus.sent);
                    this.sendMsg(BuildResponse.buildResponseMsgTask( 0, "", setWrzsStatusTask.get(0)));
                    logAssistant.addLog("server", "S " + task.getTask().get("action_type"));
                    return;
                }

                // 是否有服务端生成的门状态改变iot任务
                if(doorTask.size() > 0){
                    Task task = doorTask.get(0);
                    log.info("有开关门任务要处理 {}", task);
                    task.setTaskStatus(TaskStatus.sent);
                    this.sendMsg(BuildResponse.buildResponseMsgTask( 0, "", doorTask.get(0)));
                    logAssistant.addLog("server", "S " + task.getTask().get("action_type"));
                    return;
                }


                /**
                 * 下载音频任务的生命周期
                 * 1 发现有下载音频的任务，生成703任务通知客户端，将该任务不会有客户端返回结果，所以703发出去就不用理会了，同时将涉及的音频下载任务状态置为sent
                 * 2 客户端开始上报778要音频，服务端给音频
                 * 3 服务端返回最后一块音频，客户端在更新最后一段音频，（也会触发更新版本号，和长度），同时准备好704任务，加入到othertask中，如果客户端继续来要数据，就返回704任务
                 * 4 若客户端继续要778，则回复704的任务
                 * 5 若客户回复704，则在othertask中找到该704任务，同时在704任务中找到涉及的下载任务，完成该音频下载任务的更新
                 * 5 若客户端没有在4中继续发送778，则没有发送704任务，则客户端会继续上报100，此时音频下载任务还没完成更新，可以在这里触发更新未发送的704和下载任务
                 */
                // 是否有下载任务
                if(this.downloadVoiceTask.size() > 0){

                    VoiceTask task = this.downloadVoiceTask.get(0);
                    if(task.getTaskStatus().equals(TaskStatus.pending)){
                        task.setTaskStatus(TaskStatus.sent);


                        Map<String, Object> map = new HashMap<>();
                        if(this.firmwareVersion < 6){
                            map.put("event", 703);
                            map.put("update_voice_name", task.getDownloadPlace());
                            map.put("update_voice_length", task.getVoiceLength());
                            map.put("update_voice_version", task.getVersion());
                            logAssistant.addLog("server", "S " + 703 + "/" + task.getVoiceId());
                        }else{
                            map.put("event", 703);
                            map.put("update_voice_name", task.getClientDownloadName());
                            map.put("update_voice_length", task.getVoiceLength());
                            map.put("update_voice_version", task.getVersion());
                            map.put("box_index", task.getDownloadPlace() - 1);
                            logAssistant.addLog("server", "S " + 703 + "/" + task.getVoiceId());
                            this.retryDownloadTimes = 0; // 清空重试下载次数计数，保证单帧重复有效计数
                        }


                        List<Map> iotTaskList = new ArrayList<>();
                        iotTaskList.add(map);
                        String msg = BuildResponse.buildResponseMsg(0, "", iotTaskList);
                        this.sendMsg(msg);
                        return;
                    }else if(task.getTaskStatus().equals(TaskStatus.sent) && this.otherTask.size() > 0 && this.otherTask.stream().anyMatch(t -> t.getTask().get("downloadTaskId") != null && Utils.convertToLong(t.getTask().get("downloadTaskId"), -1).equals(task.getTaskId()))){
                        Task unSentTask = this.otherTask.stream().filter(t -> Utils.convertToLong(t.getTask().get("downloadTaskId"), -1).equals(task.getTaskId())).findFirst().get();
                        this.completeLocalTask(unSentTask.getTaskId());
                    }

                    if(task.getTaskStatus().equals(TaskStatus.sent)){
                        this.numOfErrorStats++; // 超过五个错误心跳，清掉第一个下载任务
                        if(this.numOfErrorStats > 5){
                            this.numOfErrorStats = 0;
                            log.info("门店{}主动清除音频下载中任务", this.equipmentId);
                            this.removeFristVoiceDownloadTask();
                        }
                    }

                }


                // 是否有播放任务
                if(this.playVoiceTask.size() > 0){
                    Task playVoiceTask = this.playVoiceTask.get(0);
                    if(playVoiceTask.getTaskStatus().equals(TaskStatus.pending)){
                        playVoiceTask.setTaskStatus(TaskStatus.sent);
                        Map<String, Object> map = playVoiceTask.getTask();
                        map.put("task_id", this.newTaskId());
                        List<Map> iotTaskList = new ArrayList<>();
                        iotTaskList.add(map);
                        String msg = BuildResponse.buildResponseMsg(0, "", iotTaskList);
                        logAssistant.addLog("server", "S " + 705 + "/" + map.get("update_voice_name")+ "/" + map.get("play_count")+ "/" + map.get("volume")+ "/" +map.get("box_index"));
                        this.sendMsg(msg);
                        return;
                    }

                    this.playVoiceTask.remove(0); // 不等待反馈，直接删除
                }


                // 是否有不需要回复的任务
                if(this.noResponseTask.size() > 0){
                    Task noResponseTask = this.noResponseTask.get(0);
                    Map<String, Object> map = noResponseTask.getTask();
                    map.put("task_id", this.newTaskId());
                    List<Map> iotTaskList = new ArrayList<>();
                    iotTaskList.add(map);
                    String msg = BuildResponse.buildResponseMsg(0, "", iotTaskList);
                    this.sendMsg(msg);
                    if(map.containsKey("action_type")){
                        logAssistant.addLog("server", map.get("action_type").toString());
                    }

                    this.noResponseTask.remove(0);
                    return;
                }



                this.sendMsg(BuildResponse.buildResponseMsg(0, "", ""));
                break;
            case 200:
                log.info("收到200回复 {} ", msgMap );
                // TODO: 2022/10/28 校验数据
                // TODO: 2022/10/28 修改类内记录状态与任务
                // TODO: 2022/10/28 通知服务端更新门状态、通知
                // TODO: 2022/10/28 回复盒子
                if (!msgMap.containsKey("task_id")) {

                    String msg = BuildResponse.buildResponseMsgError(-4, "参数 task_id 缺少", "");
                    this.sendMsg(msg);
                    return;
                }
                if (!msgMap.containsKey("result")) {
                    String msg = BuildResponse.buildResponseMsgError(-4, "参数 result 缺少", "");
                    this.sendMsg(msg);
                    return;
                }
                Long taskId = Utils.convertToLong(msgMap.get("task_id"), -1);
                Integer result = Utils.convertToInt(msgMap.get("result"), -1);
                if (taskId < 1) {
                    String msg = BuildResponse.buildResponseMsgError(0, "无法识别的taskId", "");
                    this.sendMsg(msg);
                    return;
                }
                if(result != 2){
                    String msg = BuildResponse.buildResponseMsgError(0, "", "");
                    this.sendMsg(msg);
                    return;
                }

                Task findTask = this.getTask(taskId);
                if(findTask != null){
                    log.info("门店{}要完成的任务{}",this.equipmentId, findTask);
                    findTask.setTaskStatus(TaskStatus.completed);
                    this.completeLocalTask(taskId);
                }
                this.sendMsg(BuildResponse.buildResponseMsg(0, "", ""));
                break;

            case 778:

                this.serverAssistantRef.tell(NoteServerUpdateHeartBeatTime.builder().build(), this.getSelf());
                this.numOfErrorStats = 0;

                // 有正在下载中的任务
                if(this.downloadVoiceTask.size() > 0 && this.downloadVoiceTask.get(0).getTaskStatus().equals(TaskStatus.sent)){
                    VoiceTask task = this.downloadVoiceTask.get(0);
                    byte[] downloadVoice = task.getVoice();

                    if(downloadVoice != null){
                        // 发送音频数据
                        this.sendMsg(DownLoadVoiceData.builder().voiceData(downloadVoice).build());
                        log.info("门店{}剩余音频长度 {} ", this.equipmentId, task.getRemainLength());
                        logAssistant.addLog("server", "S Remain Download Length " + task.getRemainLength());
                        // 已经是最后一块音频下载任务，准备结束任务
                        if(task.getRemainLength() == 0){
                            log.info("门店{}剩余音频长度 {} ", this.equipmentId, task.getRemainLength());
                            Map<String, Object> taskMap = new HashMap<>();
                            taskMap.put("event", 704);
                            taskMap.put("task_id", this.newTaskId());
                            taskMap.put("downloadTaskId", task.getTaskId());

                            Task finishDownloadTask = Task.builder()
                                    .taskStatus(TaskStatus.pending)
                                    .taskId(Utils.convertToLong(taskMap.get("task_id"), -1))
                                    .task(taskMap)
                                    .build();
                            this.otherTask.add(finishDownloadTask);
                        }
                    }else{

                        if(this.otherTask.size() > 0 && this.otherTask.stream().anyMatch(t -> t.getTask().get("downloadTaskId") != null && Utils.convertToLong(t.getTask().get("downloadTaskId"), -1).equals(task.getTaskId()))){
                            // 发送准备好的704
                            Task sentTask = this.otherTask.stream().filter(t -> Utils.convertToLong(t.getTask().get("downloadTaskId"), -1).equals(task.getTaskId())).findFirst().get();
                            sentTask.setTaskStatus(TaskStatus.sent);

                            Map<String, Object> map = new HashMap<>();
                            map.put("event", sentTask.getTask().get("event"));
                            map.put("task_id", sentTask.getTask().get("task_id"));

                            List<Map> iotTaskList = new ArrayList<>();
                            iotTaskList.add(map);
                            String msg = BuildResponse.buildResponseMsg(0, "", iotTaskList);
                            this.sendMsg(msg);
                            return;
                        }
                    }
                }
                break;


            case 779: // 重构下载机制，对于软件版本大于6以上的使用
                this.serverAssistantRef.tell(NoteServerUpdateHeartBeatTime.builder().build(), this.getSelf());
                this.numOfErrorStats = 0;

                int downloadIndexGet = -1;


                if (msgMap.containsKey("download_index")) {
                    downloadIndexGet = Utils.convertToInt(msgMap.get("download_index"), -1);
                }
                log.info("门店{}客户端上报779需要下载活跃帧为 {}",this.equipmentId, downloadIndexGet);


                // 有正在下载中的任务
                if(this.downloadVoiceTask.size() > 0 && this.downloadVoiceTask.get(0).getTaskStatus().equals(TaskStatus.sent)) {
                    VoiceTask task = this.downloadVoiceTask.get(0);



                    byte[] downloadVoice = task.getVoice();
                    int downloadIndex = task.getDownloadIndex();

                    if(downloadIndexGet != -1 && downloadIndexGet != downloadIndex){
                        log.info("门店{}上报的活跃帧和逻辑纪录逻辑帧不符，上报逻辑帧为{}，记录逻辑帧为{}，以上报逻辑帧为准重新获取数据", this.equipmentId, downloadIndexGet, downloadIndex);
                        task.setDownloadIndex(downloadIndexGet - 1);
                        downloadVoice = task.getVoice();
                        downloadIndex = task.getDownloadIndex();
                    }



                    if (downloadVoice != null && downloadVoice.length > 0) {
                        // 发送音频数据
                        this.retryDownloadTimes = 0; // 清空重试下载次数计数，保证单帧重复有效计数
                        this.sendMsg(DownLoadVoiceData.builder().voiceData(this.getVoiceDownloadData(downloadVoice, downloadIndex)).build());
                        log.info("门店{}剩余帧数 {} ",this.equipmentId, task.getRemainLength());
                        log.info("门店{}当前下载活跃帧 {} ",this.equipmentId, downloadIndex);
                        logAssistant.addLog("server", "S Remain Download Length " + task.getRemainLength());
                    }else{// 已经没有数据了结束当前下载任务
                        // 完成当前任务
                        this.serverAssistantRef.tell(NoteServerVoiceTaskStatus.builder().voiceTask(this.downloadVoiceTask.get(0)).status(2).failedReason("成功").build(), this.getSelf());
                        this.downloadVoiceTask.remove(0);
                        // 告知客户端结束下载
                        Map<String, Object> map = new HashMap<>();
                        map.put("event", 704);
                        map.put("task_id", this.newTaskId());

                        List<Map> iotTaskList = new ArrayList<>();
                        iotTaskList.add(map);
                        String msg = BuildResponse.buildResponseMsg(0, "", iotTaskList);
                        this.sendMsg(msg);
                    }
                }else{// 可能是客户端错误，结束第一个下载任务
                    this.serverAssistantRef.tell(NoteServerUpdateHeartBeatTime.builder().build(), this.getSelf());
                    if(this.downloadVoiceTask.size() > 0){
                        // 完成第一个任务
                        this.serverAssistantRef.tell(NoteServerVoiceTaskStatus.builder().voiceTask(this.downloadVoiceTask.get(0)).failedReason("无法获取到下载中的任务").status(-1).build(), this.getSelf());
                        this.downloadVoiceTask.remove(0);
                    }

                    // 告知客户端结束下载
                    Map<String, Object> map = new HashMap<>();
                    map.put("event", 704);
                    map.put("task_id", this.newTaskId());

                    List<Map> iotTaskList = new ArrayList<>();
                    iotTaskList.add(map);
                    String msg = BuildResponse.buildResponseMsg(0, "", iotTaskList);
                    this.sendMsg(msg);

                }
                break;

            case 780:// 客户端没有获取到正确的数据重新下载一次刚刚下载过的数据帧

                this.serverAssistantRef.tell(NoteServerUpdateHeartBeatTime.builder().build(), this.getSelf());
                this.numOfErrorStats = 0;

                int downloadIndexGet_780 = -1;

                if (msgMap.containsKey("download_index")) {
                    downloadIndexGet_780 = Utils.convertToInt(msgMap.get("download_index"), -1);
                }
                log.info("门店{}客户端780上报需要下载活跃帧为 {}",this.equipmentId, downloadIndexGet_780);

                if(this.retryDownloadTimes < 100 && this.downloadVoiceTask.size() > 0 && this.downloadVoiceTask.get(0).getTaskStatus().equals(TaskStatus.sent)) {
                    VoiceTask task = this.downloadVoiceTask.get(0);
                    byte[] downloadVoice = task.getLastDownloadVoiceData();
                    int downloadIndex = task.getDownloadIndex();

                    if(downloadIndexGet_780 != -1 && downloadIndexGet_780 != downloadIndex){
                        log.info("门店{}上报的活跃帧和逻辑纪录逻辑帧不符，上报逻辑帧为{}，记录逻辑帧为{}，以上报逻辑帧为准重新获取数据", this.equipmentId, downloadIndexGet_780, downloadIndex);
                        task.setDownloadIndex(downloadIndexGet_780 - 1);
                        downloadVoice = task.getVoice();
                        downloadIndex = task.getDownloadIndex();
                    }


                    if (downloadVoice != null) {
                        this.retryDownloadTimes++;
                        // 发送音频数据
                        this.sendMsg(DownLoadVoiceData.builder().voiceData(this.getVoiceDownloadData(downloadVoice, downloadIndex)).build());
                        log.info("门店{}重新触发下载，剩余帧数 {} ",this.equipmentId, task.getRemainLength());
                        log.info("门店{}当前下载活跃帧 {} ",this.equipmentId, downloadIndex);
                        logAssistant.addLog("server", "S Retry Download Remain Download Length " + task.getRemainLength());

                    }else{// 已经没有数据了结束当前下载任务
                        // 完成当前任务
                        this.serverAssistantRef.tell(NoteServerVoiceTaskStatus.builder().voiceTask(this.downloadVoiceTask.get(0)).failedReason("无法获取到下载数据779").status(-1).build(), this.getSelf());
                        this.downloadVoiceTask.remove(0);
                        // 告知客户端结束下载
                        Map<String, Object> map = new HashMap<>();
                        map.put("event", 704);
                        map.put("task_id", this.newTaskId());

                        List<Map> iotTaskList = new ArrayList<>();
                        iotTaskList.add(map);
                        String msg = BuildResponse.buildResponseMsg(0, "", iotTaskList);
                        this.sendMsg(msg);
                    }
                }else {
                    if(this.downloadVoiceTask.size() > 0){
                        // 完成第一个任务
                        String failedReason = this.retryDownloadTimes >= 100 ? "重试下载一帧音频超过100次，失败此次下载" :  "无法获取到下载数据780";
                        this.serverAssistantRef.tell(NoteServerVoiceTaskStatus.builder().voiceTask(this.downloadVoiceTask.get(0)).failedReason(failedReason).status(-1).build(), this.getSelf());
                        this.downloadVoiceTask.remove(0);
                    }

                    // 告知客户端结束下载
                    Map<String, Object> map = new HashMap<>();
                    map.put("event", 704);
                    map.put("task_id", this.newTaskId());

                    List<Map> iotTaskList = new ArrayList<>();
                    iotTaskList.add(map);
                    String msg = BuildResponse.buildResponseMsg(0, "", iotTaskList);
                    this.sendMsg(msg);
                }



                break;






            case 800:  // action 800 门店断电了
                break;

        }
    }


    /**
     * 包装带校验的音频数据
     * 起始校验位 AABB 2
     * 数据长度  FFFF 2
     * 数据     DATA n
     * 结束校验位 BBAA 2
     * 下载活跃帧标志位 FFFF 2
     */
    private byte[] getVoiceDownloadData(byte[] voiceData, int downloadIndex){
        byte[] voiceDownloadData = new byte[voiceData.length + 8];

        // 校验位
        voiceDownloadData[0] = (byte)0xaa;
        voiceDownloadData[1] = (byte)0xbb;
        voiceDownloadData[voiceDownloadData.length-2] = (byte)0xbb;
        voiceDownloadData[voiceDownloadData.length-1] = (byte)0xaa;

        // 数据长度
        byte[] length = Utils.intToBytes(voiceData.length);
        voiceDownloadData[2] = length[0];
        voiceDownloadData[3] = length[1];


        // 下载活跃帧标志位
        byte[] downloadIndexByte = Utils.intToBytes(downloadIndex);
        voiceDownloadData[4] = downloadIndexByte[0];
        voiceDownloadData[5] = downloadIndexByte[1];

        //数据
        for(int i = 0; i< voiceData.length; i++){
            voiceDownloadData[i + 6] = voiceData[i];
        }


        return voiceDownloadData;
    }

    /**
     * 根据taskId寻找task
     * @param taskId
     * @return
     */
    private Task getTask(Long taskId){
        Task findTask ;
        findTask = this.doorTask.stream().anyMatch(t -> t.getTaskId().equals(taskId)) ? this.doorTask.stream().filter(t -> t.getTaskId().equals(taskId)).findFirst().get() : null;

        if(findTask == null ){
            findTask = this.setWrzsStatusTask.stream().anyMatch(t -> t.getTaskId().equals(taskId)) ? this.setWrzsStatusTask.stream().filter(t -> t.getTaskId().equals(taskId)).findFirst().get() : null;
        }

        if(findTask == null ){
//            findTask = this.downloadVoiceTask.stream().anyMatch(t -> t.getTaskId().equals(taskId)) ? this.downloadVoiceTask.stream().filter(t -> t.getTaskId().equals(taskId)).findFirst().get() : null;
        }

        if(findTask == null ){
            findTask = this.otherTask.stream().anyMatch(t -> t.getTaskId().equals(taskId)) ? this.otherTask.stream().filter(t -> t.getTaskId().equals(taskId)).findFirst().get() : null;
        }

        if(findTask == null ){
            findTask = this.playVoiceTask.stream().anyMatch(t -> t.getTaskId().equals(taskId)) ? this.playVoiceTask.stream().filter(t -> t.getTaskId().equals(taskId)).findFirst().get() : null;
        }
        return findTask;
    }

    /**
     * 删除已经完成的task
     * @param taskId
     */
    private void completeLocalTask(Long taskId){

        if(this.doorTask.size() > 0 && this.doorTask.get(0).getTaskId().equals(taskId)){

            this.serverAssistantRef.tell(NoteServerTaskStatus.builder().task(this.doorTask.get(0)).build(), this.getSelf());

            this.doorTask.remove(0);

            if(this.doorTask.size() > 5){
                log.info("门店{}积累了超过五个doorTask任务{}",this.equipmentId, this.doorTask);
                this.doorTask.clear();
            }
        }else if(this.playVoiceTask.size() > 0 && this.playVoiceTask.get(0).getTaskId().equals(taskId)){
//            log.info("查看 移除播放任务{}", this.playVoiceTask.get(0));
            this.playVoiceTask.remove(0);

            if(this.playVoiceTask.size() > 5){
                log.info("门店{}积累了超过五个任务{}",this.equipmentId, this.playVoiceTask);
                this.playVoiceTask.clear();
            }
        }else if(this.setWrzsStatusTask.size() > 0 && this.setWrzsStatusTask.get(0).getTaskId().equals(taskId)){
            this.serverAssistantRef.tell(NoteServerTaskStatus.builder().task(this.setWrzsStatusTask.get(0)).build(), this.getSelf());

            this.setWrzsStatusTask.remove(0);

            if(this.setWrzsStatusTask.size() > 5){
                log.info("门店{}积累了超过五个任务{}",this.equipmentId, this.setWrzsStatusTask);
                this.setWrzsStatusTask.clear();
            }

        }else if(this.otherTask.size() > 0 && this.otherTask.stream().anyMatch(t -> t.getTaskId().equals(taskId))){
            Task completeTask = this.otherTask.stream().filter(t -> t.getTaskId().equals(taskId)).findFirst().get();
            if(completeTask.getTask().get("event").toString().equals("704")){
                log.info("门店{}下载任务{}需要完成", this.equipmentId, completeTask);
                if(this.downloadVoiceTask.get(0).getTaskId().equals(Utils.convertToLong(completeTask.getTask().get("downloadTaskId"), -1))){
                    this.serverAssistantRef.tell(NoteServerVoiceTaskStatus.builder().voiceTask(this.downloadVoiceTask.get(0)).failedReason("成功").status(2).build(), this.getSelf());
                    this.downloadVoiceTask.remove(0);

                }
            }
            this.otherTask.remove(completeTask);

            if(this.otherTask.size() > 5){
                log.info("门店{}积累了超过五个任务{}",this.equipmentId, this.otherTask);
                this.otherTask.clear();

            }
        }
    }


    /**
     * 验证盒子数据正确性
     * @param msgMap
     * @return
     */
    private boolean verificaMsg(Map<String, Object> msgMap){

        // TODO: 2022/10/16 增加time审核

        // 校验action和sid是否存在
        if (!msgMap.containsKey("sid") || !msgMap.containsKey("action")) {
            String msg = BuildResponse.buildResponseMsgError(-3, "缺少参数", "");
            log.info("{}缺少sid或者action", this.storeId);
            this.sendMsg(msg);
            return false;
        }

        String data = "";
        if (msgMap.containsKey("data")) {
            data = msgMap.get("data").toString();
        }


        // 校验time和sign是否存在
        if (!msgMap.containsKey("time") || !msgMap.containsKey("sign")) {
            String msg = BuildResponse.buildResponseMsgError(-3, "缺少参数(time||sign)", "");
            log.info("{}缺少time或者sign", this.storeId);
            this.sendMsg(msg);
            return false;
        }

        // 校验签名
        Long time = Utils.convertToLong(msgMap.get("time").toString(), -1);
        String sign = msgMap.get("sign").toString();
        if (!BuildResponse.buildSign(time, storeId, data, key).equalsIgnoreCase(sign.substring(0,32))) {
            String msg = BuildResponse.buildResponseMsgError(-9, "签名错误", "");
            log.info("门店{}签名错误,查看正确的签名{}, 收到的消息为 {}", this.equipmentId, BuildResponse.buildSign(time, this.storeId, data, this.key), msgMap);
            this.sendMsg(msg);
            return false;
        }

        return true;
    }


    /**
     * 给tcpController发送消息
     * @param msg
     */
//    private void sendMsg(String msg){
//        this.tcpControllerRef.tell(msg, this.getSelf());
//    }
//
//    private void sendMsg(DownLoadVoiceData msg){
//        this.tcpControllerRef.tell(msg, this.getSelf());
//    }
    private void sendMsg(String msg){
        this.server.getEquipmentId2TcpControllerRef().get(this.equipmentId).tell(msg, this.getSelf());
    }

    private void sendMsg(DownLoadVoiceData msg){
        this.server.getEquipmentId2TcpControllerRef().get(this.equipmentId).tell(msg, this.getSelf());
    }
    /**
     * 通知tcpcontroller开始自毁
     */
    private void sendStopToTcpcontroller(){

        this.receiveStopTimes++;
        log.info("{}收到来自服务端的主动发出的销毁需求, 第{}次请求", this.equipmentId, this.receiveStopTimes);
        this.serverRef.tell(NoteTcpcontrollerStop.builder().equipmentId(this.equipmentId).build(), this.getSelf());
        if(this.receiveStopTimes > 1){
            log.info("{}收到来自服务端的主动发出的销毁需求超过或等于2次，所以自毁吧", this.equipmentId);
            this.getContext().stop(getSelf());
        }
    }

    /**
     * 获取内部taskid
     * @return
     */
    private Long newTaskId(){
        this.baseTaskId ++;
        if(this.baseTaskId == 9999L){
            this.baseTaskId = 10L;
        }
        return this.baseTaskId;
    }

    /**
     * 客户端可能同时上报两个心跳，过滤为一个
     * @param msg
     * @return
     */
    private String getOneHeart(String msg){

        String[] msgSpilt = msg.split("\r\n");

        return msgSpilt[0] + "\r\n";

    }


    /**
     * 清掉第一个下载任务
     */
    private void removeFristVoiceDownloadTask(){
        if(this.downloadVoiceTask.size() > 0){
            VoiceTask task = this.downloadVoiceTask.get(0);
            this.serverAssistantRef.tell(NoteServerVoiceTaskStatus.builder().voiceTask(task).failedReason("音频下载被异常打断，失败任务，请心跳正常后重新下载").status(-1).build(), this.getSelf());
            log.info("门店{}主动清空下载任务 {} {}",this.equipmentId, task.getTaskId());
            this.downloadVoiceTask.remove(0);
        }

    }
}
