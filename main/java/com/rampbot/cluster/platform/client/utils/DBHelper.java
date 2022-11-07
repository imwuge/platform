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
    public static String getConfigValue(Integer configId) {
        String sql = "SELECT `value` FROM configs WHERE id = " + configId;
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
        String sql = "SELECT status FROM stores WHERE id = " + storeId;
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
    public static Map<String, Object> getStoreMap(Integer storeId, String companyId) {
        String sql = "SELECT `status`, `power`, `door_status`, `serial_number`, `private_key`, `open_seconds` FROM stores WHERE id = " + storeId + " AND company_id = " + companyId;
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
        String sql = "UPDATE stores SET `" + colums + "` = '" + value + "' WHERE id = " + storeId;
        if (value instanceof Integer) {
            sql = "UPDATE stores SET `" + colums + "` = " + value + " WHERE id = " + storeId;
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

    /**
     * 添加订单
     * @param storeId
     * @param type
     * @param isAppointHelperId 是否需要匹配客户Id
     * @return
     */
    public static boolean addMusOrders(int storeId, String companyId, String type, boolean isAppointHelperId) {
        Integer helperId = -1;
        Integer stationId = -1;
        String sql = "";

        // 判断是否已存在求助订单
        sql = "SELECT id FROM mus_orders WHERE store_id = " + storeId + " AND company_id = " + companyId + " AND order_type = '" + type + "' AND `status` in (0, 1) ORDER BY id DESC LIMIT 1";
        List<Map<String, Object>> ordersList = SQLHelper.executeQueryTable(sql);
        if (ordersList != null && ordersList.size() > 0) { // 存在正在执行或者待分配的故障订单，不进行生成
            return false;
        }

        if (isAppointHelperId) {
            sql = "SELECT helper_id, station_id FROM mus_orders WHERE store_id = " + storeId + " AND `status` = 1 LIMIT 1";
            ordersList = SQLHelper.executeQueryTable(sql);
            if (ordersList != null && ordersList.size() > 0) {
                helperId = Utils.convertToInt(ordersList.get(0).get("helper_id"), -1);
                stationId = Utils.convertToInt(ordersList.get(0).get("station_id"), -1);
            }
        }

        Long orderNo = Utils.buildMusOrderNo(type);
        Long clientId = -1l;

        sql = "INSERT INTO mus_orders (order_no, order_type, client_id, helper_id, store_id, station_id, `status`, create_time)\n" +
                "VALUES (" + orderNo + ", '" + type + "', " + clientId + ", " + helperId + ", " + storeId + ", " + stationId + ", 0, " + System.currentTimeMillis() + ")";
        return SQLHelper.executeUpdate(sql) >= 0;
    }

    /**
     * 添加 iot task 任务
     * @param storeId
     * @param actionType
     * @param status
     * @return
     */
    public static boolean addIotTasks(int storeId, int actionType, int status) {
        String sql = "INSERT INTO iot_tasks (store_id, action_type, `status`, start_time)\n" +
                "VALUES (" + storeId + ", " + actionType + ", " + status + ", " + System.currentTimeMillis() + ")";
        return SQLHelper.executeUpdate(sql) >= 0;
    }


    /**
     * 更新服务端生成的任务，去掉重复任务
     * @param storeId
     * @param actionType
     * @param status
     * @return
     */
    public static boolean addIotTasksWithoutProcessingOrPending(int storeId, int actionType, int status, String companyId) {
        String sql = "";
        sql = "SELECT id FROM iot_tasks WHERE store_id = " + storeId +  " AND company_id = " + companyId + " AND `status` in (0, 1) " + " AND `action_type` = " + actionType + " ORDER BY id ASC LIMIT 1";

        List<Map<String, Object>> pendingOrProcessingTask = SQLHelper.executeQueryTable(sql);
//        log.info("查看查询任务结果 {} ", pendingOrProcessingTask);
        if (pendingOrProcessingTask != null && pendingOrProcessingTask.size() > 0) { // 相同的action_type类型任务已经存在
            return false;
        }

        sql = "INSERT INTO iot_tasks (store_id, action_type, `status`, start_time)\n" +
                "VALUES (" + storeId + ", " + actionType + ", " + status + ", " + System.currentTimeMillis() + ")";
        return SQLHelper.executeUpdate(sql) >= 0;
    }




    public static boolean isHasProcessingOrPendingAction(int storeId, int actionType, String companyId) {

        String sql = "SELECT id FROM iot_tasks WHERE store_id = " + storeId + " AND company_id = " + companyId + " AND `status` in (0, 1) " + " AND `action_type` = " + actionType ;

        List<Map<String, Object>> pendingOrProcessingTask = SQLHelper.executeQueryTable(sql);

        return  pendingOrProcessingTask != null && pendingOrProcessingTask.size() > 0; // 相同的action_type类型任务已经存在


    }


    /**
     * 更新盒子最新状态
     * @param storeId
     * @param firmwareVersion
     * @param is_door_real_close_one
     * @param is_door_real_close_two
     * @return
     */
    public static boolean updateStoreLastHeartBeatTime(Integer storeId, String companyId, Integer firmwareVersion, Integer is_door_real_close_one, Integer is_door_real_close_two) {
        Long time = new Date().getTime();
        String sql = "UPDATE stores SET last_heartbeat_time = " + time + ", door_signal_one = " + is_door_real_close_one + ", door_signal_two = " + is_door_real_close_two + ", firmware_version = " + firmwareVersion + " WHERE id = " + storeId + " AND company_id = " + companyId;
        return SQLHelper.executeUpdate(sql) >= 0;
    }

    /**
     * 只更新心跳时间
     * @param storeId
     * @param companyId
     * @return
     */
    public static boolean updateStoreLastHeartBeatTime(Integer storeId, String companyId){
        Long time = new Date().getTime();
        String sql = "UPDATE stores SET last_heartbeat_time = " + time + " WHERE id = " + storeId + " AND company_id = " + companyId;
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
     * 获取最后一条 iot task 任务
     *
     * @param storeId
     * @return
     */
    public static Map<String, Object> getLastIotTask(Integer storeId) {
        String sql = "SELECT id, action_type, status, start_time, end_time FROM iot_tasks WHERE id = " + storeId + " ORDER BY id DESC LIMIT 1";
        List<Map<String, Object>> list = SQLHelper.executeQueryTable(sql);
        if (list == null || list.size() < 1) {
            return null;
        }

        return list.get(0);
    }

    /**
     * 获取最后一条执行中的 iot task 任务
     *
     * @param storeId
     * @return
     */
    public static Map<String, Object> getLastProcessingIotTask(Integer storeId) {
        String sql = "SELECT id, action_type, status, start_time, end_time FROM iot_tasks WHERE store_id = " + storeId + " and status in (0, 1, 9) and action_type != 500 ORDER BY id DESC LIMIT 1";
        List<Map<String, Object>> list = SQLHelper.executeQueryTable(sql);
        if (list == null || list.size() < 1) {
            return null;
        }

        return list.get(0);
    }

    /**
     * 获取最后一条 iot task 任务
     *
     * @param storeId
     * @return
     */
    public static Map<String, Object> getLastIotTask(Integer storeId, Integer status) {
        String sql = "SELECT id, action_type, status, start_time, end_time FROM iot_tasks WHERE id = " + storeId + " AND `status` = " + status + " ORDER BY id ASC LIMIT 1";
        List<Map<String, Object>> list = SQLHelper.executeQueryTable(sql);
        if (list == null || list.size() < 1) {
            return null;
        }

        return list.get(0);
    }

    /**
     * 获取最后一条 iot task 任务
     *
     * @param storeId
     * @return
     */
    public static Map<String, Object> getLastIotTask(Integer storeId, Integer status, Integer type) {
        String sql = "SELECT id, action_type, status, start_time, end_time FROM iot_tasks WHERE id = " + storeId + " AND `status` = " + status + " AND `action_type` = " + type + " ORDER BY id ASC LIMIT 1";
        List<Map<String, Object>> list = SQLHelper.executeQueryTable(sql);
        if (list == null || list.size() < 1) {
            return null;
        }

        return list.get(0);
    }

    /**
     * 获取最后一条 iot task 任务
     *
     * @param storeId
     * @return
     */
    public static List<Map<String, Object>> getPendingIotTask(Integer storeId, String companyId) {
        String sql = "SELECT id, action_type, status, start_time, end_time FROM iot_tasks WHERE store_id = " + storeId + " AND `status` = " + 0 + " AND company_id = " + companyId;
        List<Map<String, Object>> list = SQLHelper.executeQueryTable(sql);
        if (list == null || list.size() < 1) {
            return null;
        }

        return list;
    }

    /**
     * 获取还没触发的下载语音的任务
     *
     * @param storeId
     * @return
     */
    public static List<Map<String, Object>> getPendingVoiceTask(Integer storeId, String companyId) {
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
    public static boolean updateVoiceTask(Integer storeId, String companyId, Integer downloadPlace, Integer status) {
        Long time = new Date().getTime();
        String sql = "UPDATE stores_voice_stm SET `status` = " + status + ", stm_update_time = " +  time + " WHERE store_id = " + storeId + " AND sd_index = " + downloadPlace + " AND company_id = " + companyId;
        return SQLHelper.executeUpdate(sql) >= 0;
    }

    /**
     * 该actor在销毁之前需要重置没有完成下载任务的状态
     * @param storeId
     * @return
     */
    public static boolean updateVoiceTask2Pending(Integer storeId, String companyId) {
        log.info("销毁actor前 更新公司{} 门店{} 处理中但未完成的音频下载任务状态为 0", companyId, storeId);
        Long time = new Date().getTime();
        String sql = "UPDATE stores_voice_stm SET `status` = " + 0 + ", stm_update_time = " +  time + " WHERE store_id = " + storeId + " AND `status` = " + 1 + " AND company_id = " + companyId;
        return SQLHelper.executeUpdate(sql) >= 0;
    }
    /**
     * 该actor在销毁之前需要重置没有完成iot任务的状态
     * @param storeId
     * @return
     */
    public static boolean updateIotask2Pending(Integer storeId, String companyId) {
        log.info("销毁actor前 更新公司{} 门店{} 处理中但未完成的iotask任务状态为 0", companyId, storeId);
        Long time = new Date().getTime();
        String sql = "UPDATE iot_tasks SET `status` = " + 0 + ", end_time = " + time + " WHERE id = " + storeId + " AND company_id = " + companyId + " AND `status` = " + 1;
        return SQLHelper.executeUpdate(sql) >= 0;
    }


    /**
     * 获取播放音频配置
     * @param storeId
     * @return
     */
    public static List<Map<String, Object>> getPlayVoiceTask(Integer storeId, String companyId) {
        String sql = "SELECT\n" +
                "\tid,\n" +
                "\t`play_count`,\n" +
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
    public static Map<String, Object> getVoice(Integer voiceId, String companyId) {
        String sql = "SELECT id, file_url, version FROM voices WHERE id = " + voiceId + " AND status = " + 0 + " AND company_id = " + companyId;
        List<Map<String, Object>> list = SQLHelper.executeQueryTable(sql);
        if (list == null || list.size() < 1) {
            return null;
        }

        return list.get(0);
    }

    /**
     * 更新iot任务状态
     * @param taskId
     * @param status
     * @return
     */
    public static boolean updateIotTaskById(long taskId, Integer status, String companyId) {
        Long time = new Date().getTime();
        String sql = "UPDATE iot_tasks SET `status` = " + status + ", end_time = " + time + " WHERE id = " + taskId + " AND company_id = " + companyId;
        return SQLHelper.executeUpdate(sql) >= 0;
    }

    /**
     * 更新stores门店 门状态
     * @param storeId
     * @param status
     * @param companyId
     * @return
     */
    public static boolean updateStoreDoorStatus(Integer storeId, Integer status, String companyId) {
        Long time = new Date().getTime();
        String sql = "UPDATE stores SET door_status = " + status + " WHERE id = " + storeId + " AND company_id = " + companyId;
        return SQLHelper.executeUpdate(sql) >= 0;
    }

    /**
     * 获取最后一条 通知任务
     *
     * @param storeId
     * @return
     */
    public static Map<String, Object> getLastNotify(Integer storeId, Integer level, String companyId) {
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
    public static Integer getMaxVersionVoice(Integer storeId, String companyId) {
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
    public static Map<String, Object> getFristVersionVoice(Integer storeId, Integer version, String companyId) {
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
        String sql = "select id , private_key, company_id from stores where serial_number = '" + serialNumber + "' limit 1";
        List<Map<String, Object>> data = SQLHelper.executeQueryTable(sql);
        if (data == null || data.size() < 1) {
            return null;
        }

//        int storeId = Utils.convertToInt(data.get(0).get("id"), -1);
        return data.get(0);
    }



    /**
     * 通过门店编号获取通信 Key
     * @param storeId
     * @return
     */
    public static String getPrivateKeyByStoreId(Integer storeId) {

        String sql = "select private_key from stores where id = " + storeId;
        List<Map<String, Object>> data = SQLHelper.executeQueryTable(sql);
        if (data == null || data.size() < 1 || data.get(0).get("private_key") == null) {
            return null;
        }

        return data.get(0).get("private_key").toString();
    }
}
