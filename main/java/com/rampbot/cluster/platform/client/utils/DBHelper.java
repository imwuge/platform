package com.rampbot.cluster.platform.client.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.List;
import java.util.Map;


@Slf4j
public class DBHelper {
    /**
     * 获取配置内容
     *
     * @param configId
     * @return
     */
    public static String getConfigValue(Integer configId, int companyId) {
        String sql = "SELECT `value` FROM configs WHERE `key` = \"SYS_CLIENT_NOTIFY_AUTO_OPEN_DOOR\"" + " AND company_id = " + companyId;
        List<Map<String, Object>> configs = SQLHelper.executeQueryTable(sql);
        if (configs == null || configs.size() < 1) {
            return null;
        }

        Map<String, Object> conf = configs.get(0);
        return conf.get("value").toString();
    }


    /**
     * 通过门店Id获取门店状态
     *
     * @param storeId
     * @return (门店状态 ( 1 正常营业 、 2 停业 、 3 远程值守中))
     */
    public static Integer getStoreStatus(Integer storeId) {
        String sql = "SELECT status FROM stores WHERE store_id = " + storeId;
        List<Map<String, Object>> storeList = SQLHelper.executeQueryTable(sql);
        if (storeList == null || storeList.size() < 1) {
            return -1001;
        }

        Map<String, Object> stores = storeList.get(0);
        return Utils.convertToInt(stores.get("status"), -1);
    }

    /**
     * 通过门店Id获取门店状态
     *
     * @param storeId
     * @return (门店状态 ( 1 正常营业 、 2 停业 、 3 远程值守中))
     */
    public static Map<String, Object> getStoreMap(Integer storeId, int companyId) {
        String sql = "SELECT `status`, `mode`, `name`, `order_triggered_mode`, `power`, `door_status`, `serial_number`, `private_key`, `open_seconds`, `is_service_trust`  FROM stores WHERE store_id = " + storeId + " AND company_id = " + companyId;
        List<Map<String, Object>> storeList = SQLHelper.executeQueryTable(sql);
        if (storeList == null || storeList.size() < 1) {
            return null;
        }

        return storeList.get(0);
    }

    /**
     * 更新门店表
     *
     * @param storeId
     * @param colums
     * @param value
     * @return
     */
    public static boolean setStoreMap(Integer storeId, String colums, Object value) {
        String sql = "UPDATE stores SET `" + colums + "` = '" + value + "' WHERE store_id = " + storeId;
        if (value instanceof Integer) {
            sql = "UPDATE stores SET `" + colums + "` = " + value + " WHERE store_id = " + storeId;
        }
        return SQLHelper.executeUpdate(sql) >= 0;
    }

    /**
     * 新增通知消息
     *
     * @param storeId
     * @param title
     * @param content
     * @param level
     * @param isAppointHelperId （是否需要获取正在服务的客服人员）
     * @return
     */
    public static boolean addNotify(int storeId, String title, String content, int level, boolean isAppointHelperId) {
        Integer helperId = -1;
        Integer stationId = -1;
        String sql = "";

        if (isAppointHelperId) {
            sql = "SELECT helper_id, station_id FROM mus_orders WHERE store_id = " + storeId + " AND `status` = 1 LIMIT 1";
            List<Map<String, Object>> ordersList = SQLHelper.executeQueryTable(sql);
            if (ordersList != null && ordersList.size() > 0) {
                helperId = Utils.convertToInt(ordersList.get(0).get("helper_id"), -1);
                stationId = Utils.convertToInt(ordersList.get(0).get("station_id"), -1);
            }
        }

        sql = "INSERT INTO `notify` (`title`, `content`, `level`, `store_id`, `helper_id`, `station_id`, `create_time`) ";
        sql += "VALUES ('" + title + "', '" + content + "', " + level + ", " + storeId + ", " + helperId + ", " + stationId + ", " + System.currentTimeMillis() + ")";

        return SQLHelper.executeUpdate(sql) >= 0;
    }
    public static boolean addNotifyV2(int storeId, int companyId, String type, String name) {
        Integer helperId = -1;
//        Integer stationId = -1;

        // 加入数据库记录校验，如果数据库记录的时间在实时更新 说明已有新的服务actor更进，不需要产生服务订单
        if(type.equals("失联")){
            String sql1 = "SELECT `last_heartbeat_time`  FROM stores WHERE store_id = " + storeId + " AND company_id = " + companyId;
            List<Map<String, Object>> lastTimeList = SQLHelper.executeQueryTable(sql1);
            Long recodeLastTime = Utils.convertToLong(lastTimeList.get(0).get("last_heartbeat_time"), 0);
            if(System.currentTimeMillis() - recodeLastTime <= 50 * 1000){
                log.info("门店{}不用生成此次失联订单", storeId);
                return false;
            }
        }

        int level = Utils.getLevel(type);
        String sql = "";
        String title = "【" + type + "】新消息通知";
        String content = name +  "【" + type + "】新消息通知";
//        Long orderNo = Utils.buildMusOrderNo(type);
        // 更新redis  int companyId, int storeId, Long orderNo, String type, String title, String content,  int helperId
        sql = "INSERT INTO `notify` (`title`, `content`, `level`, `company_id`, `store_id`,  `helper_id`,  `create_time`, `type`) ";
        sql += "VALUES ('" + title + "', '" + content + "', " + level + ", " + companyId + ", " + storeId + ", " + helperId + ", "  + System.currentTimeMillis() + "," + Utils.getTypeNum(type) + ")";
        Long id = (long) SQLHelper.executeReturnInsertLastId(sql);

        try {
            RedisHelper.writeOrderOrNotify(companyId, storeId, id, type, title, content,  helperId, level);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }


    /**
     * 失联订单使用
     * @param storeId
     * @param companyId
     * @param type
     * @param name
     * @return
     */
    public static boolean addNotifyV3(int storeId, int companyId, String type, String name, String title, String content) {
        Integer helperId = -1;
//        Integer stationId = -1;

        // 加入数据库记录校验，如果数据库记录的时间在实时更新 说明已有新的服务actor更进，不需要产生服务订单
        if(type.equals("失联")){
            String sql1 = "SELECT `last_heartbeat_time`  FROM stores WHERE store_id = " + storeId + " AND company_id = " + companyId;
            List<Map<String, Object>> lastTimeList = SQLHelper.executeQueryTable(sql1);
            Long recodeLastTime = Utils.convertToLong(lastTimeList.get(0).get("last_heartbeat_time"), 0);
            if(System.currentTimeMillis() - recodeLastTime <= 50 * 1000){
                log.info("门店{}不用生成此次失联订单", storeId);
                return false;
            }
        }

        int level = Utils.getLevel(type);
        String sql = "";
//        String title = "【" + type + "】新消息通知";
//        String content = name +  "【" + type + "】新消息通知";
//        Long orderNo = Utils.buildMusOrderNo(type);
        // 更新redis  int companyId, int storeId, Long orderNo, String type, String title, String content,  int helperId
        sql = "INSERT INTO `notify` (`title`, `content`, `level`, `company_id`, `store_id`,  `helper_id`,  `create_time`, `type`) ";
        sql += "VALUES ('" + title + "', '" + content + "', " + level + ", " + companyId + ", " + storeId + ", " + helperId + ", "  + System.currentTimeMillis() + "," + Utils.getTypeNum(type) + ")";
        Long id = (long) SQLHelper.executeReturnInsertLastId(sql);

        try {
            RedisHelper.writeOrderOrNotify(companyId, storeId, id, type, title, content,  helperId, level);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }


    /**
     * 添加集体失联通知
     * @param storeId
     * @param companyId
     * @param type
     * @param name
     * @return
     */
    public static boolean addNotifyDisconnectStores(int storeId, int companyId, String name) {
        Integer helperId = -1;
//        Integer stationId = -1;

        String type = "失联";
        int level = Utils.getLevel(type);


        String sql = "";
        String title = "【门店巡检，当日目前失联门店汇总】新消息通知";
        String content = name +  "【" + type + "】新消息通知";
//        Long orderNo = Utils.buildMusOrderNo(type);
        // 更新redis  int companyId, int storeId, Long orderNo, String type, String title, String content,  int helperId
        sql = "INSERT INTO `notify` (`title`, `content`, `level`, `store_id`, `store_id`,  `helper_id`,  `create_time`, `type`) ";
        sql += "VALUES ('" + title + "', '" + content + "', " + level + ", " + storeId + ", " + helperId + ", "  + System.currentTimeMillis() + "," + Utils.getTypeNum(type) + ")";
        Long id = (long) SQLHelper.executeReturnInsertLastId(sql);

        try {
            RedisHelper.writeOrderOrNotify(companyId, storeId, id, type, title, content,  helperId, level);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
    /**
     * 添加订单  0 购物，1 补签，2 安防，3 店内求助，4 故障，5 巡店
     * @param storeId
     * @param type
     * @param
     * @return
     */
    public static boolean addMusOrders(int storeId, int companyId, String type, boolean isCanDuplicate , String name) {
        /*
         *先判断是否可以产生新订单
         */
        if(!isCanDuplicate){
            if(RedisHelper.isExistsOrder(companyId, storeId, type)){
                log.info("门店{}已存在{}订单，不生产新订单", storeId, type);
                return false;
            }
        }


        if(type.equals("安防")){
            if(RedisHelper.isExistsOrder(companyId, storeId, "安防") || RedisHelper.isExistsOrder(companyId, storeId, "购物") ||
                    RedisHelper.isExistsOrder(companyId, storeId, "补签")){
                log.info("门店{}存在安防/购物/补签订单, 不生产安防订单", storeId);
                return false;
            }
        }

        Integer helperId = RedisHelper.getHelperId(companyId, storeId);
        log.info("门店{}新生成的{}订单选取的客服是{}", storeId, type, helperId);
        Long orderNo = Utils.buildMusOrderNo(companyId, storeId, type);
        Integer stationId = -1;
        long recodeLastTime = -1;
        Long clientId = -1l;

        String title = "【" + type + "】新订单通知" ;
        String content =name +  "新【" + type + "】订单通知";
        if(type.equals("购物")){
            content = String.format(name + " 【宽进严出】");
        }
        String sql = "";
        // 更新redis  int companyId, int storeId, Long orderNo, String type, String title, String content,  int helperId
        RedisHelper.writeOrderOrNotify(companyId, storeId, orderNo, type, title, content,  helperId, 0);
        // 更新mysql
        sql = "INSERT INTO mus_orders (order_no, order_type, client_id, helper_id, store_id, station_id, `status`, create_time, company_id, type)\n" +
                "VALUES (" + orderNo + ", '" + type + "', " + clientId + ", " + helperId + ", " + storeId + ", " + stationId + ", 0, " +
                System.currentTimeMillis() + ", " + companyId + " , " + Utils.getTypeNum(type) + ")";
        return SQLHelper.executeUpdate(sql) >= 0;
    }

//    public static back(){
//        // 加入数据库记录校验，如果数据库记录的时间在实时更新 说明已有新的服务actor更进，不需要产生服务订单
////        if(type.equals("失联")){
////            String sql1 = "SELECT `last_heartbeat_time`  FROM stores WHERE store_id = " + storeId + " AND company_id = " + companyId;
////            List<Map<String, Object>> lastTimeList = SQLHelper.executeQueryTable(sql1);
////            recodeLastTime = Utils.convertToLong(lastTimeList.get(0).get("last_heartbeat_time"), 0);
////            if(System.currentTimeMillis() - recodeLastTime <= 50 * 1000){
////                return false;
////            }
////        }
//
//
//
////        if(type.equals("安防")){
////            sql = "SELECT id FROM mus_orders WHERE store_id = " + storeId + " AND company_id = " + companyId + " AND order_type in (\"安防\" , \"购物\" , \"补签\" ) AND `status` in (0, 1, 5) ORDER BY id DESC LIMIT 1";
////        }else {
////            sql = "SELECT id FROM mus_orders WHERE store_id = " + storeId + " AND company_id = " + companyId + " AND order_type = '" + type + "' AND `status` in (0, 1, 5) ORDER BY id DESC LIMIT 1";
////        }
//
//        List<Map<String, Object>> ordersList = SQLHelper.executeQueryTable(sql);
//
//        // 如果不允许存在重复订单，则校验是否产生新订单
//        if(!isCanDuplicate){
//            if (ordersList != null && ordersList.size() > 0) { // 存在正在执行或者待分配的故障订单，不进行生成
//                return false;
//            }
//        }
//
//
//        if (isAppointHelperId) {
////            sql = "SELECT helper_id, station_id FROM mus_orders WHERE store_id = " + storeId + " AND `status` in (0, 1) AND order_type != \"巡店\" " +
////                    " AND company_id = " + companyId  + " LIMIT 1";
////            ordersList = SQLHelper.executeQueryTable(sql);
////            if (ordersList != null && ordersList.size() > 0) {
////                helperId = Utils.convertToInt(ordersList.get(0).get("helper_id"), -1);
////                stationId = Utils.convertToInt(ordersList.get(0).get("station_id"), -1);
////            }
//        }
//
//        Long orderNo =
//
//
//                sql = "INSERT INTO mus_orders (order_no, order_type, client_id, helper_id, store_id, station_id, `status`, create_time, company_id)\n" +
//                        "VALUES (" + orderNo + ", '" + type + "', " + clientId + ", " + helperId + ", " + storeId + ", " + stationId + ", 0, " +
//                        System.currentTimeMillis() + ", " + companyId + ")";

//        return SQLHelper.executeUpdate(sql) >= 0;
//    }

    public static boolean isHasPendingSaftyOrder(int storeId, int companyId) {
        String sql =  "SELECT id FROM mus_orders WHERE store_id = " + storeId + " AND company_id = " + companyId + " AND order_type = \"安防\"  AND `status` = 0 ORDER BY id DESC LIMIT 1";
        List<Map<String, Object>> ordersList = SQLHelper.executeQueryTable(sql);
        return ordersList != null && ordersList.size() > 0;
    }



    /**
     * 判断是否需要自动开门
     * @param storeId
     * @param
     * @param intervalMilliseconds
     */
    public static void checkAutoOpenDoor(int companyId, Integer storeId,  long intervalMilliseconds) {

        if (intervalMilliseconds < 1000) {
            // 配置错误
            return;
        }

        if(RedisHelper.checkAutoOpenDoor(companyId, storeId, intervalMilliseconds)){
            long diffTime = System.currentTimeMillis() - intervalMilliseconds;

            String sql = "SELECT id FROM iot_tasks WHERE `status` = 9 AND start_time < " + diffTime + " AND store_id = " + storeId + " AND company_id = " + companyId;
            List<Map<String, Object>> taskList = SQLHelper.executeQueryTable(sql);
            if (taskList != null || taskList.size() > 0) {
                for (int i = 0; i < taskList.size(); i++) {
                    long taskId = Utils.convertToLong(taskList.get(i).get("id").toString(), -1);
                    // 判断门店是否存在正在执行中的订单
                    DBHelper.updateIotTaskById(taskId, 0, companyId, storeId);
                }
            }
        }
    }

    /**
     * 作废自动开门任务
     * @param companyId
     * @param storeId
     */
    public static void cancelAutoOpenDoor(int companyId, Integer storeId) {

        if(RedisHelper.cancelAutoOpenDoor(companyId, storeId)){

            Long time = new Date().getTime();
            String sql = "UPDATE iot_tasks SET `status` = -1, end_time = " + time + " WHERE `status` = 9 AND company_id = " + companyId + " AND store_id = " + storeId;
            SQLHelper.executeUpdate(sql);

        }
    }




    /**
     * 执行动作 (901 全开门 902 全关门，903 进店开门 904 进店关门，905 离店开门 906 离店关门)
     * 更新服务端生成的任务，去掉重复任务
     * @param storeId
     * @param actionType
     * @param status 要增加的任务状态
     * @return
     */
    public static void addIotTasksWithoutProcessingOrPending(int storeId, int actionType, int status, int companyId) {
        String sql = "";
//        sql = "SELECT id FROM iot_tasks WHERE store_id = " + storeId +  " AND company_id = " + companyId + " AND `status` in (0, 1) " + " AND `action_type` = " + actionType + " ORDER BY id ASC LIMIT 1";

//        List<Map<String, Object>> pendingOrProcessingTask = SQLHelper.executeQueryTable(sql);

        List<Map<String, Object>> pendingOrProcessingTask = RedisHelper.getTask(companyId, storeId, actionType, null);;
        if ( pendingOrProcessingTask.size() > 0) { // 相同的action_type类型任务已经存在
            log.info("门店{}存在相同的执行中或者待处理的任务{} {}, 不在生成同类新的新任务", storeId, actionType, pendingOrProcessingTask);
            return ;
        }

        sql = "INSERT INTO iot_tasks (store_id, action_type, `status`, start_time, company_id)\n" +
                "VALUES (" + storeId + ", " + actionType + ", " + status + ", " + System.currentTimeMillis() + ", " + companyId + ")";
        //SQLHelper.executeUpdate(sql) ;
        Long id = (long) SQLHelper.executeReturnInsertLastId(sql);
        if(status == 0 || status == 1){
            RedisHelper.addIotTasks(companyId, storeId, actionType, id, status);
        }

    }


    /**
     * 执行动作 (901 全开门 902 全关门，903 进店开门 904 进店关门，905 离店开门 906 离店关门)
     * 宽进严出情况下 增加自动完成的进店开门
     * @param storeId
     * @param
     * @param status
     * @param companyId
     * @return
     */
    public static void addIotTasksComplete(int storeId, int actionType, int status, int companyId) {
        String sql = "";
        sql = "INSERT INTO iot_tasks (store_id, action_type, `status`, start_time, company_id)\n" +
                "VALUES (" + storeId + ", " + actionType + ", " + status + ", " + System.currentTimeMillis() + ", " + companyId + ")";
        Long id = (long) SQLHelper.executeReturnInsertLastId(sql);
        if(status == 0 || status == 1){
            RedisHelper.addIotTasks(companyId, storeId, actionType, id, status);
        }
    }



    public static boolean isHasProcessingOrPendingAction(int storeId, int actionType, int companyId) {

//        String sql = "SELECT id FROM iot_tasks WHERE store_id = " + storeId + " AND company_id = " + companyId + " AND `status` in (0, 1) " + " AND `action_type` = " + actionType ;
//
//        List<Map<String, Object>> pendingOrProcessingTask = SQLHelper.executeQueryTable(sql);

        return   RedisHelper.getTask(companyId, storeId, actionType, null).size() > 0; // 相同的action_type类型任务已经存在


    }

    /**
     * 获取最后一条 iot task 任务
     *
     * @param storeId
     * @return
     */
    public static List<Map<String, Object>> getPendingIotTask(Integer storeId, int companyId, Boolean isGetProcessingTask) {
        String sql = null;
        List<Map<String, Object>> list = null;

        if(isGetProcessingTask){
            //sql = "SELECT id, action_type, status, start_time, end_time FROM iot_tasks WHERE store_id = " + storeId + " AND `status` in (0, 1) " + " AND company_id = " + companyId;

            list = RedisHelper.getTask(companyId, storeId, null, null);
        }else{
//            sql = "SELECT id, action_type, status, start_time, end_time FROM iot_tasks WHERE store_id = " + storeId + " AND `status` = " + 0 + " AND company_id = " + companyId;
            list = RedisHelper.getTask(companyId, storeId, null, 0);
        }
//        List<Map<String, Object>> list = SQLHelper.executeQueryTable(sql);
        if (list == null || list.size() < 1) {
            return null;
        }

        return list;
    }

    /**
     * 该actor在销毁之前需要重置没有完成iot任务的状态
     * @param storeId
     * @return
     */
    public static void updateIotask2Complete(Integer storeId, int companyId) {
//        log.info("销毁actor前 更新公司{} 门店{} 处理中但未完成的iotask任务状态为 2", companyId, storeId);
        Long time = new Date().getTime();
        String sql = "UPDATE iot_tasks SET `status` = 2, end_time = " + time + " WHERE store_id = " + storeId + " AND company_id = " + companyId + " AND `status` in (0,1, 9)";
        SQLHelper.executeUpdate(sql);
        RedisHelper.updateIotask2Complete(companyId, storeId, null, 2);
    }


    /**
     * 更新iot任务状态
     * @param taskId
     * @param status
     * @return
     */
    public static void updateIotTaskById(long taskId, Integer status, int companyId, Integer storeId) {
        Long time = new Date().getTime();
        String sql = "UPDATE iot_tasks SET `status` = " + status + ", end_time = " + time + " WHERE id = " + taskId + " AND company_id = " + companyId;
        SQLHelper.executeUpdate(sql);
        RedisHelper.updateIotask2Complete(companyId, storeId, taskId, status);
    }

    public static void updateIotTaskById(long taskId, int companyId, Integer storeId) {
        Long time = new Date().getTime();
        String sql = "UPDATE iot_tasks SET `status` = 2, end_time = " + time + " WHERE id = " + taskId + " AND company_id = " + companyId;
        SQLHelper.executeUpdate(sql);
    }


    /**
     * 更新盒子最新状态
     * @param storeId
     * @param firmwareVersion
     * @param is_door_real_close_one
     * @param is_door_real_close_two
     * @return
     */
    public static boolean updateStoreLastHeartBeatTime(Integer storeId, int companyId, Integer firmwareVersion, Integer is_door_real_close_one, Integer is_door_real_close_two) {
        Long time = new Date().getTime();
        String sql = "UPDATE stores SET last_heartbeat_time = " + time + ", door_signal_one = " + is_door_real_close_one + ", door_signal_two = " + is_door_real_close_two + ", firmware_version = " + firmwareVersion + " WHERE store_id = " + storeId + " AND company_id = " + companyId;
        return SQLHelper.executeUpdate(sql) >= 0;
    }

    /**
     * 只更新心跳时间
     * @param storeId
     * @param companyId
     * @return
     */
    public static boolean updateStoreLastHeartBeatTime(Integer storeId, int companyId){
        Long time = new Date().getTime();
        String sql = "UPDATE stores SET last_heartbeat_time = " + time + " WHERE store_id = " + storeId + " AND company_id = " + companyId;
        return SQLHelper.executeUpdate(sql) >= 0;
    }



    /**
     * 添加数据库log
     * @param storeId
     * @param actionType
     * @param comment
     * @return
     */
    public static boolean addIotTasks(int storeId, int actionType, String comment) {
        String sql = "INSERT INTO sys_logs (store_id, system, action_type, `comment`, create_time)\n" +
                "VALUES (" + storeId + ", 'Server', " + actionType + ", '" + comment + "', now())";
        return SQLHelper.executeUpdate(sql) >= 0;
    }












    /**
     *获取需要实时触发的音频
     */
    public static List<Map<String, Object>> getPendingVoiceIotTask(Integer storeId, int companyId) {
        String sql =  "SELECT id, voice_id, times, player, status, update_time, enable_time FROM iot_tasks_voice WHERE store_id = " + storeId + " AND `status` = 0 " + " AND company_id = " + companyId;

        List<Map<String, Object>> list = SQLHelper.executeQueryTable(sql);
        if (list == null || list.size() < 1) {
            return null;
        }

        return list;
    }

    /**
     * 完成实时触发的音频状态
     */
    public static boolean updateIotaskVoice2Complete(Integer storeId, int companyId, Integer voiceId, Integer player, Integer stats) {
        Long time = new Date().getTime();
        String sql = "UPDATE iot_tasks_voice SET `status` = " + stats + ", stm_update_time = " + time + " WHERE store_id = " + storeId + " AND company_id = " + companyId + " AND voice_id = " + voiceId + " AND player = " + player;
        return SQLHelper.executeUpdate(sql) >= 0;
    }
    public static boolean updateIotaskVoice2Complete(long id, Integer stats) {
        Long time = new Date().getTime();
        String sql = "UPDATE iot_tasks_voice SET `status` = " + stats + ", stm_update_time = " + time + " WHERE id = " + id ;
        return SQLHelper.executeUpdate(sql) >= 0;
    }


    /**
     * 获取还没触发的下载语音的任务
     *
     * @param storeId
     * @return
     */
    public static List<Map<String, Object>> getPendingVoiceTask(Integer storeId, int companyId) {
        String sql = "SELECT id, store_id, voice_id, sd_index, sd_version FROM stores_voice_stm WHERE store_id = " + storeId + " AND `status` = " + 0 + " AND company_id = " + companyId;
        List<Map<String, Object>> list = SQLHelper.executeQueryTable(sql);
        if (list == null || list.size() < 1) {
            return null;
        }

        return list;
    }


    /**
     * 更新音频下载任务状态
     * @param storeId
     * @param downloadPlace
     * @param status
     * @return
     */
    public static boolean updateVoiceTask(Integer storeId, int companyId, Integer downloadPlace, Integer status) {
        Long time = new Date().getTime();
        String sql = "UPDATE stores_voice_stm SET `status` = " + status + ", stm_update_time = " +  time + " WHERE store_id = " + storeId + " AND sd_index = " + downloadPlace + " AND company_id = " + companyId;
        return SQLHelper.executeUpdate(sql) >= 0;
    }

    /**
     * 该actor在销毁之前需要重置没有完成下载任务的状态
     * @param storeId
     * @return
     */
    public static boolean updateVoiceTask2Pending(Integer storeId, int companyId) {
//        log.info("销毁actor前 更新公司{} 门店{} 处理中但未完成的音频下载任务状态为 0", companyId, storeId);
        Long time = new Date().getTime();
        String sql = "UPDATE stores_voice_stm SET `status` = " + 0 + ", stm_update_time = " +  time + " WHERE store_id = " + storeId + " AND `status` = " + 1 + " AND company_id = " + companyId;
        return SQLHelper.executeUpdate(sql) >= 0;
    }


    /**
     * 获取播放音频配置
     * @param storeId
     * @return
     */
    public static List<Map<String, Object>> getPlayVoiceTask(Integer storeId, int companyId) {
        String sql = "SELECT\n" +
                "\tid,\n" +
                "\t`play_count`,\n" +
                "\t`voice_id`,\n" +
                "\t`interval`,\n" +
                "\tplay_ymd,\n" +
                "\tplay_week,\n" +
                "\tplay_time,\n" +
                "\t`event`,\n" +
                "\tbox_index,\n" +
                "\tvolume\n" +
                "FROM\n" +
                "\tstores_voice \n" +
                "WHERE\n" +
                "\tstore_id = " + storeId + " AND company_id = " + companyId + " AND play_time != '-1'";
        List<Map<String, Object>> list = SQLHelper.executeQueryTable(sql);
        if (list == null || list.size() < 1) {
            return null;
        }

        return list;
    }


    /*
    id
name
description
file_url
duration
version
type
status
create_time
update_time
is_deleted
     */

    /**
     *
     * @param voiceId
     * @return
     */
    public static Map<String, Object> getVoice(Integer voiceId, int companyId) {
        String sql = "SELECT id, file_url, version FROM voices WHERE id = " + voiceId + " AND status = " + 0 + " AND company_id = " + companyId;
        List<Map<String, Object>> list = SQLHelper.executeQueryTable(sql);
        if (list == null || list.size() < 1) {
            return null;
        }

        return list.get(0);
    }


    /**
     * 更新stores门店 门状态
     * 门锁状态 (0 全关门 1 全开门， 10 进店关门 11 进店开门， 20 离店关门 21 离店开门)
     * @param storeId
     * @param status
     * @param companyId
     * @return
     */
    public static boolean updateStoreDoorStatus(Integer storeId, Integer status, int companyId) {
        Long time = new Date().getTime();
        String sql = "UPDATE stores SET door_status = " + status + " WHERE store_id = " + storeId + " AND company_id = " + companyId;
        return SQLHelper.executeUpdate(sql) >= 0;
    }

    /**
     * 获取最后一条 通知任务
     *
     * @param storeId
     * @return
     */
    public static Map<String, Object> getLastNotify(Integer storeId, Integer level, int companyId) {
        String sql = "SELECT id, status, create_time FROM notify WHERE store_id = " + storeId + " ORDER BY id DESC LIMIT 1";
        if (level > -1) {
            sql = "SELECT id, status, create_time FROM notify WHERE store_id = " + storeId + " AND company_id = " + companyId + " AND `level` = " + level + " ORDER BY id DESC LIMIT 1";
        }
        List<Map<String, Object>> list = SQLHelper.executeQueryTable(sql);
        if (list == null || list.size() < 1) {
            return null;
        }

        return list.get(0);
    }

    /**
     * 获取当前门店最新版本的语音包
     *
     * @param storeId
     * @return
     */
    public static Integer getMaxVersionVoice(Integer storeId, int companyId) {
        String sql = "SELECT max( voice_version ) AS version FROM v_stores_voice WHERE store_id = " + storeId + " AND company_id = " + companyId;
        List<Map<String, Object>> list = SQLHelper.executeQueryTable(sql);
        if (list == null || list.size() < 1) {
            return -1001;
        }

        return Utils.convertToInt(list.get(0).get("version"), -1);
    }

    /**
     * 获取当前门店最新版本的语音包
     *
     * @param storeId
     * @return
     */
    public static Map<String, Object> getFristVersionVoice(Integer storeId, Integer version, int companyId) {
        String sql = "SELECT id, `event`, voice_version, voice_url FROM v_stores_voice WHERE store_id = " + storeId + " AND company_id = " + companyId + " AND voice_version > "+version+" ORDER BY voice_version ASC limit 1";
        List<Map<String, Object>> list = SQLHelper.executeQueryTable(sql);
        if (list == null || list.size() < 1) {
            return null;
        }
        return list.get(0);
    }


    /**
     * 通过门店设备序列号 获取门店编号
     *
     * @param serialNumber
     * @return
     */
    public static Map<String, Object> getStoreIdAndCompanyIdBySerialNumber(String serialNumber) {
        String sql = "select id , store_id, name, private_key, company_id, is_service_trust from stores where serial_number = '" + serialNumber + "' limit 1";

        List<Map<String, Object>> data = SQLHelper.executeQueryTable(sql);
        if (data == null || data.size() < 1) {
            return null;
        }

//        int storeId = Utils.convertToInt(data.get(0).get("id"), -1);
        return data.get(0);
    }


    /**
     * 更新重启字段
     * @param storeId
     * @param companyId
     */
    public static void updateConfigRestart(Integer storeId, int companyId){
        String sql2 = "UPDATE stores_stm_config SET restart_stm = 0 WHERE store_id = " + storeId + " AND company_id = " + companyId;
        SQLHelper.executeUpdate(sql2);
    }

    /**
     * 获取变化配置
     * @return
     */
    public static Map<String, Object> getUpdateStoreconfigs(Integer storeId, int companyId, boolean isFinishInit) {


        String sql = "select is_reload_config from stores_stm_config  WHERE store_id = " + storeId + " AND company_id = " + companyId;
        List<Map<String, Object>> data = SQLHelper.executeQueryTable(sql);
        if (data == null || data.size() < 1) {
            return null;
        }

        if(!isFinishInit || Utils.convertToInt(data.get(0).get("is_reload_config"), 0) == 1){
            log.info("门店{}需要重新加载配置", storeId);

            String sql2 = "UPDATE stores_stm_config SET is_reload_config = 0 WHERE store_id = " + storeId + " AND company_id = " + companyId;
            SQLHelper.executeUpdate(sql2);

            String sql1 = "select " +
                    "open_seconds_welcome_second," +
                    "interval_milliseconds," +
                    "missing_milliseconds," +
                    "disable_safty_order_mins," +
                    "play_second," +
                    "open_seconds," +
                    "door_may_broken_wait_secons," +
                    "body_sensor_data_collection_time," +
                    "body_detect_filter_time," +
                    "safty_alarm_voice_play_interval_secons," +
                    "work_mode," +
                    "is_has_small_sign_light," +
                    "order_triggered_Mode," +
                    "single_for_close," +
                    "singleton_door," +
                    "has_out_coice_player," +
                    "has_in_coice_player," +
                    "has_power_detection," +
                    "is_nonstandard_store," +
                    "enable_passwordlock," +
                    "net_connecter," +
                    "disconnected_time," +
                    "help_silence_time," +
                    "disconnected_restart_time," +
                    "wait_time," +
                    "in_volume," +
                    "out_volume," +
                    "is_enable_in_light_control," +
                    "is_enable_out_light_control," +
                    "relay_control_stats," +
                    "restart_stm," +
                    "disable_order_type," +
                    "disable_actiong_type," +
                    "in_light_turn_off_time" +
                    " from stores_stm_config  WHERE store_id = " + storeId + " AND company_id = " + companyId;
            List<Map<String, Object>> data1 = SQLHelper.executeQueryTable(sql1);
            if (data1 == null || data1.size() < 1) {
                return null;
            }else{
                return data1.get(0);
            }
        }

        return null;
    }

    /**
     * 设置config表 中控状态字段
     */

    public static void setConfigStatus(int companyId, Integer storeId, String status){

        int storeSatus = Utils.getStatus(status);
        String sql2 = "UPDATE stores_stm_config SET status = " + storeSatus + " WHERE store_id = " + storeId + " AND company_id = " + companyId;
        SQLHelper.executeUpdate(sql2);
    }

    /**
     * 获取配置
     * @return
     */
    public static Map<String, Object> getStoreconfigs(Integer storeId, int companyId) {


        String sql1 = "select " +
                "open_seconds_welcome_second," +
                "interval_milliseconds," +
                "body_sensor_data_collection_time," +
                "missing_milliseconds," +
                "disable_safty_order_mins," +
                "play_second," +
                "door_may_broken_wait_secons," +
                "safty_alarm_voice_play_interval_secons," +
                "work_mode," +
                "order_triggered_Mode," +
                "single_for_close," +
                "singleton_door," +
                "has_out_coice_player," +
                "has_in_coice_player," +
                "has_power_detection," +
                "is_nonstandard_store," +
                "enable_passwordlock," +
                "net_connecter," +
                "disconnected_time," +
                "help_silence_time," +
                "disconnected_restart_time," +
                "wait_time," +
                "in_volume," +
                "out_volume," +
                "is_enable_in_light_control," +
                "is_enable_out_light_control," +
                "relay_control_stats," +
                "restart_stm" +
                " from stores_stm_config  WHERE store_id = " + storeId + " AND company_id = " + companyId;
        List<Map<String, Object>> data1 = SQLHelper.executeQueryTable(sql1);
        if (data1 == null || data1.size() < 1) {
            return null;
        }else{
            return data1.get(0);
        }
    }



    /**
     * 通过门店编号获取通信 Key
     * @param storeId
     * @return
     */
    public static List<Map<String, Object>> getDisconnectStores() {

        long now = System.currentTimeMillis() ;

        long oneMinsAgo = now - 60 *1000;
        System.out.println(oneMinsAgo);

        long dayMillisecond = 60 * 60 * 24 * 1000 ;

        System.out.println((now%(3600*1000)) );



        long dayTime = now - (now + 8 * 3600) % dayMillisecond;
        System.out.println(dayTime);
//        1682726371200
//        1682784000000


        String sql = "select store_id , company_id from stores where last_heartbeat_time <  " + oneMinsAgo + " and last_heartbeat_time > " + dayTime;
        List<Map<String, Object>> data = SQLHelper.executeQueryTable(sql);
        if (data == null || data.size() < 1 ) {
            return null;
        }

        return data;
    }


    /**
     * 插入/更新log表
     */
    public static int insertStoreLogsId(long id, Integer storeId, int companyId, String log, String system) {
        String sql = "INSERT INTO stores_logs(`id`, `store_id`, `company_id`) VALUES(" + id + "," + storeId + "," + companyId + ") ON DUPLICATE KEY UPDATE `logs` = \"" + log + "\", `system` = \"" + system +  "\", `update_time` =current_timestamp()\n";
        return SQLHelper.executeUpdate(sql);

    }

    /**
     * 获取log表id
     * @param storeId
     * @return
     */
    public static Long getStoreLogsId(Integer storeId, int companyId) {
        String sql = "SELECT id FROM stores_logs WHERE `store_id` = " + storeId + " AND `company_id` = " + companyId;
        //System.out.println(sql);
        List<Map<String, Object>> storeList = SQLHelper.executeQueryTable(sql);
        if (storeList == null || storeList.size() < 1) {
            return initStoreLogsId(storeId, companyId);
        }

        Map<String, Object> stores = storeList.get(0);

        if(Utils.convertToInt(stores.get("id"), -1) == -1 ){
            return initStoreLogsId(storeId, companyId);
        }else{
            return Utils.convertToLong(storeList.get(0).get("id"), -1);
        }

    }

    /**
     * 初始化门店logid
     */
    public static Long initStoreLogsId(Integer storeId, int companyId) {
        System.out.println("插入新的log");
        String sql = "INSERT INTO stores_logs(`store_id`, `company_id`, `logs`, `system`, `update_time`) VALUES(" + storeId + "," + companyId + "," + " \"第一次上报状态\", \"server\", current_timestamp())\n";


        Long id = (long) SQLHelper.executeReturnInsertLastId(sql);

        return id;
    }
//    public static void main(String[] args)  throws  Exception{
//        List<Map<String, Object>> data = getPrivateKeyByStoreId();
//        System.out.println(data.size());
//        log.info("data : {}", data);
//
//    }


    /**
     * 更新传感器表
     */
    public static void updateSensor( int companyId, Integer storeId, String sensorType, int sensorValue, int countData){

        int sensorTypeNum = Utils.getSensorNum(sensorType);
        String sql = "INSERT INTO stores_sensors (company_id, store_id, sensor_type, sensor_value, sensor_count, stm_update_time)\n" +
                "VALUES (" + companyId + ", " + storeId + ", " + sensorTypeNum + ", " + sensorValue + ", " + countData  + ", " +  "current_timestamp())";
        SQLHelper.executeUpdate(sql) ;
        //Long id = (long) SQLHelper.executeReturnInsertLastId(sql);
    }


    /**
     * 记录中控 与门店 作所属关系
     * @param companyId
     * @param storeId
     * @param store_name
     * @param type 1 开始工作  -1 失联
     */
    public static void addWorkStatusLog(int companyId, Integer storeId, String store_name, String serial_number, int type){
        String sql = "INSERT INTO stores_work_log (company_id, store_id, store_name, serial_number, type, stm_update_time)\n" +
                "VALUES (" + companyId + ", " + storeId + ", \"" + store_name + "\", " + " \"" + serial_number + "\", " + type + ", " +  "current_timestamp())";

        SQLHelper.executeUpdate(sql) ;
    }
;





}
