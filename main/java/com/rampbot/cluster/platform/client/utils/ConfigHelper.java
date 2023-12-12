package com.rampbot.cluster.platform.client.utils;


import afu.org.checkerframework.checker.oigj.qual.O;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ConfigHelper {

    @Getter
    public static Map<String, Map<String, Object>> store2Configs = new HashMap();  // 记录每个门店的配置

    private  static Map<String, Object> defultConfig = new HashMap<>();


    static {
        defultConfig.put("interval_milliseconds", 10 * 1000);
    }


    public static void setStoreConfig(String storeKey,  Map<String, Object> configs){
        if(store2Configs.containsKey(storeKey)){
            Map<String, Object> configRember = store2Configs.get(storeKey);
            for (String configKey : configs.keySet()){
                configRember.put(configKey, configs.get(configKey));
            }
        }else {
            store2Configs.put(storeKey, ConfigHelper.setDefultConfig());
            Map<String, Object> configRember = store2Configs.get(storeKey);
            for (String configKey : configs.keySet()){
                configRember.put(configKey, configs.get(configKey));
            }
            //store2Configs.put(storeKey, configs);
        }
    }

    public static Object getStoreConfig(String storeKey,  String configKey){
        if(store2Configs.containsKey(storeKey)){
            Map<String, Object> configs = store2Configs.get(storeKey);
            if(configs.containsKey(configKey)){
                return configs.get(configKey);
            }else {
                log.info("门店{}需要的配置{}不存在", storeKey, configKey);
                return null;
            }
        }else {
            log.info("门店{}获取配置失败{}，使用默认配置", storeKey, configKey);
            return getDefultConfig(configKey);
        }
    }


    public static void setStoreConfig(Integer companyId,  Integer storeId, String configKey, Object configValue){
        Map<String, Object> config = new HashMap<>();
        config.put(configKey, configValue);
        ConfigHelper.setStoreConfig(Utils.getKeyFromInteger(companyId,storeId), config);
    }


    /**
     * 设置所有配置的初始值
     * @return
     */
    private static Map<String, Object> setDefultConfig(){
        Map<String, Object> defultConfigs = new HashMap<>();
        for(String key : defultConfig.keySet()){
            defultConfigs.put(key, defultConfig.get(key));
        }
        return defultConfigs;
    }

    private static Object getDefultConfig(String key){
       return defultConfig.get(key);
    }
}
