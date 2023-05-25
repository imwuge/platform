package com.rampbot.cluster.platform.client.controller;

import com.rampbot.cluster.platform.client.utils.DBHelper;
import com.rampbot.cluster.platform.client.utils.Utils;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class LogAssistant {

    private final long id;
    private final int companyId;
    private final int storeId;
    private final int maxLogsNum = 30;



    private List<String> time2contentList = new LinkedList<>();

    public LogAssistant(int companyId, int storeId, long id) {
        this.companyId = companyId;
        this.storeId = storeId;
        this.id = id;
    }




    public void addLog(String system, String log){
        String time = Utils.getTime();
        this.time2contentList.add(time + ": " + log);


        if(this.time2contentList.size() > maxLogsNum){
            this.time2contentList.remove(0);
        }

        String finalContent = "";
        for(String content : this.time2contentList){
            finalContent = finalContent + content + "\r\n" ;
        }


        DBHelper.insertStoreLogsId(this.id, this.storeId, this.companyId, finalContent, system);
    }

//    public static void main(String[] args) throws InterruptedException {
//        addLog("1");
//        Thread.sleep(1000);
//        addLog("2");
//        Thread.sleep(1000);
//        addLog("3");
//        Thread.sleep(1000);
//        addLog("4");
//        Thread.sleep(1000);
//        addLog("5");
//        Thread.sleep(1000);
//        addLog("6");
//        Thread.sleep(1000);
//        addLog("7");
//        Thread.sleep(1000);
//        addLog("8");
//        Thread.sleep(1000);
//        addLog("9");
//        Thread.sleep(1000);
//        addLog("10");
//        Thread.sleep(1000);
//        addLog("11");
//        Thread.sleep(1000);
//        addLog("12");
//
//    }
}
