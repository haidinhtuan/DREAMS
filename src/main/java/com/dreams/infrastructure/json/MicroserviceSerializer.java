package com.dreams.infrastructure.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.dreams.domain.model.K8sCluster;
import com.dreams.domain.model.Microservice;

import java.io.IOException;
import java.util.Map;

public class MicroserviceSerializer extends JsonSerializer<Microservice> {
    @Override
    public void serialize(Microservice microservice, JsonGenerator jsonGenerator, SerializerProvider serializers) throws IOException {
        jsonGenerator.writeStartObject();

        // Write basic fields
        jsonGenerator.writeStringField("id", microservice.getId());
        jsonGenerator.writeBooleanField("isNonMigratable", microservice.isNonMigratable());

        // Write affinities
        jsonGenerator.writeObjectFieldStart("affinities");
        for (Map.Entry<Microservice, Double> entry : microservice.getAffinities().entrySet()) {
            Microservice affinityMicroservice = entry.getKey();
            Double affinityValue = entry.getValue();

            jsonGenerator.writeObjectFieldStart(affinityMicroservice.getId());
            jsonGenerator.writeStringField("name", affinityMicroservice.getId()); // Name is equivalent to ID
            jsonGenerator.writeNumberField("value", affinityValue);

            // Write K8sCluster details
            K8sCluster k8sCluster = affinityMicroservice.getK8sCluster();
            if (k8sCluster != null) {
                jsonGenerator.writeObjectFieldStart("k8sCluster");
                jsonGenerator.writeStringField("id", k8sCluster.getId());
                jsonGenerator.writeStringField("location", k8sCluster.getLocation());
                jsonGenerator.writeEndObject();
            }
            jsonGenerator.writeEndObject();
        }
        jsonGenerator.writeEndObject();

        jsonGenerator.writeEndObject();
    }
}
