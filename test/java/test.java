import afu.org.checkerframework.checker.igj.qual.I;
import com.alibaba.druid.pool.DruidDataSourceFactory;
;
import com.rampbot.cluster.platform.client.utils.*;

import com.rampbot.cluster.platform.domain.LockGetPlayVoiceTimeout;
import com.rampbot.cluster.platform.domain.Task;
import com.rampbot.cluster.platform.domain.TaskStatus;
import com.sun.media.jfxmedia.track.Track;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import scala.concurrent.duration.FiniteDuration;

import javax.sql.DataSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLOutput;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
//import com.sun.jna.Library;
//import com.sun.jna.Native;
@Slf4j
public class test {
    public static void main(String[] args) throws Exception {

        int[] test = new int[2];



        try {
            for(int i = 0; i<4; i++){
                System.out.println(test[i]);
            }
        }catch (Exception e){
            e.printStackTrace();
           log.info("输出错误信息 {}", e.getMessage());
        }

        System.out.println("结束");

//        ConfigHelper.getStore2Configs();
//
//
//        Map<String, Map<String, Object>> store2Configs = new HashMap();  // 记录每个门店的配置
//        String storeKey = "110_112";
//
//        Map<String, Object> testOld = new HashMap();
//        Map<String, Object> configs= new HashMap();
//        testOld.put("test", 1);
//        testOld.put("test3", 3);
//        configs.put("test", 2);
//        configs.put("test2", 3);
//
//        ConfigHelper.setStoreConfig("1", testOld);
//        log.info("{}", ConfigHelper.getStore2Configs());
//        ConfigHelper.setStoreConfig("2", configs);
//        log.info("{}", ConfigHelper.getStore2Configs());
//        store2Configs.put("110_112", testOld);
//        log.info("处理前 {}", store2Configs);
//
//        if(store2Configs.containsKey(storeKey)){
//            Map<String, Object> configRember = store2Configs.get(storeKey);
//            for (String configKey : configs.keySet()){
//                configRember.put(configKey, configs.get(configKey));
//            }
//        }else {
//            store2Configs.put(storeKey, configs);
//        }
//
//        log.info("处理后 {}", store2Configs);
//        // {"log":"2023-11-04 09:01:46,581 INFO  c.r.c.p.n.t.controller.TcpController   - 收到错误数据数量超过500，清空数据 {\"action\":\"100\",\"sid\":\"2202270710235\",\"time\":59697548,\"maxVoiceVersion\":264,\"firmwareVersion\":1,\"status\":128,\"sign\":\"D02EB2AA279BA7FFCECF2D622020DE65\"}\r\n","stream":"stdout","time":"2023-11-04T01:01:46.582129089Z"}
//        String test = "\"log\":\"2023-11-04 09:01:46,581 INFO  c.r.c.p.n.t.controller.TcpController   - 收到错误数据数量超过500，清空数据 {\\\"action\\\":\\\"100\\\",\\\"sid\\\":\\\"2202270710235\\\",\\\"time\\\":59697548,\\\"maxVoiceVersion\\\":264,\\\"firmwareVersion\\\":1,\\\"status\\\":128,\\\"sign\\\":\\\"D02EB2AA279BA7FFCECF2D622020DE65\\\"}\\r\\n\",\"stream\":\"stdout\",\"time\":\"2023-11-04T01:01:46.582129089Z\"}\n";
//        String[] resultArry = test.split("");
//
//        boolean isHasStart = false;
//        boolean isHasEnd = false;
//        int startIndex = 0;
//        int endIndex = 1;
//        //if(resultArry.length < 3){return false;}
//        for(int i = 0; i < resultArry.length; i++){
//            if(resultArry[i].equals("{")){
//                isHasStart = true;
//                startIndex = i;
//                break;
//            }
//        }
//        if(isHasStart){
//            for(int i = startIndex+1; i < resultArry.length; i++){
//                if(resultArry[i].equals("}")){
//
//                    isHasEnd = true;
//                    endIndex = i;
//                    break;
//                }
//            }
//        }
//        String data = "";
//        for(int i = startIndex; i <= endIndex; i++){
//            data = data + resultArry[i];
//        }
//
//        data = data + "\r\n";
//        System.out.println(data);

//        DBHelper.updateVoiceTask(1,1,1,1,1,"测试");
        //DBHelper.getPendingVoiceTask();
//        byte[] voiceData = new byte[1024];
//        voiceData[0] = 127;
//        byte[] voiceDownloadData = new byte[voiceData.length + 6];
//
//        voiceDownloadData[0] = (byte)0xaa;
//        voiceDownloadData[1] = (byte)0xbb;
//        voiceDownloadData[voiceDownloadData.length-2] = (byte)0xbb;
//        voiceDownloadData[voiceDownloadData.length-1] = (byte)0xaa;
//
//
//        byte[] length = Utils.intToBytes(voiceDownloadData.length);
//        voiceDownloadData[2] = length[0];
//        voiceDownloadData[3] = length[1];
//
//        //数据
//        for(int i = 0; i< voiceData.length; i++){
//            voiceDownloadData[i + 4] = voiceData[i];
//        }
//
//        log.info("{}", voiceDownloadData);
//
//        System.out.println(Integer.toHexString(1030));
       // DBHelper.addWorkStatusLog(this.companyId, this.storeId, this.storeName, this.equipmentId, 1);


        /**
         * 将int数值转换为占四个字节的byte数组，本方法适用于(高位在前，低位在后)的顺序。  和bytesToInt2（）配套使用
         */





//        Map<String, Object> configs = DBHelper.getUpdateStoreconfigs(10042, 10050, false);
//
//        log.info("{}", configs);
//
//
//        int  a = Utils.convertToInt(configs.get("body_sensor_data_collection_time"), 6000);
//        System.out.println(a);

//        DBHelper.updateSensor(1,1,"室内求助", 1);
//        String a = "Actor[akka://ClusterSystem/user/server/ClientController.2202270710411.1685001027783#2129580891]\\n";
//
//        log.info("{}   {}",a.split("\\.")[1]);
        // int companyId, int storeId, Long orderNo, String type, String title, String content, int helperId, int statusOrLevel
        //RedisHelper.writeOrderOrNotify(10050,10011, (long)123, "购物", "", "", -1 , 0);
        // int companyId, Integer storeId, int type, long id, int stats)
//        RedisHelper.addIotTasks(10050,10628,903, 111837, 0);


        //System.loadLibrary("C:\\Users\\work\\Desktop\\郑州安防对接\\资料\\Lib\\AlarmSDK");

        //System.out.println(DBHelper.insertStoreLogsId(10, 2,2, "更新测试44445555", "server"));
       // System.out.println("8d7ffd8b5f374a80ad26431b4b0bf220".length());
        //getConfigFile(10221,10050);

//        String fileUrl = "http://platform.honglinghb.com:9003/resources/upload/voice/72bb05cbf99142c98eb993ebc24f3247.mp3";
//        byte[] serverVoiceData = null;
//        byte[] clientVoiceData = null;
////        public static  void printMoney(int year, int month, int firstMoney){
//
//        //printMoney(3, 0, 10 - 1.4);
//        Map<String, Integer> test = new HashMap<>();
//        test.put("a", 11);
//        System.out.println(Utils.convertToInt(test.get("b"), 0));
////
//        serverVoiceData = Utils.downloadVoiceFiles(fileUrl);
//        System.out.println(serverVoiceData.length);
//        System.out.println(serverVoiceData.length%4096);
//        System.out.println(serverVoiceData.length%1024);
//        System.out.println(serverVoiceData.length/512);
//
//
//
//
//        clientVoiceData = Utils.getContent("C:\\Users\\work\\Desktop\\03.mp3");
//        System.out.println(clientVoiceData.length);
//        System.out.println(clientVoiceData.length%4096);
//        System.out.println(clientVoiceData.length%1024);
//        System.out.println(clientVoiceData.length/512);
////        System.out.println("adfadevent".contains("event"));
//
//        log.info("服务端数据：{}", serverVoiceData);
//        BytePrintAsString(serverVoiceData);
//        log.info("客户端数据：{}", clientVoiceData);
//        BytePrintAsString(clientVoiceData);

//        getSerious(452, 11, "2202270710",  "电磁锁 继电器 8 0402");
//        public static void writeOrderOrNotify(int companyId, int storeId, Long orderNo, String type, String title, String content, int helperId, int statusOrLevel){
//        List<Map<String, Object>> pendingPlayVoiceTask = DBHelper.getPlayVoiceTask(10347, 10050);
//        getPlayVoiceTask(pendingPlayVoiceTask);
//        log.info("查看获取结果 {} ", pendingPlayVoiceTask);
           // RedisHelper.writeOrderOrNotify(10050,10011, (long)123, "购物", "", "", -1 , 0);
        //System.out.println(RedisHelper.isExistsOrderCannotCloseInLight( 10050,10011));

//        String[] currentTime;
//
//        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy MM dd HH mm ss EEEE");
//        currentTime = dateFormat.format(new Date()).split(" ");
//        currentTime[6] = Utils.getWeek();
//
//        for(String i : currentTime){
//            System.out.println(i);
//        }
//        System.out.println(currentTime);
//        byte[] test = new byte[]{0x42};
//        System.out.println( new String(test));
//
//        byte[] testa = "AT\r\n".getBytes(StandardCharsets.UTF_8);
//        for(byte i : testa){
//            System.out.println(Integer.toHexString(i));
//        }


        //DBHelper.addNotifyV2(10011,10050, "失联", "测试店");

      //  DBHelper.getStoreIdAndCompanyIdBySerialNumber("2202270710005");

        // int storeId, int actionType, int status, int companyId

//        log.info("{}", DBHelper.getPlayVoiceTask(10118, 10050));

//        System.out.println();

        //System.out.println(Integer.parseInt("1110000110".substring("1110000110".length()-7, "1110000110".length()-6)));


//        Map<String, Object> voiceMsg = DBHelper.getVoice(1, 10050);
//        if(voiceMsg != null && voiceMsg.size() > 0){
//            int version = Utils.convertToInt(voiceMsg.get("version"), -1);
//            String fileUrl = voiceMsg.get("file_url").toString();
//            //voiceData = Utils.getContent("C:\\Users\\work\\Desktop\\V6.0\\voice\\sd voice\\01.mp3");
//            try {
//                byte[] server = Utils.downloadVoiceFiles(fileUrl);
//                log.info("server {}  {}  {}  {}  {}" ,server.length , server.length/512, server.length%512, server.length/4096, server.length%4096);
//
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
////
////        byte[] server = Utils.getContent("C:\\Users\\work\\Desktop\\92.mp3");
////        byte[] client = Utils.getContent("C:\\Users\\work\\Desktop\\91.mp3");
//        //byte[] server = Utils.getContent("C:\\Users\\work\\Desktop\\1.mp3");
//        byte[] client = Utils.getContent("C:\\Users\\work\\Desktop\\新建文件夹\\01.mp3");
//       // log.info("server {}  {}  {}  {}  {}" ,server.length , server.length/512, server.length%512, server.length/4096, server.length%4096);
//        log.info("client {}  {}  {}  {}  {}" ,client.length , client.length/512, client.length%512, client.length/4096, client.length%4096 );
//        //System.out.println("长度差为：" + (client.length - server.length));
//
////        log.info("server: {}" ,server );
////        log.info("client: {}" ,client );
////        for(int i = server.length ; i < client.length; i++){
////            //log.info("client 的 {} 位是 {}" ,i, client[i] );
////
//////            if(server[i] != client[i]){
////                log.info("client 的 {} 位是 {}" ,i, client[i] );
////                log.info("server 的 {} 位是 {}" ,i, server[i] );
////            }
//        }
//
//        log.info("90 {} ", voiceData);
//
//
//
//        try {
//
//            // 创建文件
//
//            File file = new File("C:\\Users\\work\\Desktop\\91test.mp3");
//
//            // 如果文件不存在,则创建新的文件
//
//            if(!file.exists()) {
//
//                file.createNewFile();
//
//                System.out.println("创建文件成功！");
//
//            } else {
//
//                System.out.println("文件已存在！");
//
//            }
//
//
//            FileOutputStream out = null;
//            out =  new FileOutputStream(file);;
//            out.write(voiceData );
//            out.flush();
//            out.close();
//
//        } catch (Exception e) {
//
//            e.printStackTrace();
//
//        }

    }
    public static  byte[] intToBytes(int value){
        byte[] src = new byte[2];
//        src[0] = (byte) ((value >> 24) & 0xFF);
//        src[1] = (byte) ((value >> 16) & 0xFF);
        src[0] = (byte) ((value >> 8) & 0xFF);
        src[1] = (byte) (value & 0xFF);
        return src;
    }
    public static byte[] intToBytes2(int value) {
        byte[] src = new byte[4];
        src[3] = (byte) ((value >> 24) & 0xFF);
        src[2] = (byte) ((value >> 16) & 0xFF);
        src[1] = (byte) ((value >> 8) & 0xFF);
        src[0] = (byte) (value & 0xFF);
        return src;
    }


    /**
     * 生成config文件
     * @param storeId
     * @param companyId
     */
    public static void getConfigFile(Integer storeId, int companyId) {


        String sql1 = "select " +
                "serial_number," +
                "private_key," +
                "open_seconds_welcome_second," +
                "interval_milliseconds," +
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
                "disconnected_play_time," +
                "wait_time," +
                "in_volume," +
                "out_volume," +
                "is_enable_in_light_control," +
                "is_enable_out_light_control," +
                "relay_control_stats," +
                "restart_stm" +
                " from stores_stm_config  WHERE store_id = " + storeId + " AND company_id = " + companyId;
        List<Map<String, Object>> data1 = SQLHelper.executeQueryTable(sql1);
        if (data1 == null || data1.size() > 0) {

            Map<String, Object> date = data1.get(0);

            // 序列号
            String serialNum = date.get("serial_number").toString();
            if(serialNum == null || serialNum.length() != 13){
                System.out.println("序列号获取失败,无法生成config文件");
                return;
            }

            // key
            String key = date.get("private_key").toString();
            if(key == null || key.length() != 32){
                System.out.println("key获取失败,无法生成config文件");
                return;
            }

            // 开关配置
            Integer workMode = Utils.convertToInt(date.get("work_mode"), -1);
            if(workMode == -1){
                System.out.println("工作模式配置获取失败,无法生成config文件");
                return;
            }else{
                if(workMode == 0){
                    workMode = 1;
                }
            }
            Integer singleForClose = Utils.convertToInt(date.get("single_for_close"), -1);
            if(singleForClose == -1){
                System.out.println("门禁信号反馈配置获取失败,无法生成config文件");
                return;
            }
            Integer singletonDoor = Utils.convertToInt(date.get("singleton_door"), -1);
            if(singletonDoor == -1){
                System.out.println("单双门配置获取失败,无法生成config文件");
                return;
            }
            Integer hasOutVoicePlayer = Utils.convertToInt(date.get("has_out_coice_player"), -1);
            if(hasOutVoicePlayer == -1){
                System.out.println("外音响配置获取失败,无法生成config文件");
                return;
            }
            Integer hasInVoicePlayer = Utils.convertToInt(date.get("has_in_coice_player"), -1);
            if(hasInVoicePlayer == -1){
                System.out.println("内音响配置获取失败,无法生成config文件");
                return;
            }
            Integer hasPowerDetection = Utils.convertToInt(date.get("has_power_detection"), -1);
            if(hasPowerDetection == -1){
                System.out.println("断电检测配置获取失败,无法生成config文件");
                return;
            }
            Integer isNonstandardStore = Utils.convertToInt(date.get("is_nonstandard_store"), -1);
            if(isNonstandardStore == -1){
                System.out.println("自动门配置获取失败,无法生成config文件");
                return;
            }
            Integer enablePasswordlock = Utils.convertToInt(date.get("enable_passwordlock"), -1);
            if(enablePasswordlock == -1){
                System.out.println("使能密码锁配置获取失败,无法生成config文件");
                return;
            }
            // 自动门无反馈信号
            if(isNonstandardStore == 1){
                singleForClose = 0;
            }
            // 生成配置开关
            String switchs = workMode.toString() + singleForClose.toString() + singletonDoor.toString() + hasOutVoicePlayer.toString() + hasInVoicePlayer.toString() + hasPowerDetection.toString() + isNonstandardStore.toString() + enablePasswordlock.toString();

            // 失联时间
            Integer disconnectedTime = Utils.convertToInt(date.get("disconnected_time"), -1);
            if(disconnectedTime == -1){
                System.out.println("失联时间配置获取失败,无法生成config文件");
                return;
            }

            // 求助按钮失能时间
            Integer helpSilenceTime = Utils.convertToInt(date.get("help_silence_time"), -1);
            if(helpSilenceTime == -1){
                System.out.println("求助按钮失能配置获取失败,无法生成config文件");
                return;
            }

            // 重启时间
            Integer disconnectedRestartTime = Utils.convertToInt(date.get("disconnected_restart_time"), -1);
            if(disconnectedRestartTime == -1){
                System.out.println("重启次数配置获取失败,无法生成config文件");
                return;
            }

            // 断网播报次数
            Integer disconnectedPlayTime = Utils.convertToInt(date.get("disconnected_play_time"), -1);
            if(disconnectedPlayTime == -1){
                System.out.println("断网播报最大次数配置获取失败,无法生成config文件");
                return;
            }

            // 单个心跳最大阻塞时间
            Integer waitTime = Utils.convertToInt(date.get("wait_time"), -1);
            if(waitTime == -1){
                System.out.println("单个心跳最大阻塞时间配置获取失败,无法生成config文件");
                return;
            }

            // 室外音量
            Integer outVolume = Utils.convertToInt(date.get("out_volume"), -1);
            if(outVolume == -1){
                System.out.println("室外音量配置获取失败,无法生成config文件");
                return;
            }

            // 室内音量
            Integer inVolume = Utils.convertToInt(date.get("in_volume"), -1);
            if(inVolume == -1){
                System.out.println("室内音量配置获取失败,无法生成config文件");
                return;
            }

            // 生成配置字符串
            String connect = " ";
            String config = serialNum + connect + key  + connect +  switchs  + connect +  disconnectedTime  + connect +  helpSilenceTime  + connect +  disconnectedRestartTime  + connect +  disconnectedPlayTime  + connect +  waitTime  + connect +  outVolume  + connect +  inVolume;


            // 生成config文件,这里需要你修改为下载问年间
            String fileName = "config";
            String filePath = "C:\\Users\\work\\Desktop\\" + fileName;
            FileWriter fw = null;
            try
            {
                File file = new File(filePath);
                if (!file.exists())
                {
                    file.createNewFile();
                }
                fw = new FileWriter(filePath);
                fw.write(config);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            finally
            {
                try
                {
                    fw.close();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }

            }
        }else{
            System.out.println("获取配置资源失败,无法生成config文件");
        }
    }



    public static void BytePrintAsString(byte [] byteArray) {
        for (int i = 0; i < byteArray.length; i++) {
            String hex = Integer.toHexString(byteArray[i] & 0xFF);
            if (hex.length() == 1) {
                hex = '0' + hex;
            }
            System.out.print(hex.toUpperCase());
            System.out.print(" ");
        }
        System.out.println();
    }



    public static  void printMoney(int year, int month, double firstMoney){

        double shouciTotal = firstMoney + 1.4;
        System.out.println("首次总付款" + shouciTotal + "万");

        System.out.println("贷款" + year + "年");

        double daikuanTotal = 33 - firstMoney;
        System.out.println("贷款总金额" + daikuanTotal + "万");

        double lixiTotal = daikuanTotal * year * 0.0276;
        String  str222 = String.format("%.2f",lixiTotal);
        double four222 = Double.parseDouble(str222);
        System.out.println("贷款总利息" + four222 + "万");


        double huankuanMonth = (lixiTotal + daikuanTotal) / (12 * year );

        String  str22 = String.format("%.2f",huankuanMonth*10);
        double four22 = Double.parseDouble(str22);
        System.out.println("月还款数" + four22*1000 + "元");

        System.out.println("月可支配金额" + (21000 - four22*1000));

        System.out.println("到" + (2023 + year) + "年6月,还清贷款");

        System.out.println("------------------");

    }


    public static void getSerious(int start, int length, String baseName, String conment){
        for(int i = start; i <  start + length; i++){
            System.out.println("// " + i + " " + conment);

            System.out.println("// " +  baseName + i);
            UUID uuid = UUID.randomUUID();
            System.out.println("// " + subStringToString(uuid).substring(0, 29) + i);

            System.out.println(" ");
        }
    }


    private static String subStringToString(UUID uuid) {

        String src = uuid.toString();

        return src.substring(0, 8) + src.substring(9, 13) + src.substring(14, 18) + src.substring(19, 23) + src.substring(24, 36);

    }


    public static void testJedis(){
        //1、获取连接
        Jedis jedis = new Jedis("localhost",6379);
        //有密码的话再加一句下面这个(123456是我的密码)
        jedis.auth("123456");

        //2、执行具体操作
        jedis.set("username","likangkang");

        //3、关闭连接
        jedis.close();
    }

    public static boolean isFullResult(String result){
        String[] resultArry = result.split("");
        log.info("客户端{}分解后的数量为{}"," ", resultArry.length);
        return resultArry[0].equals("{") && resultArry[resultArry.length - 3].equals("}");
    }


    public static boolean tests(int big){
        for (int i = 0; i <=big; i++){

            if(i == 12){
                return true;
            }
        }

        return false;

    }


    public static List<Task> getPlayVoiceTask(List<Map<String, Object>> pendingPlayVoiceTask){

        List<Task> tasks = new LinkedList<>();



        for (Map<String, Object> task : pendingPlayVoiceTask) {

            String playYmd = task.get("play_ymd") == null ? "" : task.get("play_ymd").toString().trim();
            String playWeek = task.get("play_week") == null ? "" : task.get("play_week") .toString().trim();

            if(true){ // 当天是否需要播放
//                log.info("查看 当天需要播放");
                String playTime = task.get("play_time") == null ? "" : task.get("play_time").toString().trim();
                if(true){ // 此时是否需要播放
//                    log.info("查看此时需要播放");



                    Integer storeVoiceId = Utils.convertToInt(task.get("id"), -1);
                    Integer event = Utils.convertToInt(task.get("event"), -1);
                    Integer interval = Utils.convertToInt(task.get("interval"), -1);
                    Integer playCount = Utils.convertToInt(task.get("play_count"), -1);
                    System.out.println("play count " + playCount);
                    Integer volume = Utils.convertToInt(task.get("volume"), -1);
                    Integer boxIndex = Utils.convertToInt(task.get("box_index"), -1);


                    // event 705 系统通知板子播放某一个媒体（媒体编号、多少次、播放的音量：0~254、用那个播放器：1室内、2室外）
                    if(playCount > 0){
                        Map<String, Object> map = new HashMap<>();
                        map.put("event", 705);
                        map.put("update_voice_name", event);
                        map.put("play_count", playCount);
                        map.put("volume", volume);
                        map.put("box_index", boxIndex);
                        map.put("interval", interval);
                        tasks.add(Task.builder()
                                .task(map)
                                .taskStatus(TaskStatus.pending)
                                .build());
                    }

                }
            }
        }


        return tasks;
    }

}

