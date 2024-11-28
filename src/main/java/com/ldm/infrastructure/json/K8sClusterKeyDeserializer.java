//package com.ldm.infrastructure.json;
//
//import com.fasterxml.jackson.databind.DeserializationContext;
//import com.fasterxml.jackson.databind.KeyDeserializer;
//import com.ldm.domain.model.K8sCluster;
//
//import java.io.IOException;
//
//public class K8sClusterKeyDeserializer extends KeyDeserializer {
//    @Override
//    public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
//        String[] parts = key.split("\\|"); // Split the serialized key into parts (id and location)
//        if (parts.length != 2) {
//            throw new IOException("Invalid key format for K8sCluster: " + key);
//        }
//        return new K8sCluster(parts[0], parts[1]); // Create a new K8sCluster object
//    }
//}
