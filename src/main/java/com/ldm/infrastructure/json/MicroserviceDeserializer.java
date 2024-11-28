package com.ldm.infrastructure.json;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.ldm.domain.model.K8sCluster;
import com.ldm.domain.model.Microservice;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class MicroserviceDeserializer extends JsonDeserializer<Microservice> {
    @Override
    public Microservice deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
        JsonNode root = p.getCodec().readTree(p);

        String id = root.get("id").asText();
        boolean isNonMigratable = root.get("isNonMigratable").asBoolean();

        Map<Microservice, Double> affinities = new HashMap<>();

        JsonNode affinitiesNode = root.get("affinities");
        if (affinitiesNode != null && affinitiesNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = affinitiesNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String microserviceKey = field.getKey();
                JsonNode affinityDetails = field.getValue();

                K8sCluster k8sCluster = new K8sCluster(
                        affinityDetails.get("k8sCluster").get("id").asText(),
                        affinityDetails.get("k8sCluster").get("location").asText()
                );

                Microservice affinityMicroservice = new Microservice();
                affinityMicroservice.setId(microserviceKey);
                affinityMicroservice.setName(affinityDetails.get("name").asText());
                affinityMicroservice.setK8sCluster(k8sCluster);

                double affinityValue = affinityDetails.get("value").asDouble();
                affinities.put(affinityMicroservice, affinityValue);
            }
        }

        return Microservice.builder()
                .id(id)
                .name(id)
                .isNonMigratable(isNonMigratable)
                .affinities(affinities)
                .build();
    }
}
