import com.alibaba.druid.pool.DruidDataSourceFactory;
;
import com.rampbot.cluster.platform.client.utils.DBHelper;
import com.rampbot.cluster.platform.client.utils.RedisHelper;
import com.rampbot.cluster.platform.client.utils.SQLHelper;
import com.rampbot.cluster.platform.client.utils.Utils;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;

import javax.sql.DataSource;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Slf4j
public class test {
    public static void main(String[] args) throws Exception {


//        System.out.println(DBHelper.getConfigValue(104,10050));

//        RedisHelper.testR();

         System.out.println(RedisHelper.getHelperId(1,1));
        // int companyId, int storeId, Long orderNo, String type, String title, String content,  int helperId
//        RedisHelper.writadeRedis(1, 2, (long)3, "安防", "5", "6", 10);
//        System.out.println(RedisHelper.getHelperId(1,2));
//        System.out.println(RedisHelper.isExistsOrder(1,2, "安防" ));

//        DBHelper.addNotifyV2(1, 1, "断电");
//
//
//
//        Long id = Long.valueOf(123);
//        System.out.println(id);

//        Map<String, Object> voiceMsg = DBHelper.getVoice(41, "10050");
//        System.out.println(voiceMsg);
//
//        String fileUrl = voiceMsg.get("file_url").toString();
//
//
//        byte[] voiceData = Utils.downloadVoiceFiles(fileUrl);
//
//        System.out.println(voiceData.length);

//        System.out.println(DBHelper.getStoreStatus(10811));
//        //1.导入jar包
//        //2.定义配置文件
//        //3. 加载配置文件
//        Properties prop=new Properties();
//        prop.load(new FileInputStream("C:\\Users\\work\\workplace\\stm.server\\platform\\src\\main\\resources\\druid.properties"));
//
//        //4. 获取连接池对象
//        DataSource dataSource= DruidDataSourceFactory.createDataSource(prop);
//
//        //5. 获取数据库连接 Connection
//        Connection conn=dataSource.getConnection();
//
//
//        String sql = "SELECT status FROM stores WHERE store_id = " + 10811;;
//        Statement stmt=conn.createStatement();
//        ResultSet rs = stmt.executeQuery(sql);
//        log.info("查看 {} ", rs);
//        while (rs.next()) {
//            int id = rs.getInt("status");
//            System.out.println(id);
//        }

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


}

