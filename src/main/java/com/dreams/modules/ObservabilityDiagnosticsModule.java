package com.dreams.modules;

import com.dreams.application.service.MeasurementService;
import com.dreams.domain.measurement.MeasurementData;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Observability and Diagnostics Module (ODM) — Metrics aggregation and event logging.
 * Provides structured access to performance measurements and system diagnostics.
 */
@ApplicationScoped
@Slf4j
public class ObservabilityDiagnosticsModule {

    @Inject
    MeasurementService measurementService;

    public void recordProcessStart(String processId, String processName, long startTime, String createdBy) {
        measurementService.recordStart(processId, processName, startTime, createdBy);
    }

    public void recordProcessEnd(String processId, long endTime, String result) {
        measurementService.recordEnd(processId, endTime, result);
    }

    public Map<String, MeasurementData> getAllMeasurements() {
        return measurementService.getMeasurements();
    }
}
