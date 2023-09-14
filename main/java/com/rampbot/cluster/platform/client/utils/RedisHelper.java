package com.rampbot.cluster.platform.client.utils;


import com.rampbot.cluster.platform.domain.Notify;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.*;

import com.alibaba.fastjson.JSON;

@Slf4j
public class RedisHelper {


//    【MySQL】
//    Ip：dbmusmain.internal.cn-north-4.mysql.rds.myhuaweicloud.com
//    Port：4001
//    Us：root
//    Pwd：2A86F7f1B1A86308
//
//   【Redis】
//    Ip：192.168.0.249
//    Port：9379
//    Pwd：G84Kd_n3L51Gh*n2D{v




//    本地测试服务器
//    public static final String REDIS_HOST = "60.6.202.67";

////    本地测试
//    public static final String REDIS_HOST = "localhost";

//    华为云服务器 内网访问
    public static final String REDIS_HOST = "192.168.0.52";  // root   A8$6F#7F1_B1A|86

//    世纪城服务器
//    public static final String REDIS_HOST = "192.168.0.52";

//    端口
    public static final Integer REDIS_PORT = 9379;

    public static final String REDIS_PWD = "G84Kd_n3L51Gh*n2D{v";
    // redis 数据库索引
    //    索引：0  是音频对讲使用的内存
    //    索引：1 支付信息存储的内存
    //    索引：2 通知、订单的内存
    public static Integer ORDER_NOTIFY_INDEX = 2;
    public static Integer TASK_INDEX = 3;
    public static Integer VOICE_TASK_INDEX = 4;

    // 测试
//    public static Integer ORDER_NOTIFY_INDEX = 6;
//    public static Integer TASK_INDEX = 7;
//    private static final Jedis REDIS_MUS = new Jedis(REDIS_HOST, REDIS_PORT);
    // 存在时间
    public static final Long ONE_HOURS = 1 * 60 * 60L;

    public static JedisPool pool = null;



//    //从连接池中得到可用的jedis对象
//    Jedis jedis = pool.getResource();
//    //通过jedis操作Redis数据库
//        jedis.set("t2","DaoXiaoMian");
//    //关闭jedis对象，将这个对象放回Jedis连接池中
//        jedis.close();



    static {
        try{
            JedisPoolConfig config =new JedisPoolConfig();
            config.setMaxTotal(100);//最大提供的连接数
            config.setMaxIdle(20);//最大空闲连接数(即初始化提供了100有效的连接数)
            config.setMinIdle(10);//最小保证的提供的（空闲）连接数
            config.setMaxWaitMillis(10 * 1000);
            config.setTestOnBorrow(true);
            //创建Jedis连接池
            pool = new JedisPool(config,REDIS_HOST,REDIS_PORT, 2000, REDIS_PWD);

//            REDIS_MUS.auth(REDIS_PWD);
//            REDIS_MUS.select(REDIS_INDEX);

        }catch (Exception e){
            e.printStackTrace();
        }
    }



    // 格式 -> <模块名>:<商户编号>:<门店编号>:<类型>:<坐席编号>:<服务单号>:<状态>
    //        <模块名> ORDERS
    //        <类型> 0 购物，1 补签，2 安防，3 店内求助，4 故障，5 巡店
    //        <坐席编号> -1 待分配， 其他 已分配
    //        <状态> 0 待分配，1 服务中
    //         备注 -> 只保状态为 0、1 的数据，不是则删除 Redis 队列
    //         示例 -> ORDERS:10050:10092:1:-1:123013102992012

    // 格式 -> <模块名>:<商户编号>:<门店编号>:<类型>:<坐席编号>:<消息主键ID>:<级别>
    //        <模块名> NOTIFY
    //        <类型> 1 离店通知，7 托管消息，8 断电，9 失联
    //        <坐席编号> -1 待分配， 其他 已分配
    //        <级别> 0 静默提示，1 弹窗提示
    //         备注 -> 只保留未读消息，已读则删除 Redis 队列
    //         示例 -> NOTIFY:10050:10092: 9:-1:33122:1


    // 格式 -> <模块名>:<商户编号>:<门店编号>:<类型>:<坐席编号>:<务主键ID>:<状态>
    //        <模块名> TASK
    //        <类型>  901 全开门、  902 全关门、  903 进店开门、 904 进店关门、  905 离店开门、 906 离店关门
    //        <坐席编号> 预留字段
    //        <状态> 0 待执行，1 执行中，9 待审核
    //         备注 -> 只保状态为 0、1、9 的数据，不是则删除 Redis 队列
    //         示例 -> TASK:10050:10092: 901:18:12049:0


    // -----------> 音频 Redis Key 规范
    // 格式 -> <模块名>:<商户编号>:<门店编号>:<音频ID>:<坐席编号>:<任务主键ID>:<状态>
    // 注解 -> <状态> 0 待播放 1 播放中  2 已播放  -1 过期音频不予播放
    // 备注 -> 只保留状态为 0、1 的数据，不是则删除 Redis 队列
    // 示例 -> V-TASK:10050:10092:192:8:12049:0

    /**
     * 获取客服id
     * @param companyId
     * @param storeId
     * @return
     */
//    public static Integer getHelperId( int companyId, int storeId){
//
//
//        Integer heaperIdRead = null;
//        Integer orderTypeRead = null;
//
//
//        String patternKeyOrder = String.format("ORDERS:%d:%d:*", companyId, storeId);
//        Jedis jedis = pool.getResource();
//        jedis.auth(REDIS_PWD);
//        jedis.select(ORDER_NOTIFY_INDEX);
//        Set<String> keys = jedis.keys(patternKeyOrder);
//        if (keys != null && keys.size() >= 1) {
//            for (String key : keys) {
//                String[] keyArray = key.split("\\:");
//                if (keyArray.length >= 7) {
//                    if(heaperIdRead == null || heaperIdRead == -1){// 指定客服
//                        orderTypeRead = Utils.convertToInt(keyArray[3], -1);
//                        if(orderTypeRead != 5){
//                            heaperIdRead = Utils.convertToInt(keyArray[4], -1);
//                            if(heaperIdRead != -1){
//                                break;
//                            }
//                        }
//
//                    }
//                }
//            }
//        }
//
//        // 查询通知表中的客服id
////        if(heaperIdRead == null || heaperIdRead == -1){
////            String patternKeyNotify = String.format("NOTIFY:%d:%d:*",  companyId, storeId);
////            Set<String> keysNotify = REDIS_MUS.keys(patternKeyNotify);
////            if (keysNotify != null && keysNotify.size() >= 1) {
////                for (String key : keysNotify) {
////                    String[] keyArray = key.split("\\:");
////                    if (keyArray.length >= 6) {
////                        heaperIdRead = Utils.convertToInt(keyArray[4], -1);
////                        if(heaperIdRead != -1){
////                            break;
////                        }
////                    }
////                }
////            }
////        }
//
//
//        jedis.close();
//        /**
//         * heaperIdRead 有三种结果：
//         *      1 没有订单为null
//         *      2 有订单未分配为 -1
//         *      3 已分配结果
//         */
//        return heaperIdRead == null ? -1 : heaperIdRead;
//    }
    public static Integer getHelperId( int companyId, int storeId){


        Integer heaperIdRead = null;
        Integer orderTypeRead = null;

        Jedis jedis = null;
        try {
            jedis =  pool.getResource();
            if(jedis != null) {
                jedis.select(ORDER_NOTIFY_INDEX);
                String patternKeyOrder = String.format("ORDERS:%d:%d:*", companyId, storeId);
                Set<String> keys = jedis.keys(patternKeyOrder);
                if (keys != null && keys.size() >= 1) {
                    for (String key : keys) {
                        String[] keyArray = key.split("\\:");
                        if (keyArray.length >= 7) {
                            if(heaperIdRead == null || heaperIdRead == -1){// 指定客服
                                orderTypeRead = Utils.convertToInt(keyArray[3], -1);
                                if(orderTypeRead != 5){
                                    heaperIdRead = Utils.convertToInt(keyArray[4], -1);
                                    if(heaperIdRead != -1){
                                        break;
                                    }
                                }

                            }
                        }
                    }
                }
            }else{
                log.error("门店{}getHelperId获取jedis失败" ,storeId);
            }
        }catch(Exception e) {
            if(jedis != null) {
                jedis.close();
                jedis = null;
//                pool.returnBrokenResource(jedis);
            }
            log.error("门店{}获取redis出现问题 {}" ,storeId, e);
        }finally {
            if(jedis != null) {
                jedis.close();
//                pool.returnResource(jedis);
            }
        }

        /**
         * heaperIdRead 有三种结果：
         *      1 没有订单为null
         *      2 有订单未分配为 -1
         *      3 已分配结果
         */
        return heaperIdRead == null ? -1 : heaperIdRead;
    }

    /**
     * 判断是否还有订单（购物、求助、安防），若有不关灯
     * @param companyId
     * @param storeId
     * @return
     */
    public static  Set<String> isExistsOrderCannotCloseInLight(int companyId, int storeId){

        Set<String> keys = null;

        Jedis jedis = null;
        try {

            String patternKey = String.format("%s:%d:%d:*", "ORDERS", companyId, storeId);
            jedis =  pool.getResource();
            if(jedis != null) {
                jedis.select(ORDER_NOTIFY_INDEX);
                keys = jedis.keys(patternKey);
            }else{
                log.error("门店{}isExistsOrderCannotCloseInLight获取jedis失败" ,storeId);
            }
        }catch(Exception e) {
            if(jedis != null) {
                jedis.close();
//                pool.returnBrokenResource(jedis);
            }
            log.error("门店{}获取redis出现问题 {}" ,storeId, e);
        }finally {
            if(jedis != null) {
                jedis.close();
//                pool.returnResource(jedis);
            }
        }

        return keys;

    }

    /**
     * 判断是否存在正在执行或待分配的订单
     * @param companyId
     * @param storeId
     * @param type
     * @return
     */
//    public static boolean isExistsOrder(int companyId, int storeId, String type){
////        REDIS_MUS.auth(REDIS_PWD);
////        REDIS_MUS.select(REDIS_INDEX);
//        String moduleName = Utils.getModuleName(type);
//        int typeNumber = Utils.getTypeNum(type);
//        String patternKey = String.format("%s:%d:%d:%d:*", moduleName, companyId, storeId, typeNumber);
////        Set<String> keys = REDIS_MUS.keys(patternKey);
//        Set<String> keys = null;
//        try {
//            Jedis jedis = pool.getResource();
//            jedis.auth(REDIS_PWD);
//            jedis.select(ORDER_NOTIFY_INDEX);
//            keys = jedis.keys(patternKey);
//            jedis.close();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return keys != null && keys.size() >= 1;
//
//    }

    public static boolean isExistsOrder(int companyId, int storeId, String type){

        Set<String> keys = null;

        Jedis jedis = null;
        try {
            String moduleName = Utils.getModuleName(type);
            int typeNumber = Utils.getTypeNum(type);
            String patternKey = String.format("%s:%d:%d:%d:*", moduleName, companyId, storeId, typeNumber);
            jedis =  pool.getResource();
            if(jedis != null) {
                jedis.select(ORDER_NOTIFY_INDEX);
                keys = jedis.keys(patternKey);
            }else{
                log.error("门店{}isExistsOrder获取jedis失败" ,storeId);
            }
        }catch(Exception e) {
            if(jedis != null) {
                jedis.close();
//                pool.returnBrokenResource(jedis);
            }
            log.error("门店{}获取redis出现问题 {}" ,storeId, e);
        }finally {
            if(jedis != null) {
                jedis.close();
//                pool.returnResource(jedis);
            }
        }

        return keys != null && keys.size() >= 1;

    }

    /**
     * 是否存在待处理的安防订单
     * @param companyId
     * @param storeId
     * @return
     */
    public static boolean isExistsPendingSaftOrder(int companyId, int storeId){
//        REDIS_MUS.auth(REDIS_PWD);
//        REDIS_MUS.select(REDIS_INDEX);
        String moduleName = Utils.getModuleName("安防");
        int typeNumber = Utils.getTypeNum("安防");
        String patternKey = String.format("%s:%d:%d:%d:*", moduleName, companyId, storeId, typeNumber);
//        Set<String> keys = REDIS_MUS.keys(patternKey);
        Set<String> keys = null;
        Jedis jedis = null;
        try {
            keys = null;
            jedis = pool.getResource();
            jedis.auth(REDIS_PWD);
            jedis.select(ORDER_NOTIFY_INDEX);
            keys = jedis.keys(patternKey);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Integer heaperIdRead = null;
        if(keys != null && keys.size() >= 1){
            for (String key : keys) {
                String[] keyArray = key.split("\\:");
                if (keyArray.length >= 6) {
                    // 指定客服
                    heaperIdRead = Utils.convertToInt(keyArray[4], -1);
                    if(heaperIdRead != -1){
                        break;
                    }
                }
            }
        }
        jedis.close();
        return heaperIdRead != null && heaperIdRead == -1;

    }

//    public static boolean isExistsPendingSaftOrderV2(int companyId, int storeId){
////        REDIS_MUS.auth(REDIS_PWD);
////        REDIS_MUS.select(REDIS_INDEX);
//        String moduleName = Utils.getModuleName("安防");
//        int typeNumber = Utils.getTypeNum("安防");
//        String patternKey = String.format("%s:%d:%d:%d:*", moduleName, companyId, storeId, typeNumber);
//        Set<String> keys = null;
//        Jedis jedis = null;
//        try {
//            keys = null;
//            jedis = pool.getResource();
//            jedis.auth(REDIS_PWD);
//            jedis.select(ORDER_NOTIFY_INDEX);
//            keys = jedis.keys(patternKey);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        Integer heaperIdRead = null;
//        if(keys != null && keys.size() >= 1){
//            for (String key : keys) {
//                String[] keyArray = key.split("\\:");
//                if (keyArray.length >= 7) {
//                    // 指定客服
//                    if(Utils.convertToInt(keyArray[6], -1) == 0){
//                        return true;
//                    }
//                }
//            }
//        }
//
//        if(jedis != null){
//            jedis.close();
//        }
//
//        return false;
//
//    }
    public static boolean isExistsPendingSaftOrderV2(int companyId, int storeId){

        String moduleName = Utils.getModuleName("安防");
        int typeNumber = Utils.getTypeNum("安防");
        String patternKey = String.format("%s:%d:%d:%d:*", moduleName, companyId, storeId, typeNumber);

        Jedis jedis = null;
        try {
            Set<String> keys = null;
            jedis = pool.getResource();
            if(jedis != null){
                jedis.select(ORDER_NOTIFY_INDEX);
                keys = jedis.keys(patternKey);
                if(keys != null && keys.size() >= 1){
                    for (String key : keys) {
                        String[] keyArray = key.split("\\:");
                        if (keyArray.length >= 7) {
                            // 指定客服
                            if(Utils.convertToInt(keyArray[6], -1) == 0){
                                return true;
                            }
                        }
                    }
                }
            }else{
                log.error("门店{}isExistsPendingSaftOrderV2获取jedis失败" ,storeId);
            }
        }catch(Exception e) {
            if(jedis != null) {
                jedis.close();
//                pool.returnBrokenResource(jedis);
            }
            log.error("门店{}获取redis出现问题 {}" ,storeId, e);
        }finally {
            if(jedis != null) {
                jedis.close();
//                pool.returnResource(jedis);
            }
        }
        return false;

    }

    /**
     * 写入 订单表或者通知表
     * @param companyId
     * @param storeId
     * @param orderNo
     * @param type
     * @param title
     * @param content
     * @param helperId
     * @param statusOrLevel
     */
//    public static void writeOrderOrNotify(int companyId, int storeId, Long orderNo, String type, String title, String content, int helperId, int statusOrLevel){
////        String redisKey = String.format("NOTIFY:%d:%s:%d:%d:%d", ERedisNotifyType.MUS_ORDERS.getStatusCode(), companyId, storeId, -1, orderNo);
//        String moduleName = Utils.getModuleName(type);
//        int typeNumber = Utils.getTypeNum(type);
//
//        String redisKey = String.format("%s:%d:%d:%d:%d:%d:%d", moduleName, companyId, storeId, typeNumber, helperId, orderNo, statusOrLevel);
//        String redisValue = null;
//
//        Notify notify = new Notify();
//        notify.setId(orderNo);
//        notify.setStatus(0);
////        notify.setTitle("【购物】新订单通知");
//        notify.setTitle(title);
////        notify.setType(ERedisNotifyType.MUS_ORDERS.getStatusCode());
//        notify.setType(moduleName.equals("ORDERS") ? 0 : 1);
//        notify.setNotifyType(typeNumber);
////        notify.setContent(String.format("门店：{0}[{1}]\\r\\n会员用户：{2}\\r\\n信誉分：{3} {4} {5}\\r\\n\\r\\n手机号码：{6}"));
//        notify.setContent(String.format(content));
//        redisValue = JSON.toJSONString(notify);
//        // 存放时长 OFHOURS小时，如果不是主动销毁，则OFHOURS小时后自动回收
////        REDIS_MUS.auth(REDIS_PWD);
////        REDIS_MUS.select(REDIS_INDEX);
////        REDIS_MUS.set(redisKey, redisValue, String.valueOf(Duration.ofHours(OFHOURS)));
////        REDIS_MUS.set(redisKey, redisValue);
////        REDIS_MUS.expire(redisKey,OFHOURS);
//        try {
//            Jedis jedis = pool.getResource();
//            jedis.auth(REDIS_PWD);
//            jedis.select(ORDER_NOTIFY_INDEX);
//            jedis.set(redisKey, redisValue);
//            jedis.expire(redisKey,Utils.GET_OFHOURS(type));
//            jedis.close();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
    public static void writeOrderOrNotify(int companyId, int storeId, Long orderNo, String type, String title, String content, int helperId, int statusOrLevel){

        String moduleName = Utils.getModuleName(type);
        int typeNumber = Utils.getTypeNum(type);

        String redisKey = String.format("%s:%d:%d:%d:%d:%d:%d", moduleName, companyId, storeId, typeNumber, helperId, orderNo, statusOrLevel);
        String redisValue = null;

        Notify notify = new Notify();
        notify.setId(orderNo);
        notify.setStatus(0);
//        notify.setTitle("【购物】新订单通知");
        notify.setTitle(title);
//        notify.setType(ERedisNotifyType.MUS_ORDERS.getStatusCode());
        notify.setType(moduleName.equals("ORDERS") ? 0 : 1);
        notify.setNotifyType(typeNumber);
//        notify.setContent(String.format("门店：{0}[{1}]\\r\\n会员用户：{2}\\r\\n信誉分：{3} {4} {5}\\r\\n\\r\\n手机号码：{6}"));
        notify.setContent(String.format(content));
        redisValue = JSON.toJSONString(notify);



        Jedis jedis = null;
        try {
            jedis = pool.getResource();
            if(jedis != null){
                jedis.select(ORDER_NOTIFY_INDEX);

                String resoult = "init";
                resoult = jedis.set(redisKey, redisValue);
                log.info("门店{}插入key {}，value {} 的结果为 {} ", storeId, redisKey, redisValue, resoult);
                jedis.expire(redisKey,Utils.GET_OFHOURS(type));
            }else{
                log.error("门店{}writeOrderOrNotify获取jedis失败" ,storeId);
            }

        }catch(Exception e) {
            if(jedis != null) {
                jedis.close();
//                pool.returnBrokenResource(jedis);
            }
            log.error("门店{}获取redis出现问题 {}" ,storeId, e);
        }finally {
            if(jedis != null) {
                jedis.close();
//                pool.returnResource(jedis);
            }
        }


    }
    /**
     *
     * @param companyId
     * @param storeId
     * @param type  可以不指定，表示检索所有任务类型
     * @param stats   可以不指定表示检索0、1  指定0表示检索未处理  指定1表示检索处理中
     * @return
     * <模块名>0:<商户编号>1:<门店编号>2:<类型>3:<坐席编号>4:<务主键ID>5:<状态>6
     */
//    public static List<Map<String, Object>> getTask(int companyId, int storeId , Integer type, Integer stats){
//
//
//        String patternKey = "";
//
//        if(type == null){
//            patternKey  = String.format("%s:%d:%d:*", "TASK", companyId, storeId);
//        }else {
//            patternKey  = String.format("%s:%d:%d:%d:*", "TASK", companyId, storeId, type);
//        }
//
//        List<Map<String, Object>> actionList= new LinkedList<>();
//        Set<String> keys = null;
//        Jedis jedis = null;
//        try {
//            keys = null;
//            jedis = pool.getResource();
//            jedis.auth(REDIS_PWD);
//            jedis.select(TASK_INDEX);
//            keys = jedis.keys(patternKey);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        if(keys != null && keys.size() >= 1){
//            for (String key : keys) {
//                String[] keyArray = key.split("\\:");
//                if (keyArray.length >= 7) {
//                    int readStats = Utils.convertToInt(keyArray[6], -1);
//                    long id = Utils.convertToLong(keyArray[5], -1);
//                    int actionType = Utils.convertToInt(keyArray[3], -1);
//                    if(stats == null){
//                        if(readStats == 0 || readStats == 1){
//                            Map<String, Object> rowData = new HashMap<String, Object>();
//                            rowData.put("id", id);
//                            rowData.put("action_type", actionType);
//                            actionList.add(rowData);
//                        }
//                    }else{
//                        if(readStats == stats){
//                            Map<String, Object> rowData = new HashMap<String, Object>();
//                            rowData.put("id", id);
//                            rowData.put("action_type", actionType);
//                            actionList.add(rowData);
//                        }
//                    }
//
//                }
//            }
//        }
//
//        if(jedis != null){
//            jedis.close();
//        }
//        // log.info("查询门店iot {} 状态 {} 结果为 {}", type, stats , actionList);
//        return actionList;
//
//    }

    /**
     * 获取iot
     * @param companyId
     * @param storeId
     * @param type
     * @param stats
     * @return
     */
    public static List<Map<String, Object>> getTask(int companyId, int storeId , Integer type, Integer stats){

        String patternKey = "";

        if(type == null){
            patternKey  = String.format("%s:%d:%d:*", "TASK", companyId, storeId);
        }else {
            patternKey  = String.format("%s:%d:%d:%d:*", "TASK", companyId, storeId, type);
        }

        List<Map<String, Object>> actionList= new LinkedList<>();
        Set<String> keys = null;
        Jedis jedis = null;
        try {
            jedis = pool.getResource();
            if(jedis != null){
                jedis.select(TASK_INDEX);
                keys = jedis.keys(patternKey);
                if(keys != null && keys.size() >= 1){
                    for (String key : keys) {
                        String[] keyArray = key.split("\\:");
                        if (keyArray.length >= 7) {
                            int readStats = Utils.convertToInt(keyArray[6], -1);
                            long id = Utils.convertToLong(keyArray[5], -1);
                            int actionType = Utils.convertToInt(keyArray[3], -1);
                            if(stats == null){
                                if(readStats == 0 || readStats == 1){
                                    Map<String, Object> rowData = new HashMap<String, Object>();
                                    rowData.put("id", id);
                                    rowData.put("action_type", actionType);
                                    actionList.add(rowData);
                                }
                            }else{
                                if(readStats == stats){
                                    Map<String, Object> rowData = new HashMap<String, Object>();
                                    rowData.put("id", id);
                                    rowData.put("action_type", actionType);
                                    actionList.add(rowData);
                                }
                            }

                        }
                    }
                }
            }else{
                log.error("门店{}getTask获取jedis失败" ,storeId);
            }


        }catch(Exception e) {
            if(jedis != null) {
                jedis.close();
//                pool.returnBrokenResource(jedis);
            }
            log.error("门店{}获取redis出现问题 {}" ,storeId, e);
        }finally {
            if(jedis != null) {
                jedis.close();
//                pool.returnResource(jedis);
            }
        }
        return actionList;

    }



    // 格式 -> <模块名>0:<商户编号>1:<门店编号>2:<类型>3:<坐席编号>4:<务主键ID>5:<状态>6
    //        <模块名> TASK
    //        <类型>  901 全开门、  902 全关门、  903 进店开门、 904 进店关门、  905 离店开门、 906 离店关门
    //        <坐席编号> 预留字段
    //        <状态> 0 待执行，1 执行中，9 待审核
    //         备注 -> 只保状态为 0、1、9 的数据，不是则删除 Redis 队列
    //         示例 -> TASK:10050:10092: 901:18:12049:0


//    public static boolean checkAutoOpenDoor( int companyId, Integer storeId, long intervalMilliseconds){
//        String patternKey = String.format("%s:%d:%d:*", "TASK", companyId, storeId);
//        Set<String> keys = null;
//        Jedis jedis = null;
//        try {
//            keys = null;
//            jedis = pool.getResource();
//            jedis.auth(REDIS_PWD);
//            jedis.select(TASK_INDEX);
//            keys = jedis.keys(patternKey);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        if(keys != null && keys.size() >= 1){
//            for (String key : keys) {
//                String[] keyArray = key.split("\\:");
//                if (keyArray.length >= 7) {
//                    if( Utils.convertToInt(keyArray[6], -1) == 9){
//                        if(ONE_HOURS - jedis.ttl(key) > intervalMilliseconds / 1000){
//                            int actionType = Utils.convertToInt(keyArray[3], -1);
//                            int helperId = Utils.convertToInt(keyArray[4], -1);
//                            long id = Utils.convertToLong(keyArray[5], -1);
//                            String newKey = String.format("%s:%d:%d:%d:%d:%d:%d", "TASK", companyId, storeId, actionType, helperId, id, 0);
//                            jedis.rename(key, newKey);
//                            return true;
//                        }
//
//                    }
//                }
//            }
//        }
//
//        if(jedis != null){
//            jedis.close();
//        }
//        return false;
//    }

    /**
     * 检查状态为9 需要超时自动执行的任务
     * @param companyId
     * @param storeId
     * @param intervalMilliseconds
     * @return
     */
    public static boolean checkAutoOpenDoor( int companyId, Integer storeId, long intervalMilliseconds){
        String patternKey = String.format("%s:%d:%d:*", "TASK", companyId, storeId);
        Set<String> keys = null;
        Jedis jedis = null;
        try {
            jedis = pool.getResource();
            if(jedis != null){
                jedis.select(TASK_INDEX);
                keys = jedis.keys(patternKey);
                if(keys != null && keys.size() >= 1){
                    for (String key : keys) {
                        String[] keyArray = key.split("\\:");
                        if (keyArray.length >= 7) {
                            if( Utils.convertToInt(keyArray[6], -1) == 9){
                                if(ONE_HOURS - jedis.ttl(key) > intervalMilliseconds / 1000){
                                    int actionType = Utils.convertToInt(keyArray[3], -1);
                                    int helperId = Utils.convertToInt(keyArray[4], -1);
                                    long id = Utils.convertToLong(keyArray[5], -1);
                                    String newKey = String.format("%s:%d:%d:%d:%d:%d:%d", "TASK", companyId, storeId, actionType, helperId, id, 0);
                                    jedis.rename(key, newKey);
                                    return true;
                                }

                            }
                        }
                    }
                }
            }else{
                log.error("门店{}checkAutoOpenDoor获取jedis失败" ,storeId);
            }

        }catch(Exception e) {
            if(jedis != null) {
                jedis.close();
//                pool.returnBrokenResource(jedis);
            }
            log.error("门店{}checkAutoOpenDoor获取redis出现问题" ,storeId);
        }finally {
            if(jedis != null) {
                jedis.close();
//                pool.returnResource(jedis);
            }
        }
        return false;
    }

    /**
     * 取消自动开门
     * @param companyId
     * @param storeId
     * @return
     */
    public static boolean cancelAutoOpenDoor( int companyId, Integer storeId){
        String patternKey = String.format("%s:%d:%d:*", "TASK", companyId, storeId);
        Set<String> keys = null;
        Jedis jedis = null;
        try {
            jedis = pool.getResource();
            if(jedis != null){
                jedis.select(TASK_INDEX);
                keys = jedis.keys(patternKey);
                if(keys != null && keys.size() >= 1){
                    for (String key : keys) {
                        String[] keyArray = key.split("\\:");
                        if (keyArray.length >= 7) {
                            if( Utils.convertToInt(keyArray[6], -1) == 9){

                                long id = Utils.convertToLong(keyArray[5], -1);
                                log.info("门店{}删除自动任务 {}", storeId, id);
                                jedis.del(key);
                                return true;

                            }
                        }
                    }
                }
            }else{
                log.error("门店{}checkAutoOpenDoor获取jedis失败" ,storeId);
            }

        }catch(Exception e) {
            if(jedis != null) {
                jedis.close();
//                pool.returnBrokenResource(jedis);
            }
            log.error("门店{}checkAutoOpenDoor获取redis出现问题" ,storeId);
        }finally {
            if(jedis != null) {
                jedis.close();
//                pool.returnResource(jedis);
            }
        }
        return false;
    }

//    public static void updateIotask2Complete( int companyId, Integer storeId , Long taskId, int status){
//        String patternKey = String.format("%s:%d:%d:*", "TASK", companyId, storeId);
//        Set<String> keys = null;
//        Jedis jedis = null;
//        try {
//            keys = null;
//            jedis = pool.getResource();
//            jedis.auth(REDIS_PWD);
//            jedis.select(TASK_INDEX);
//            keys = jedis.keys(patternKey);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        if(keys != null && keys.size() >= 1){
//            if(status == 1){
//                for (String key : keys) {
//                    String[] keyArray = key.split("\\:");
//                    if (keyArray.length >= 7) {
//                        if(taskId != null){
//                            long id = Utils.convertToLong(keyArray[5], -1);
//                            if(id == taskId){
//                                // log.info("更新key 老{}", key);
//                                int actionType = Utils.convertToInt(keyArray[3], -1);
//                                int helperId = Utils.convertToInt(keyArray[4], -1);
//                                String newKey = String.format("%s:%d:%d:%d:%d:%d:%d", "TASK", companyId, storeId, actionType, helperId, id, 1);
//                                // log.info("更新key 新{}", newKey);
//                                jedis.rename(key, newKey);
//                            }
//                        }
//
//                    }
//                }
//            }else if(status == 2){
//                for (String key : keys) {
//                    String[] keyArray = key.split("\\:");
//                    if (keyArray.length >= 7) {
//                        if(taskId == null){
//                            // log.info("删除key {}", key);
//                            jedis.del(key);
//                        }else{
//                            long id = Utils.convertToLong(keyArray[5], -1);
//                            if(id == taskId){
//                                // log.info("删除key {}", key);
//                                jedis.del(key);
//                            }
//                        }
//
//                    }
//                }
//            }
//
//        }
//
//        if(jedis != null){
//            jedis.close();
//        }
//    }

    /**
     * 完成redis中的iot任务
     * @param companyId
     * @param storeId
     * @param taskId
     * @param status
     */
    public static void updateIotask2Complete( int companyId, Integer storeId , Long taskId, int status){
        String patternKey = String.format("%s:%d:%d:*", "TASK", companyId, storeId);
        Set<String> keys = null;
        Jedis jedis = null;
        try {
            jedis = pool.getResource();
            if(jedis != null){
                jedis.select(TASK_INDEX);
                keys = jedis.keys(patternKey);
                if(keys != null && keys.size() >= 1){
                    if(status == 1){
                        for (String key : keys) {
                            String[] keyArray = key.split("\\:");
                            if (keyArray.length >= 7) {
                                if(taskId != null){
                                    long id = Utils.convertToLong(keyArray[5], -1);
                                    if(id == taskId){
                                        // log.info("更新key 老{}", key);
                                        int actionType = Utils.convertToInt(keyArray[3], -1);
                                        int helperId = Utils.convertToInt(keyArray[4], -1);
                                        String newKey = String.format("%s:%d:%d:%d:%d:%d:%d", "TASK", companyId, storeId, actionType, helperId, id, 1);
                                        // log.info("更新key 新{}", newKey);
                                        jedis.rename(key, newKey);
                                    }
                                }

                            }
                        }
                    }else if(status == 2 || status == -2){
                        for (String key : keys) {
                            String[] keyArray = key.split("\\:");
                            if (keyArray.length >= 7) {
                                if(taskId == null){
                                    // log.info("删除key {}", key);
                                    jedis.del(key);
                                }else{
                                    long id = Utils.convertToLong(keyArray[5], -1);
                                    if(id == taskId){
                                        // log.info("删除key {}", key);
                                        jedis.del(key);
                                    }
                                }

                            }
                        }
                    }

                }
            }else{
                log.error("门店{}updateIotask2Complete获取jedis失败" ,storeId);
            }

        }catch(Exception e) {
            if(jedis != null) {
                jedis.close();
//                pool.returnBrokenResource(jedis);
            }
            log.error("门店{}获取redis出现问题 {}" ,storeId, e);
        }finally {
            if(jedis != null) {
                jedis.close();
//                pool.returnResource(jedis);
            }
        }

    }


    /**
     * 当一个tcp链接断开时候，删除未完成的任务
     * @param equipmentId
     */
    public static void deleteKey( String equipmentId){

        Map<String, Object> storeMsgMap = DBHelper.getStoreIdAndCompanyIdBySerialNumber(equipmentId);
        int storeId = -1;
        int companyId = -1;
        if(storeMsgMap != null && storeMsgMap.size() > 0){
            storeId = Utils.convertToInt(storeMsgMap.get("store_id"), -1);
            companyId = Utils.convertToInt(storeMsgMap.get("company_id").toString(), -1);
        }

        // 校验数据
        if(storeId == -1 || companyId == -1){
            log.info("门店{}获取数据失败，无法删除iot任务", equipmentId);
            return;
        }

        String patternKey = String.format("%s:%d:%d:*", "TASK", companyId, storeId);
        Set<String> keys = null;
        Jedis jedis = null;
        try {
            jedis = pool.getResource();
            if(jedis != null){
                jedis.select(TASK_INDEX);
                keys = jedis.keys(patternKey);
                if(keys != null && keys.size() >= 1){
                    for (String key : keys) {
                        jedis.del(key);
                        String[] keyArray = key.split("\\:");
                        long id = Utils.convertToLong(keyArray[5], -1);
                        log.info("门店{}删除任务{}", equipmentId, id);
                        if(id != -1){
                            DBHelper.updateIotTaskById(id, companyId, storeId);
                        }
                    }
                }
            }else{
                log.error("门店{}updateIotask2Complete获取jedis失败" ,storeId);
            }

        }catch(Exception e) {
            if(jedis != null) {
                jedis.close();
//                pool.returnBrokenResource(jedis);
            }
            log.error("门店{}获取redis出现问题 {}" ,storeId, e);
        }finally {
            if(jedis != null) {
                jedis.close();
//                pool.returnResource(jedis);
            }
        }

    }

//    public static void addIotTasks( int companyId, Integer storeId, int type, long id, int stats){
//        String patternKey = String.format("%s:%d:%d:%d:%d:%d:%d", "TASK", companyId, storeId, type, -1, id, stats);
//        // log.info("增加门店key {}",patternKey );
//        Set<String> keys = null;
//        try {
//            Jedis jedis = pool.getResource();
//            jedis.auth(REDIS_PWD);
//            jedis.select(TASK_INDEX);
//            jedis.set(patternKey, "");
//            jedis.expire(patternKey,1 * 60 * 60);
//            jedis.close();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }


    /**
     * 向redis注入iot任务
     * @param companyId
     * @param storeId
     * @param type
     * @param id
     * @param stats
     */
    public static void addIotTasks( int companyId, Integer storeId, int type, long id, int stats){
        String patternKey = String.format("%s:%d:%d:%d:%d:%d:%d", "TASK", companyId, storeId, type, -1, id, stats);
        Jedis jedis = null;
        try {
            jedis = pool.getResource();
            if(jedis != null){
                jedis.select(TASK_INDEX);
                jedis.set(patternKey, "");
                jedis.expire(patternKey,1 * 60 );
            }else{
                log.error("门店{}addIotTasks获取jedis失败" ,storeId);
            }
        }catch(Exception e) {
            if(jedis != null) {
                jedis.close();
//                pool.returnBrokenResource(jedis);
            }
            log.error("门店{}获取redis出现问题 {}" ,storeId, e);
        }finally {
            if(jedis != null) {
                jedis.close();
//                pool.returnResource(jedis);
            }
        }

    }


    /**
     * 获取有订单的门店
     * @return
     */
    public static Set<String> getStoreIdWithOrders(){

        Jedis jedis = null;
        Set<String> keys = null;
        try {
            jedis =  pool.getResource();
            if(jedis != null) {
                jedis.select(ORDER_NOTIFY_INDEX);
                String patternKeyOrder = "ORDERS:*";
                keys = jedis.keys(patternKeyOrder);
            }else{
                log.error("查询门店订单状态获取jedis失败");
            }
        }catch(Exception e) {
            if(jedis != null) {
                jedis.close();
                jedis = null;
//                pool.returnBrokenResource(jedis);
            }
            log.error("查询门店订单状态获取redis出现问题 {}" , e);
        }finally {
            if(jedis != null) {
                jedis.close();
//                pool.returnResource(jedis);
            }
        }

        return keys;
    }












    // -----------> 音频 Redis Key 规范
    // 格式 -> <模块名>:<商户编号>:<门店编号>:<音频ID>:<坐席编号>:<任务主键ID>:<状态>
    // 注解 -> <状态> 0 待播放 1 播放中  2 已播放  -1 过期音频不予播放
    // 备注 -> 只保留状态为 0、1 的数据，不是则删除 Redis 队列
    // 示例 -> V-TASK:10050:10092:192:8:12049:0

    public static Set<String> getStoreVoiceTask(){

        Jedis jedis = null;
        Set<String> keys = null;
        try {
            jedis =  pool.getResource();
            if(jedis != null) {
                jedis.select(VOICE_TASK_INDEX);
                String patternKeyOrder = "V-TASK:*";
                keys = jedis.keys(patternKeyOrder);
            }else{
                log.error("查询门店音频任务获取jedis失败");
            }
        }catch(Exception e) {
            if(jedis != null) {
                jedis.close();
                jedis = null;
//                pool.returnBrokenResource(jedis);
            }
            log.error("查询门店音频任务获取redis出现问题 {}" , e);
        }finally {
            if(jedis != null) {
                jedis.close();
//                pool.returnResource(jedis);
            }
        }

        return keys;
    }


    /**
     * 删除key
     * @param key
     */
    public static void delVoiceKey( String key){
        Jedis jedis = null;
        try {
            jedis = pool.getResource();
            if(jedis != null){
                jedis.select(VOICE_TASK_INDEX);
                jedis.del(key);
            }else{
                log.error("删除音频任务key 失败" );
            }

        }catch(Exception e) {
            if(jedis != null) {
                jedis.close();
//                pool.returnBrokenResource(jedis);
            }
            log.error("音频任务获取redis出现问题 {}" , e);
        }finally {
            if(jedis != null) {
                jedis.close();
//                pool.returnResource(jedis);
            }
        }

    }

    /**
     * 获取值
     * @param key
     */
    public static String getVoiceValue( String key){
        Jedis jedis = null;
        String value = null;
        try {
            jedis = pool.getResource();
            if(jedis != null){
                jedis.select(VOICE_TASK_INDEX);
                value = jedis.get(key);
            }else{
                log.error("删除音频任务key 失败" );
            }

        }catch(Exception e) {
            if(jedis != null) {
                jedis.close();
//                pool.returnBrokenResource(jedis);
            }
            log.error("音频任务获取redis出现问题 {}" , e);
        }finally {
            if(jedis != null) {
                jedis.close();
//                pool.returnResource(jedis);
            }
        }

        return value;
    }
}

