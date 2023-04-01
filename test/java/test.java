import com.alibaba.druid.pool.DruidDataSourceFactory;
;
import com.rampbot.cluster.platform.client.utils.DBHelper;
import com.rampbot.cluster.platform.client.utils.RedisHelper;
import com.rampbot.cluster.platform.client.utils.SQLHelper;
import com.rampbot.cluster.platform.client.utils.Utils;

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
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
public class test {
    public static void main(String[] args) throws Exception {


        // int companyId, int storeId, Long orderNo, String type, String title, String content, int helperId, int statusOrLevel
        //RedisHelper.writeOrderOrNotify(10050,10011, (long)123, "购物", "", "", -1 , 0);
        // int companyId, Integer storeId, int type, long id, int stats)
//        RedisHelper.addIotTasks(10050,10628,903, 111837, 0);


//        public static  void printMoney(int year, int month, int firstMoney){

        //printMoney(3, 0, 10 - 1.4);

        //getSerious(140, 6, "1011800000",  "电磁锁 继电器 5 0313");
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


        DBHelper.addNotifyV2(10011,10050, "失联", "测试店");

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
//            // 如果文件不存在，则创建新的文件
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

        System.out.println("到" + (2023 + year) + "年6月，还清贷款");

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

