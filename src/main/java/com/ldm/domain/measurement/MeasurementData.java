package com.ldm.domain.measurement;

import lombok.Data;

@Data
public class MeasurementData {

    private String processId;
    private String processName;
    private String result;
    private long startTime;
    private long endTime=-1;
    private Double durationInMs;
    private String createdBy;



    public MeasurementData(String processId, String processName, long startTime, String createdBy) {
        this.processId = processId;
        this.processName = processName;
        this.startTime = startTime;
        this.createdBy = createdBy;
    }
}
