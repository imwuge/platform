package com.rampbot.cluster.platform.domain;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Data
@Builder
@Getter
@Setter
public class Task {
    Long taskId;
    TaskStatus taskStatus;
    Map<String, Object> task;

}
