//package com.ldm.infrastructure.json;
//
//import com.fasterxml.jackson.core.JsonGenerator;
//import com.fasterxml.jackson.databind.JsonSerializer;
//import com.fasterxml.jackson.databind.SerializerProvider;
//import com.ldm.domain.model.Microservice;
//
//import java.io.IOException;
//
//public class MicroserviceKeySerializer extends JsonSerializer<Microservice> {
//    @Override
//    public void serialize(Microservice value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
//        gen.writeFieldName(value.getId()); // Use the ID as the key in JSON
//    }
//}
