package com.ldm.domain.measurement;

import com.ldm.shared.constants.MessageTypeEnum;
import lombok.Data;

@Data
public class MeasurementDataDTO {
    private MessageTypeEnum type;
    private MeasurementData value;

    public MeasurementDataDTO(MeasurementData value) {
        this.type = MessageTypeEnum.MEASUREMENT_DATA;
        this.value = value;
    }
}
