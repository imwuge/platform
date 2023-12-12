package com.rampbot.cluster.platform.client.utils;


import com.rampbot.cluster.platform.domain.DownLoadVoiceData2;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class DownloadVoiceHelper {
    public static Map<Integer, DownLoadVoiceData2> voiceId2DownloadData = new HashMap();

    public static int getRemainLengthIndex(int voiceId, int downloadIndex, int downloadOnceLength){
        if(voiceId2DownloadData.containsKey(voiceId)){
            return voiceId2DownloadData.get(voiceId).remainLength(downloadIndex, downloadOnceLength);
        }else {
            return -1;
        }
    }

    public static byte[] getVoiceData(int voiceId, int downloadIndex, int downloadOnceLength){
        if(voiceId2DownloadData.containsKey(voiceId)){
            return voiceId2DownloadData.get(voiceId).getVoiceData(downloadIndex, downloadOnceLength);
        }else {
            return null;
        }
    }

    public static int getVoiceClientDownloadName(int voiceId){
        if(voiceId2DownloadData.containsKey(voiceId)){
            return voiceId2DownloadData.get(voiceId).getClientDownloadName();
        }else {
            return -1;
        }
    }

    public static boolean isContainVoice(Integer voiceId){
        return voiceId2DownloadData.containsKey(voiceId);
    }

    public static int getVersion(Integer voiceId){
        if(voiceId2DownloadData.containsKey(voiceId)){
            return voiceId2DownloadData.get(voiceId).getVersion();
        }else {
            return -1;
        }
    }

    public static int getDataLength(Integer voiceId){
        if(voiceId2DownloadData.containsKey(voiceId)){
            return voiceId2DownloadData.get(voiceId).getVoiceData().length;
        }else {
            return -1;
        }
    }

    public static void addVoice(Integer voiceId, int companyId, boolean isUpdate){
        if(!voiceId2DownloadData.containsKey(voiceId) || isUpdate) {
            // 准备音频数据
            String fileUrl = null;
            byte[] voiceData = null;
            int version = -1;
            int clientDownloadName = -1;
            Map<String, Object> voiceMsg = DBHelper.getVoice(voiceId, companyId);
            fileUrl = voiceMsg.get("file_url").toString();
            version = Utils.convertToInt(voiceMsg.get("version"), -1);
            try {
                voiceData = Utils.downloadVoiceFiles(fileUrl);
            } catch (IOException e) {
                e.printStackTrace();
            }
            clientDownloadName = Utils.convertToInt(voiceMsg.get("client_download_id"), -1);
            //voiceData = Utils.getContent("C:\\Users\\work\\Desktop\\test.mp3");  //测试代码 需要恢复
            DownLoadVoiceData2 downLoadVoiceData = DownLoadVoiceData2.builder()
                    .voiceData(voiceData)
                    .version(version)
                    .voiceId(voiceId)
                    .clientDownloadName(clientDownloadName)
                    .createTime(System.currentTimeMillis())
                    .build();
            voiceId2DownloadData.put(voiceId, downLoadVoiceData);
        }else if(voiceId2DownloadData.containsKey(voiceId)){
            voiceId2DownloadData.get(voiceId).updateCreateTime();
        }
//        else{
//            // 准备音频数据
//            String fileUrl = null;
//            byte[] voiceData = null;
//            int version = -1;
//            Map<String, Object> voiceMsg = DBHelper.getVoice(voiceId, companyId);
//            fileUrl = voiceMsg.get("file_url").toString();
//            version = Utils.convertToInt(voiceMsg.get("version"), -1);
//            try {
//                voiceData = Utils.downloadVoiceFiles(fileUrl);
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//           //voiceData = Utils.getContent("C:\\Users\\work\\Desktop\\test.mp3");  //测试代码 需要恢复
//            DownLoadVoiceData2 downLoadVoiceData = DownLoadVoiceData2.builder()
//                    .voiceData(voiceData)
//                    .version(version)
//                    .voiceId(voiceId)
//                    .createTime(System.currentTimeMillis())
//                    .build();
//            voiceId2DownloadData.put(voiceId, downLoadVoiceData);
//        }

    }

    public void removeVoice(Integer voiceId){
        voiceId2DownloadData.remove(voiceId);
    }

    public static void inspectVoice(){
        if(voiceId2DownloadData.size() > 0){
            Set<Integer> toBeRemoveVoiceData = voiceId2DownloadData.entrySet().stream().filter(e -> !e.getValue().isCanReserve()).map(Map.Entry::getKey).collect(Collectors.toSet());
            if(toBeRemoveVoiceData != null && toBeRemoveVoiceData.size() > 0){
                for(Integer voiceId : toBeRemoveVoiceData){
                    log.info("音频{}超过{}没有涉及下载任务，清空", voiceId , voiceId2DownloadData.get(voiceId).getMAX_RESERVE_TIME());
                    voiceId2DownloadData.remove(voiceId);
                }
            }
        }

    }
}
