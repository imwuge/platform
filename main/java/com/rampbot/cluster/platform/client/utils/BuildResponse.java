package com.rampbot.cluster.platform.client.utils;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mysql.jdbc.StringUtils;
import com.rampbot.cluster.platform.domain.Task;
import lombok.extern.slf4j.Slf4j;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class BuildResponse {





    /**
     * 构建响应客户端消息体
     *
     * @param code
     * @param msg
     * @param task
     * @return
     */
    public static String buildResponseMsgTask(Integer code, String msg, Task task) {
        // 兼容处理，
        long time = System.currentTimeMillis();
        Integer intTime = Utils.convertToInt((time + "").substring(4), -1);

        Map<String, Object> taskMap = task.getTask();
        Map<String, Object> eventMap = new HashMap<>();

        // TODO: 2022/10/29 下载播放语音额外处理
        eventMap.put("event", taskMap.get("action_type"));
        eventMap.put("task_id", taskMap.get("id"));


        List<Map> iotTaskList = new ArrayList<>();
        iotTaskList.add(eventMap);

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("code", Math.abs(code));
        jsonObject.put("msg", msg);
        jsonObject.put("data", iotTaskList);
//        jsonObject.put("data", taskMap);
        jsonObject.put("time", intTime);

        return JSON.toJSONString(jsonObject);
    }

    /**
     * 构建响应客户端消息体
     *
     * @param code
     * @param msg
     * @param contents
     * @return
     */
    public static String buildResponseMsgError(Integer code, String msg, String contents) {
        // 兼容处理，
        long time = System.currentTimeMillis();
        Integer intTime = Utils.convertToInt((time + "").substring(4), -1);

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("code", Math.abs(code));
        jsonObject.put("msg", msg);
        jsonObject.put("data", contents);
        jsonObject.put("time", intTime);
        return JSON.toJSONString(jsonObject);
    }


    /**
     * 构建响应客户端消息体
     *
     * @param code
     * @param msg
     * @param contents
     * @return
     */
    public static String buildResponseMsg( Integer code, String msg, Object contents) {
        long time = System.currentTimeMillis();
//        String key = SQLHelper.getPrivateKeyByStoreId(storeId);
//        String sign = md5(time + key + md5(contents));
//        if (StringUtils.isNullOrEmpty(key)) {
//            log.error("Get Private Key ERROR. storeId: " + storeId);
//            sign = "";
//        }
        if (contents == null || contents.toString().length() < 1) {
            contents = "[]";
        }

        // 兼容处理，
        Integer intTime = Utils.convertToInt((time + "").substring(4), -1);

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("code", code);
        jsonObject.put("msg", msg);
        jsonObject.put("data", contents);
        jsonObject.put("time", intTime);
//        jsonObject.put("sign", sign);
//        log.info("查看回复消息 {}", JSON.toJSONString(jsonObject));
        return JSON.toJSONString(jsonObject);
    }

    /**
     * 构建签名
     *
     * @param time
     * @param storeId
     * @param contents
     * @return
     */
    public static String buildSign(Long time, Integer storeId, String contents, String key) {

        if (StringUtils.isNullOrEmpty(key)) {
            log.error("Get Private Key ERROR. storeId: " + storeId);
            return null;
        }

        if (!StringUtils.isNullOrEmpty(contents)) {
            return md5(time + "" + key + md5(contents));
        } else {
            return md5(time + "" + key);
        }
    }

    /**
     * MD5
     *
     * @param value
     * @return
     */
    public static String md5(String value) {
        String result = "";
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(value.getBytes());
            byte b[] = md.digest();
            int i;
            StringBuffer buf = new StringBuffer("");
            for (int offset = 0; offset < b.length; offset++) {
                i = b[offset];
                if (i < 0)
                    i += 256;
                if (i < 16)
                    buf.append("0");
                buf.append(Integer.toHexString(i));
            }
            result = buf.toString().toUpperCase();    // 32位的加密
        } catch (NoSuchAlgorithmException e) {
        }
        return result.toLowerCase();
    }


}
