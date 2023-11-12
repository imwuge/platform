package com.rampbot.cluster.platform.domain;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Data
@Builder
@Getter
@Setter

@Slf4j
public class DownLoadVoiceData2 {
    private final long MAX_RESERVE_TIME = 2 * 60 * 60 * 1000; // 2小时

    private Integer voiceId;

    private Integer version;

    private long createTime;  // 创建时间

    private final byte[] voiceData; // 要下载语音的音频内容


    /**
     * 判断音频数据是否需要继续保留
     * @return
     */
    public Boolean isCanReserve(){
        return System.currentTimeMillis() - createTime <= MAX_RESERVE_TIME;
    }

    public byte[] getVoiceData(int downloadIndex, int downloadOnceLength){
        // 下载起至位置
        int startPos = (downloadIndex - 1) * downloadOnceLength;
        int endPos = downloadIndex * downloadOnceLength - 1;

        if(this.voiceData == null || this.voiceData.length < 1){
            return null;
        }else if(startPos >= this.voiceData.length){
            return null;
        }else{
            if (endPos >= this.voiceData.length)
            endPos = this.voiceData.length-1;
            byte[] result = new byte[endPos - startPos + 1];
            for(int i = 0; i < result.length; i++){
                result[i] = this.voiceData[i+startPos];
            }
            return result;
        }
    }

    public void updateCreateTime(){
        createTime = System.currentTimeMillis();
    }


    public int remainLength(int index, int downloadOnceLength){
        return voiceData.length/downloadOnceLength - index;
    }
}
