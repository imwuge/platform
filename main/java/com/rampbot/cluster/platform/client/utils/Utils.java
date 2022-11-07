package com.rampbot.cluster.platform.client.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

public class Utils {

    /**
     * 记录门店门锁不一致时间
     * Key=门店Id、Value=故障时间
     */
    public static Integer _CONFIG_DOOR_CLOSE_TIME_MILLISECOND = 30000; // 30 秒
    public static HashMap<Integer, Long> _STORE_DOOR_STATUS_TIME_OUT = new HashMap<>();
    public static HashMap<Integer, Long> _STORE_DOOR_STATUS_TIME_IN = new HashMap<>();

    /**
     * convert int
     *
     * @param value
     * @param defaultValue
     * @return
     */
    public static Integer convertToInt(Object value, Integer defaultValue) {
        try {
            return Integer.parseInt(value + "");
        } catch (Exception e) {
        }

        return defaultValue;
    }

    /**
     * Convert To Int32
     *
     * @param value
     * @param defaultValue
     * @return
     */
    public static Long convertToLong(Object value, long defaultValue) {
        try {
            return Long.parseLong((value + ""));
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    /**
     * Get Now Date Format "yyyyMMddHHmmss"
     *
     * @return
     */
    public static String getTimeSpace(String format) {
        SimpleDateFormat df = new SimpleDateFormat(format);
        return df.format(new Date());
    }

    /**
     * Get Now Date Format "yyyyMMddHHmmss"
     *
     * @return
     */
    public static String getTimeSpace() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return df.format(new Date());
    }

    /**
     * Get Now Date Format "yyyyMMddHHmmss"
     *
     * @return long
     */
    public static long getTimeSpaceSeconds() {
        return new Date().getTime();
//        SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
//        return Long.parseLong(df.format(new Date()));
    }

    /**
     * Java将Unix时间戳转换成指定格式日期字符串
     * @param timestamp 时间戳 如
     * @param formats 要格式化的格式 默认："yyyy-MM-dd HH:mm:ss";
     *
     * @return 返回结果 如："2016-09-05 16:06:42";
     */
    public static String formatTimeSpace(long timestamp, String formats) {
        try {
            String date = new SimpleDateFormat(formats, Locale.CHINA).format(new Date(timestamp));
            return date;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Java将Unix时间戳转换成指定格式日期字符串
     * @param timestamp 时间戳 如
     *
     * @return 返回结果 如："2016-09-05 16:06:42";
     */
    public static String formatTimeSpace(long timestamp) {
        try {
            String formats = "yyyy-MM-dd HH:mm:ss";
            String date = new SimpleDateFormat(formats, Locale.CHINA).format(new Date(timestamp));
            return date;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 获取今天星期几
     * @return
     */
    public static String getWeek(){
        String[] weeks = {"星期日","星期一","星期二","星期三","星期四","星期五","星期六"};
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        int week_index = cal.get(Calendar.DAY_OF_WEEK) - 1;
        if(week_index<0){
            week_index = 0;
        }
        return weeks[week_index];
    }

    /**
     * Int 转 byte[]
     *
     * @param value
     * @return
     */
    public static byte[] intToByteArray(int value) {
        try {
            byte[] byteArray = new byte[4];
            byteArray[0] = (byte) (value & 0xFF);
            byteArray[1] = (byte) (value >> 8 & 0xFF);
            byteArray[2] = (byte) (value >> 16 & 0xFF);
            byteArray[3] = (byte) (value >> 24 & 0xFF);
            return byteArray;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 把byte转为字符串的bit
     *
     * @param b
     * @return
     */
    public static String byteToBitString(byte b) {
        return ""
                + (byte) ((b >> 7) & 0x1) + (byte) ((b >> 6) & 0x1)
                + (byte) ((b >> 5) & 0x1) + (byte) ((b >> 4) & 0x1)
                + (byte) ((b >> 3) & 0x1) + (byte) ((b >> 2) & 0x1)
                + (byte) ((b >> 1) & 0x1) + (byte) ((b >> 0) & 0x1);
    }

    /**
     * 写日志
     *
     * @param contents
     */
    public static void writeDebugLog(String contents) {
        System.out.println("[" + Utils.getTimeSpace() + "] [DEBUG] " + contents);
    }

    /**
     * 写日志
     *
     * @param contents
     */
    public static void writeERRORLog(String contents) {
        System.out.println("[" + Utils.getTimeSpace() + "] 【ERROR】 " + contents);
    }

    /**
     * 下载语音
     *
     * @param downloadUrl
     * @return
     */
    public static byte[] downloadVoiceFiles(String downloadUrl) throws IOException {
        URL url = new URL(downloadUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        //设置超时间为3秒
        conn.setConnectTimeout(3 * 1000);
        //防止屏蔽程序抓取而返回403错误
        conn.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");

        //得到输入流
        InputStream inputStream = conn.getInputStream();
        byte[] buffer = new byte[1024];
        int len = 0;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        while ((len = inputStream.read(buffer)) != -1) {
            bos.write(buffer, 0, len);
        }
        bos.close();
        byte[] getData = bos.toByteArray();

        if (inputStream != null) {
            inputStream.close();
        }

        return getData;
    }

    /**
     * 通过订单类型  获取订单前缀
     * @param type
     * @return
     */
    public static String getOrderNoPreFixByType(String type) {
        /**
         * 失联
         * 店内求助
         * 店外求助
         * 故障
         * 补签
         * 购物
         * 骑手
         * 巡店
         * 安防检查
         */
        switch (type.trim()) {
            case "购物":
                return "1";
            case "补签":
                return "2";
            case "店内求助":
            case "店外求助":
                return "3";
            case "巡店":
                return "4";

            case "骑手":
                return "6";
            case "故障":
                return "8";
            case "失联":
                return "9";
            default:
                return "7";
        }
    }

    /**
     * 构建订单编号
     * @param type
     * @return
     */
    public static Long buildMusOrderNo(String type) {
        Integer rand = new Random().nextInt(900) + 100;
        String prefix = getOrderNoPreFixByType(type);
        return Utils.convertToLong(prefix + Utils.getTimeSpace("yyMMddHHmmss") + rand, -1);
    }

    /**
     * 规范上报心跳
     * @param status
     * @return
     */
    public static String standardStatus(int status){
        String statusStr = Integer.toBinaryString(status);

        StringBuilder sb =new StringBuilder();
        for (int i = 0; i < 16 - statusStr.length(); i++) {
            sb.append("0");
        }

        return sb + statusStr;
    }




}
