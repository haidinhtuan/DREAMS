//package com.ldm.infrastructure.json;
//
//import com.fasterxml.jackson.core.JsonGenerator;
//import com.fasterxml.jackson.databind.JsonSerializer;
//import com.fasterxml.jackson.databind.SerializerProvider;
//import com.ldm.domain.model.K8sCluster;
//
//import java.io.IOException;
//
//public class K8sClusterKeySerializer extends JsonSerializer<K8sCluster> {
//
//    @Override
//    public void serialize(K8sCluster value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
//        gen.writeFieldName(value.getId() + "|" + value.getLocation()); // Serialize as "id|location"
//    }
//}
