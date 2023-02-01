package com.rampbot.cluster.platform.client.utils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

public class Utils {

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
     * Convert To Int32
     *
     * @param value
     * @param defaultValue
     * @return
     */
    public static int convertToInt32(Object value, int defaultValue) {
        try {
            return Integer.parseInt(value + "");
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
    public static String getOrderNoPreFixByType_BAK(String type) {
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
            case "安防":
                return "7";
            case "断电":
                return "11";
            default:
                return "10";
        }
    }

    /**
     * TYPE 8 断电，9 失联
     * 级别 (0 仅在消息盒子中展示，1 需要弹窗提示)
     * @param type
     * @return
     */
    public static int getLevel(String type){
        switch (type.trim()) {
            case "失联":
                return 1;
            case "断电":
                return 1;
            default:
                return 0;
        }
    }

    /**
     * NOTIFY、ORDERS
     *     // 订单 ORDERS:10050:10092:1:-1:123013102992012       // 类型：0 购物，1 补签，2 安防，3 店内求助，4 故障，5 巡店
     *     // 通知 NOTIFY:10050:10092:1:-1:123013102992012       // 类型：1 离店通知，7 托管消息，8 断电，9 失联
     * @param type
     * @return
     */
    public static String getModuleName(String type) {
        switch (type.trim()) {
            case "购物":
                return "ORDERS";
            case "补签":
                return "ORDERS";
            case "店内求助":
            case "店外求助":
                return "ORDERS";
            case "巡店":
                return "ORDERS";

            case "骑手":
                return "NULL";
            case "故障":
                return "ORDERS";
            case "失联":
                return "NOTIFY";
            case "安防":
                return "ORDERS";
            case "断电":
                return "NOTIFY";
            default:
                return "NULL";
        }
    }


    /**
     *     // 订单 ORDERS:10050:10092:1:-1:123013102992012       // 类型：0 购物，1 补签，2 安防，3 店内求助，4 故障，5 巡店
     *     // 通知 NOTIFY:10050:10092:1:-1:123013102992012       // 类型：1 离店通知，7 托管消息，8 断电，9 失联
     * @param type
     * @return
     */
    public static int getTypeNum(String type) {
        switch (type.trim()) {
            case "购物":
                return 0;
            case "补签":
                return 1;
            case "店内求助":
                return 3;
            case "店外求助":
                return -1;
            case "巡店":
                return 5;
            case "骑手":
                return -1;
            case "故障":
                return 4;
            case "失联":
                return 9;
            case "安防":
                return 2;
            case "断电":
                return 8;
            default:
                return -1;
        }
    }

    /**
     * 通过订单类型  获取订单前缀
     * @param type
     * @return
     */
    public static Integer getOrderNoPreFixByType(String type) {
        switch (type) {
            case "购物":  // 购物
                return 1;
            case "补签":  // 补签
                return 2;
            case "安防":  // 安防
                return 7;
            case "店内求助":      // 店内求助
                return 3;
            case "故障":     // 故障
                return 4;
            case "巡店":     // 巡店
                return 5;
            default: // 未知
                return 9;
        }
    }

    /**
     * 构建订单编号
     * @param type
     * @return
     */
//    public static Long buildMusOrderNo_BAK(String type) {
//        Integer rand = new Random().nextInt(900) + 100;
//        String prefix = getOrderNoPreFixByType(type);
//        return Utils.convertToLong(prefix + Utils.getTimeSpace("yyMMddHHmmss") + rand, -1);
//    }

    /**
     * 构建订单编号
     * @param type
     * @return
     */
    public static Long buildMusOrderNo(Integer companyId, Integer storeId, String type) {
        Integer prefix = getOrderNoPreFixByType(type);
        String time = Utils.getTimeSpace("yyMMddHHmmss");

        Integer rand = new Random().nextInt(99999) + 10000;
        Integer mill = Utils.convertToInt32(Utils.getTimeSpace("SSSss"), rand);
        Integer num = companyId + storeId + mill;

        String guid = String.format("%d%s%d", prefix, time, num);
        return Utils.convertToLong(guid, -1);
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


    /**
     * mp3读取
     * @param filePath
     * @return
     */
    public static byte[] getContent(String filePath) {
        File file = new File(filePath);
        long fileSize = file.length();
        if (fileSize > Integer.MAX_VALUE) {
            System.out.println("file too big...");
            return null;
        }
        FileInputStream fi = null;
        try {
            fi = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        byte[] buffer = new byte[(int) fileSize];
        int offset = 0;
        int numRead = 0;
        while (true) {
            try {
                if (!(offset < buffer.length
                                && (numRead = fi.read(buffer, offset, buffer.length - offset)) >= 0)) break;
            } catch (IOException e) {
                e.printStackTrace();
            }
            offset += numRead;
        }
        // 确保所有数据均被读取
        if (offset != buffer.length) {
            try {
                throw new IOException("Could not completely read file "
                        + file.getName());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            fi.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return buffer;
    }


}
