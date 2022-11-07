package com.rampbot.cluster.platform.domain;


import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Builder
@Getter
@Setter
public class VoiceTask {
    private final int downloadOnceLength = 1024;
    private final int version; // 要下载语音的版本
    private final byte[] voiceData; // 要下载语音的音频内容
    private final int voiceId; // 要下载语音的id
    private final int downloadPlace; // 要下载到盒子中的位置
    private int nextStartPlace; // 下次开始下载的位置
    private TaskStatus taskStatus; // 任务状态
    private Long taskId; //


    /**
     * 获取还剩下的长度
     * @return
     */
    public int getRemainLength(){
        return this.voiceData.length - nextStartPlace;
    }

    /**
     * 获取音频总长度
     * @return
     */
    public int getVoiceLength(){
        return this.voiceData.length;
    }

    /**
     * 获取一次要下载的音频长度
     */
    public byte[] getVoice(){

        if((voiceData.length - nextStartPlace) >= downloadOnceLength){
            byte[] result = new byte[downloadOnceLength];
            for(int i = 0; i < downloadOnceLength; i++){
                result[i] = voiceData[i+nextStartPlace];
            }
            nextStartPlace = nextStartPlace + downloadOnceLength;
            return result;
        }else if(nextStartPlace < voiceData.length) {
            int remianLength = voiceData.length - nextStartPlace;
            byte[] result = new byte[remianLength];
            for(int i = 0; i < remianLength; i++){
                result[i] = voiceData[i+nextStartPlace];
            }
            nextStartPlace = voiceData.length;

            return result;
        }else {
            return null;
        }
    }
}
