package com.dreams.application.service;

import com.dreams.domain.measurement.MeasurementData;
import jakarta.inject.Singleton;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;

@Singleton
@Data
@Slf4j
public class MetricsAggregator {

    private final ConcurrentHashMap<String, MeasurementData> measurements = new ConcurrentHashMap<>();

    public void recordStart(String key, String processName, long startTime, String createdBy) {
        measurements.computeIfAbsent(key, k -> new MeasurementData(key, processName, startTime, createdBy));
    }

    public void recordEnd(String key, long endTime, String result) {
        measurements.computeIfPresent(key, (k, v) -> {
            v.setEndTime(endTime);
            long durationNano = endTime - v.getStartTime();

            double durationMillis = durationNano / 1_000_000.0;

            v.setDurationInMs(durationMillis);
            v.setResult(result);
            return v;
        });
    }
}
