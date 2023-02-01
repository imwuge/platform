package com.rampbot.cluster.platform.client.utils;

import com.alibaba.druid.pool.DruidDataSourceFactory;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.*;
import java.util.*;

@Slf4j
public class SQLHelper {
//    private static String driver = "com.mysql.jdbc.Driver";
////    private static String url = "jdbc:mysql://db.ttzhwr.cn:3306/macrobot_mus?useUnicode=true&characterEncoding=UTF-8&useSSL=false&autoReconnect=true&allowMultiQueries=true";
////    private static String url = "jdbc:mysql://db.ttzhwr.cn:3306/macrobot_mus?useUnicode=true&characterEncoding=UTF-8&useSSL=false&autoReconnect=true&allowMultiQueries=true&serverTimezone=GMT%2B8";
//    private static String url = "jdbc:mysql://192.168.0.52:4001/macrobot_mus?useUnicode=true&characterEncoding=UTF-8&useSSL=false&autoReconnect=true&allowMultiQueries=true&serverTimezone=GMT%2B8";
//    private static String name = "root";
//    private static String pwd = "root";

    // TODO: 本机调试 ......................
//    private static String driver = "com.mysql.jdbc.Driver";
//    private static String url = "jdbc:mysql://127.0.0.1:3306/macrobot_mus?useUnicode=true&characterEncoding=UTF-8&useSSL=false&autoReconnect=true&allowMultiQueries=true";
//    private static String name = "root";
//    private static String pwd = "root";


    private static DataSource source;

    /**
     * initDbConfig
     */
    private static boolean initDbConfig() {
//		if (driver != null && driver.length() > 0) {
//			return true;
//		}
//
//		final Properties properties = new Properties();
//		try {
//			properties.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("resources/jdbc.properties"));
//		} catch (final IOException e) {
//			log.error("加载JDBC配置信息失败..." + e.toString(), e);
//
//			return false;
//		}
//
//		driver = properties.getProperty("jdbc.driver");
//		url = properties.getProperty("jdbc.url");
//		name = properties.getProperty("jdbc.username");
//		pwd = properties.getProperty("jdbc.password");

//		driver = "com.mysql.jdbc.Driver";
//		url = "jdbc:mysql://localhost:3306/macrobot_admin?useUnicode=true&characterEncoding=UTF-8&autoReconnect=true&allowMultiQueries=true";
//		name = "root";
//		pwd = "G84Kd_n3L51Gh*n2D{v";

//        driver = "com.mysql.jdbc.Driver";
//        url = "jdbc:mysql://db.ttzhwr.cn:3306/macrobot_mus?useUnicode=true&characterEncoding=UTF-8&useSSL=false&autoReconnect=true&allowMultiQueries=true";
//        name = "root";
//        pwd = "root";

//      =============================================================================================
//        driver = "com.mysql.jdbc.Driver";
//        InputStream in = MySQLHelper.class.getClassLoader().getResourceAsStream("jdbc.properties");
//        Properties properties = new Properties();
//        try {
//            properties.load(in);
//        } catch (IOException e) {
//            url = "jdbc:mysql://db.a.macrobot-sys.site:3306/macrobot_admin?useUnicode=true&characterEncoding=UTF-8&autoReconnect=true&allowMultiQueries=true";
//            name = "root";
//            pwd = "root";
//            return true;
//        }
//
//        url = properties.getProperty("url");
//        name = properties.getProperty("username");
//        pwd = properties.getProperty("password");

        return true;
    }

    static {
        try{
            //创建properties对象，用来封装从文件中获取的流数据
            Properties pros = new Properties();
            //采用类加载方式获取文件的内容，并封装成流
            InputStream is = ClassLoader.getSystemClassLoader().getResourceAsStream("druid.properties");
            //将流传入到pros对象中
            pros.load(is);
            //利用工厂类创建数据库连接池
            source = DruidDataSourceFactory.createDataSource(pros);
        }catch (Exception e){
            e.printStackTrace();
        }
    }



    
    /**
     * create connection
     */
    private static Connection getConnection() {

//        if (!initDbConfig()) {
//            return null;
//        }

        // 1、通过JDBC获得链接
        try {
            return source.getConnection();
        } catch (Exception e) {
            log.error(e.toString(), e);
        }

        return null;
    }

    /**
     * executeUpdate
     */
    public static int executeUpdate(String sql, Object... prams) {

        Connection conn = null;
        PreparedStatement pstmt = null;

        try {
            conn = getConnection();
            pstmt = conn.prepareStatement(sql);

            if (prams != null) {
                for (int i = 0; i < prams.length; i++) {
                    pstmt.setObject(i + 1, prams[i]);
                }
            }

            return pstmt.executeUpdate();
        } catch (SQLException e) {
            log.error(e.toString(), e);
        } finally {
            try {
                if (pstmt != null) {
                    pstmt.closeOnCompletion();
                }
            } catch (Exception e) {
                log.error(e.toString(), e);
            }

            try {
                if (conn != null) {
                    conn.close();
//                    pool.add(conn);
                }
            } catch (Exception e) {
                log.error(e.toString(), e);
            }
        }

        return -9;
    }

    /**
     * executeReturnInserLastId
     */
    public static int executeReturnInsertLastId(String sql, Object... prams) {

        Connection conn = null;
        PreparedStatement pstmt = null;

        try {
            conn = getConnection();
            // 传入参数：STATEMENT.RETURN_GENERATED_KEYS，指定返回生成的主键
            pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);

            if (prams != null) {
                for (int i = 0; i < prams.length; i++) {
                    pstmt.setObject(i + 1, prams[i]);
                }
            }

            // 执行sql
            int count = pstmt.executeUpdate();

            // 返回的主键
            int autoIncKey = 0;

            if (count > 0) {
                ResultSet rs = pstmt.getGeneratedKeys(); // 获取结果

                if (rs.next()) {
                    autoIncKey = rs.getInt(1);// 取得ID
                } else {
                    log.error("插入语句出错，没有获取到新增的ID！");
                }
            }
            return autoIncKey;
        } catch (SQLException e) {
            log.error(e.toString(), e);
        } finally {
            try {
                if (pstmt != null) {
                    pstmt.closeOnCompletion();
                }
            } catch (Exception e) {
                log.error(e.toString(), e);
            }

            try {
                if (conn != null) {
                    conn.close();
//                    pool.add(conn);
                }
            } catch (Exception e) {
                log.error(e.toString(), e);
            }
        }

        return 0;
    }

    /**
     * executeQueryTable
     */
    public static List<Map<String, Object>> executeQueryTable(String sql, Object... prams) {

        ResultSet res = null;
        Connection conn = null;
        PreparedStatement pstmt = null;
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();

        try {
            conn = getConnection();
            pstmt = conn.prepareStatement(sql);

            if (prams != null) {
                for (int i = 0; i < prams.length; i++) {
                    pstmt.setObject(i + 1, prams[i]);
                }
            }

            res = pstmt.executeQuery();

            if (res == null) {
                return null;
            }

            ResultSetMetaData md = res.getMetaData();
            int columnCount = md.getColumnCount();
            while (res.next()) {
                Map<String, Object> rowData = new HashMap<String, Object>();
                for (int i = 1; i <= columnCount; i++) {
//                    rowData.put(md.getColumnName(i), res.getObject(i));
                    rowData.put(md.getColumnLabel(i), res.getObject(i));
                }
                list.add(rowData);
            }
        } catch (SQLException e) {
            log.error(e.toString(), e);
        } finally {
            try {
                if (pstmt != null) {
                    pstmt.closeOnCompletion();
                }
            } catch (Exception e) {
                log.error(e.toString(), e);
            }

            try {
                if (conn != null) {
                    conn.close();
//                    pool.add(conn);
                }
            } catch (Exception e) {
                log.error(e.toString(), e);
            }
        }

        return list;
    }



}
