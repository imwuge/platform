package com.rampbot.cluster.platform.client.utils;


import com.rampbot.cluster.platform.domain.Notify;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.io.InputStream;
import java.time.Duration;
import java.util.Properties;
import java.util.Set;
import com.alibaba.fastjson.JSON;

@Slf4j
public class RedisHelper {

//    本地测试服务端
    public static final String REDIS_HOST = "60.6.202.67";
//    本地测试
//    public static final String REDIS_HOST = "localhost";
//    服务端
//    public static final String REDIS_HOST = "192.168.0.52";
//    端口
    public static final Integer REDIS_PORT = 9379;

    public static final String REDIS_PWD = "G84Kd_n3L51Gh*n2D{v";
    // redis 数据库索引
    //    索引：0  是音频对讲使用的内存
    //    索引：1 支付信息存储的内存
    //    索引：2 通知、订单的内存
    public static Integer REDIS_INDEX = 2;
//    private static final Jedis REDIS_MUS = new Jedis(REDIS_HOST, REDIS_PORT);
    // 存在时间
    public static final Integer OFHOURS = 1 * 60 * 60;

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
            config.setMaxTotal(1000);//最大提供的连接数
            config.setMaxIdle(100);//最大空闲连接数(即初始化提供了100有效的连接数)
            config.setMinIdle(10);//最小保证的提供的（空闲）连接数

            //创建Jedis连接池
            pool = new JedisPool(config,REDIS_HOST,REDIS_PORT);
//            REDIS_MUS.auth(REDIS_PWD);
//            REDIS_MUS.select(REDIS_INDEX);

        }catch (Exception e){
            e.printStackTrace();
        }
    }




    // ********** 读取
    // Redis.Key = <模块名>:<商户编号>:<门店编号>:<订单类型>:<客服编号>:<主键ID|服务单号>
    // 模块名：NOTIFY、ORDERS、TASK
    // <模块名>:<商户编号>:<门店编号>:<类型>:<客服编号>:<主键ID|服务单号>
    // 订单 ORDERS:10050:10092:1:-1:123013102992012       // 类型：0 购物，1 补签，2 安防，3 店内求助，4 故障，5 巡店
    // 通知 NOTIFY:10050:10092:1:-1:123013102992012       // 类型：1 离店通知，7 托管消息，8 断电，9 失联
    // 任务 TASK:10050:10092:901:18:12049                 // 类型：901 全开门 902 全关门，903 进店开门 904 进店关门，905 离店开门 906 离店关门
    public static Integer getHelperId( int companyId, int storeId){


        Integer heaperIdRead = null;
        Integer orderTypeRead = null;
//        REDIS_MUS.auth(REDIS_PWD);
//        REDIS_MUS.select(REDIS_INDEX);
        // 查询订单表中的客服id
        String patternKeyOrder = String.format("ORDERS:%d:%d:*", companyId, storeId);
        Jedis jedis = pool.getResource();
        jedis.auth(REDIS_PWD);
        jedis.select(REDIS_INDEX);
        Set<String> keys = jedis.keys(patternKeyOrder);
        if (keys != null && keys.size() >= 1) {
            for (String key : keys) {
                String[] keyArray = key.split("\\:");
                if (keyArray.length >= 6) {
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

        // 查询通知表中的客服id
//        if(heaperIdRead == null || heaperIdRead == -1){
//            String patternKeyNotify = String.format("NOTIFY:%d:%d:*",  companyId, storeId);
//            Set<String> keysNotify = REDIS_MUS.keys(patternKeyNotify);
//            if (keysNotify != null && keysNotify.size() >= 1) {
//                for (String key : keysNotify) {
//                    String[] keyArray = key.split("\\:");
//                    if (keyArray.length >= 6) {
//                        heaperIdRead = Utils.convertToInt(keyArray[4], -1);
//                        if(heaperIdRead != -1){
//                            break;
//                        }
//                    }
//                }
//            }
//        }


        jedis.close();
        /**
         * heaperIdRead 有三种结果：
         *      1 没有订单为null
         *      2 有订单未分配为 -1
         *      3 已分配结果
         */
        return heaperIdRead == null ? -1 : heaperIdRead;
    }

    // ********** 读取
    // Redis.Key = <模块名>:<商户编号>:<门店编号>:<订单类型>:<客服编号>:<主键ID|服务单号>
    // 模块名：NOTIFY、ORDERS、TASK
    // <模块名>:<商户编号>:<门店编号>:<类型>:<客服编号>:<主键ID|服务单号>
    // 订单 ORDERS:10050:10092:1:-1:123013102992012       // 类型：0 购物，1 补签，2 安防，3 店内求助，4 故障，5 巡店
    // 通知 NOTIFY:10050:10092:1:-1:123013102992012       // 类型：1 离店通知，7 托管消息，8 断电，9 失联
    // 任务 TASK:10050:10092:901:18:12049                 // 类型：901 全开门 902 全关门，903 进店开门 904 进店关门，905 离店开门 906 离店关门
    public static boolean isExistsOrder(int companyId, int storeId, String type){
//        REDIS_MUS.auth(REDIS_PWD);
//        REDIS_MUS.select(REDIS_INDEX);
        String moduleName = Utils.getModuleName(type);
        int typeNumber = Utils.getTypeNum(type);
        String patternKey = String.format("%s:%d:%d:%d:*", moduleName, companyId, storeId, typeNumber);
//        Set<String> keys = REDIS_MUS.keys(patternKey);
        Set<String> keys = null;
        try {
            Jedis jedis = pool.getResource();
            jedis.auth(REDIS_PWD);
            jedis.select(REDIS_INDEX);
            keys = jedis.keys(patternKey);
            jedis.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return keys != null && keys.size() >= 1;

    }

    public static boolean isExistsPendingSaftOrder(int companyId, int storeId){
        log.info("查询是否有安防订单被触发");
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
            jedis.select(REDIS_INDEX);
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

    // ********** 读取
    // Redis.Key = <模块名>:<商户编号>:<门店编号>:<订单类型>:<客服编号>:<主键ID|服务单号>
    // 模块名：NOTIFY、ORDERS、TASK
    // <模块名>:<商户编号>:<门店编号>:<类型>:<客服编号>:<主键ID|服务单号>
    // 订单 ORDERS:10050:10092:1:-1:123013102992012       // 类型：0 购物，1 补签，2 安防，3 店内求助，4 故障，5 巡店
    // 通知 NOTIFY:10050:10092:1:-1:123013102992012       // 类型：1 离店通知，7 托管消息，8 断电，9 失联
    // 任务 TASK:10050:10092:901:18:12049                 // 类型：901 全开门 902 全关门，903 进店开门 904 进店关门，905 离店开门 906 离店关门
    public static void writadeRedis(int companyId, int storeId, Long orderNo, String type, String title, String content,  int helperId){
//        String redisKey = String.format("NOTIFY:%d:%s:%d:%d:%d", ERedisNotifyType.MUS_ORDERS.getStatusCode(), companyId, storeId, -1, orderNo);
        String moduleName = Utils.getModuleName(type);
        int typeNumber = Utils.getTypeNum(type);

        String redisKey = String.format("%s:%d:%d:%d:%d:%d", moduleName, companyId, storeId, typeNumber, helperId, orderNo);
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
        // 存放时长 OFHOURS小时，如果不是主动销毁，则OFHOURS小时后自动回收
//        REDIS_MUS.auth(REDIS_PWD);
//        REDIS_MUS.select(REDIS_INDEX);
//        REDIS_MUS.set(redisKey, redisValue, String.valueOf(Duration.ofHours(OFHOURS)));
//        REDIS_MUS.set(redisKey, redisValue);
//        REDIS_MUS.expire(redisKey,OFHOURS);
        try {
            Jedis jedis = pool.getResource();
            jedis.auth(REDIS_PWD);
            jedis.select(REDIS_INDEX);
//            jedis.set(redisKey, redisValue);
//            jedis.expire(redisKey,OFHOURS);
            jedis.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
