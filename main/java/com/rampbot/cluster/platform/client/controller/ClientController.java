package com.rampbot.cluster.platform.client.controller;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import com.alibaba.fastjson.JSON;
import com.mysql.jdbc.StringUtils;
import com.rampbot.cluster.platform.client.utils.BuildResponse;
import com.rampbot.cluster.platform.client.utils.DBHelper;
import com.rampbot.cluster.platform.client.utils.SQLHelper;
import com.rampbot.cluster.platform.client.utils.Utils;
import com.rampbot.cluster.platform.domain.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;


@Slf4j
public class ClientController extends UntypedActor {

    private Long baseTaskId = 10L;
    private final ActorRef tcpControllerRef;
    private ActorRef serverAssistantRef;
    private final String equipmentId; // 门店序列号
    private int storeId; // 门店编号
    private String key; // 密钥
    private String companyId;

    List<Task> setWrzsStatusTask = new LinkedList<>(); // 服务端生成的播放语音的任务
    List<Task> doorTask = new LinkedList<>(); // 服务端生成的门状态改变的iot任务
    List<VoiceTask> downloadVoiceTask = new LinkedList<>(); // 服务端生成的下载语音的任务
    List<Task> playVoiceTask = new LinkedList<>(); // 服务端生成的播放语音的任务
    List<Task> otherTask = new LinkedList<>(); // 1 703因为客户端不需要回复暂不加入 2 目前加入704下载音频结束

    public ClientController(ActorRef tcpControllerRef, String equipmentId){
        this.equipmentId = equipmentId;
        this.tcpControllerRef = tcpControllerRef;
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
        }
    }

    public void postStop(){
        log.info("{} 停止任务", this.getSelf());
    }

    public void preStart() {
        // TODO: 2022/10/14 获取门店号、key、公司号
        // company_id=10050, private_key=d8987a72374346809ee0925cba75629c, id=10011
//        this.storeId = DBHelper.getStoreIdBySerialNumber(this.equipmentId);

        Map<String, Object> storeMsgMap = DBHelper.getStoreIdAndCompanyIdBySerialNumber(this.equipmentId);
        this.storeId = Utils.convertToInt(storeMsgMap.get("id"), -1);
        this.key = storeMsgMap.get("private_key").toString();
        this.companyId = storeMsgMap.get("company_id").toString();

        log.info("门店编号 {}  密钥 {}  公司编号 {}", storeId, key, companyId);
        // 生成actor阶段校验，校验门店号
        if (this.storeId < 1) {
            String msg = BuildResponse.buildResponseMsgError(-101, "设备序列号不存在", "");
            this.sendMsg(msg);
        }else {
            // 回复一个空心跳
            this.sendMsg(BuildResponse.buildResponseMsg(0,"",""));
        }
        log.info("盒子{}所以门店的编号为{} 密钥为{}", this.equipmentId, this.storeId, this.key);

        this.serverAssistantRef =  this.getContext().actorOf(Props.create(ServerAssistant.class, this.getSelf(), this.storeId, this.companyId), "ServerAssistant." +  this.storeId + "." + System.currentTimeMillis());

    }


    /**
     * 处理服务端任务
     * @param msg
     */
    private void processServerTask(NoteControllerTask msg ){
        log.info("收到服务端新发的任务{}", msg);
        if(msg.getDoorTask() != null && msg.getDoorTask().size() > 0){
            log.info("添加开关门任务 {}", msg.getDoorTask());
            this.doorTask.addAll(msg.getDoorTask());
        }

        if(msg.getSetWrzsStatusTask() != null && msg.getSetWrzsStatusTask().size() > 0){
            log.info("添加开关无人值守任务 {}", msg.getSetWrzsStatusTask());
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
//            log.info("查看 新增加的播放任务{}", playVoiceTask);
            this.playVoiceTask.addAll(playVoiceTask);
        }

        if(msg.getDownloadVoiceTask() != null && msg.getDownloadVoiceTask().size() > 0){
            // 为每一个音频下载任务赋值taskId
            List<VoiceTask> downloadTask = msg.getDownloadVoiceTask();
            for(int i = 0; i < downloadTask.size(); i++){
                downloadTask.get(i).setTaskId(this.newTaskId());
            }
            this.downloadVoiceTask.addAll(downloadTask);
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
            msgMap = JSON.parseObject(clientMsg, Map.class);
        } catch (Exception e) {
        }
        if (msgMap == null || msgMap.size() < 1) {

            String msg = BuildResponse.buildResponseMsgError(-2, "内容格式错误", "");
            this.sendMsg(msg);
            return  null;
        }
        return msgMap;
    }



    /**
     * 处理盒子上报的action
     * @param msgMap
     */
    private void processClientAction(Map<String, Object> msgMap){
        Integer action = Utils.convertToInt(msgMap.get("action"), -1);

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
                        .build();
                this.serverAssistantRef.tell(noteServerClientStatus, this.getSelf());


                // 处理客户端
                // 未开启初始化
                if(is_init != 1){
                    // 配置参数
                }


                // 是否值守状态不一致
                if(this.setWrzsStatusTask.size() > 0){


                    Task task = this.setWrzsStatusTask.get(0);
                    log.info("有开关无人值守任务要处理 {}", task);
                    task.setTaskStatus(TaskStatus.sent);
                    this.sendMsg(BuildResponse.buildResponseMsgTask( 0, "", setWrzsStatusTask.get(0)));
                    return;
                }

                // 是否有服务端生成的门状态改变iot任务
                if(doorTask.size() > 0){
                    Task task = doorTask.get(0);
                    log.info("有开关门任务要处理 {}", task);
                    task.setTaskStatus(TaskStatus.sent);
                    this.sendMsg(BuildResponse.buildResponseMsgTask( 0, "", doorTask.get(0)));
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
                        map.put("event", 703);
                        map.put("update_voice_name", task.getDownloadPlace());
                        map.put("update_voice_length", task.getVoiceLength());
                        map.put("update_voice_version", task.getVersion());

                        List<Map> iotTaskList = new ArrayList<>();
                        iotTaskList.add(map);
                        String msg = BuildResponse.buildResponseMsg(0, "", iotTaskList);
                        this.sendMsg(msg);
                        return;
                    }else if(task.getTaskStatus().equals(TaskStatus.sent) && this.otherTask.size() > 0 && this.otherTask.stream().anyMatch(t -> t.getTask().get("downloadTaskId") != null && Utils.convertToLong(t.getTask().get("downloadTaskId"), -1).equals(task.getTaskId()))){
                        Task unSentTask = this.otherTask.stream().filter(t -> Utils.convertToLong(t.getTask().get("downloadTaskId"), -1).equals(task.getTaskId())).findFirst().get();
                        this.completeLocalTask(unSentTask.getTaskId());
                    }

                }


                // 是否有播放任务
                if(this.playVoiceTask.size() > 0){
                    Task playVoiceTask = this.playVoiceTask.get(0);
                    if(playVoiceTask.getTaskStatus().equals(TaskStatus.pending)){
                        playVoiceTask.setTaskStatus(TaskStatus.sent);
                        Map<String, Object> map = playVoiceTask.getTask();
                        List<Map> iotTaskList = new ArrayList<>();
                        iotTaskList.add(map);
                        String msg = BuildResponse.buildResponseMsg(0, "", iotTaskList);
                        this.sendMsg(msg);
                        return;
                    }
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
                log.info("要完成的任务{}", findTask);
                findTask.setTaskStatus(TaskStatus.completed);
                this.completeLocalTask(taskId);

                this.sendMsg(BuildResponse.buildResponseMsg(0, "", ""));
                break;

            case 778:

                this.serverAssistantRef.tell(NoteServerUpdateHeartBeatTime.builder().build(), this.getSelf());

                // 有正在下载中的任务
                if(this.downloadVoiceTask.size() > 0 && this.downloadVoiceTask.get(0).getTaskStatus().equals(TaskStatus.sent)){
                    VoiceTask task = this.downloadVoiceTask.get(0);
                    byte[] downloadVoice = task.getVoice();

                    if(downloadVoice != null){
                        // 发送音频数据
                        this.sendMsg(DownLoadVoiceData.builder().voiceData(downloadVoice).build());
                        // 已经是最后一块音频下载任务，准备结束任务
                        if(task.getRemainLength() == 0){
                            log.info("剩余音频长度 {} ", task.getRemainLength());
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
                return;
            case 800:  // action 800 门店断电了
                return;
        }
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
        }else if(this.playVoiceTask.size() > 0 && this.playVoiceTask.get(0).getTaskId().equals(taskId)){
//            log.info("查看 移除播放任务{}", this.playVoiceTask.get(0));
            this.playVoiceTask.remove(0);
        }else if(this.setWrzsStatusTask.size() > 0 && this.setWrzsStatusTask.get(0).getTaskId().equals(taskId)){
            this.serverAssistantRef.tell(NoteServerTaskStatus.builder().task(this.setWrzsStatusTask.get(0)).build(), this.getSelf());

            this.setWrzsStatusTask.remove(0);
        }else if(this.otherTask.size() > 0 && this.otherTask.stream().anyMatch(t -> t.getTaskId().equals(taskId))){
            Task completeTask = this.otherTask.stream().filter(t -> t.getTaskId().equals(taskId)).findFirst().get();
            if(completeTask.getTask().get("event").toString().equals("704")){
                log.info("下载任务{}需要完成", completeTask);
                if(this.downloadVoiceTask.get(0).getTaskId().equals(Utils.convertToLong(completeTask.getTask().get("downloadTaskId"), -1))){
                    this.serverAssistantRef.tell(NoteServerVoiceTaskStatus.builder().voiceTask(this.downloadVoiceTask.get(0)).build(), this.getSelf());
                    this.downloadVoiceTask.remove(0);
                }
            }
            this.otherTask.remove(completeTask);
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
        if (!BuildResponse.buildSign(time, storeId, data, key).equalsIgnoreCase(sign)) {

            String msg = BuildResponse.buildResponseMsgError(-9, "签名错误", "");
            log.info("{}签名错误,查看正确的签名{}", this.storeId, BuildResponse.buildSign(time, this.storeId, data, this.key));
            this.sendMsg(msg);
            return false;
        }

        return true;
    }


    /**
     * 给tcpController发送消息
     * @param msg
     */
    private void sendMsg(String msg){
        this.tcpControllerRef.tell(msg, this.getSelf());
    }

    private void sendMsg(DownLoadVoiceData msg){
        this.tcpControllerRef.tell(msg, this.getSelf());
    }

    /**
     * 通知tcpcontroller开始自毁
     */
    private void sendStopToTcpcontroller(){
        this.tcpControllerRef.tell(NoteTcpcontrollerStop.builder().build(), this.getSelf());
    }

    private Long newTaskId(){
        this.baseTaskId ++;
        if(this.baseTaskId == 9999L){
            this.baseTaskId = 10L;
        }
        return this.baseTaskId;
    }

}
