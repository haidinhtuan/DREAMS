//package com.ldm.infrastructure.json;
//
//import com.fasterxml.jackson.databind.DeserializationContext;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.KeyDeserializer;
//import com.ldm.domain.model.K8sCluster;
//import com.ldm.domain.model.Microservice;
//
//import java.io.IOException;
//import java.util.*;
//
//public class MicroserviceKeyDeserializer extends KeyDeserializer {
//
//    @Override
//    public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
////        Map<String, K8sCluster> k8sClusterCache = new HashMap<>();
//        Map<Microservice, Double> affinitiesMap = new HashMap<>();
//
//        JsonNode node = ctxt.readTree(ctxt.getParser());
//
//        JsonNode microserviceNode = node.get(key);
//        String name = microserviceNode.get("name").asText();
//        String value = microserviceNode.get("value").asText();
//        JsonNode k8sClusterNode = microserviceNode.get("k8sCluster");
//        K8sCluster k8sCluster = new K8sCluster(k8sClusterNode.get("id").asText(), k8sClusterNode.get("location").asText());
//
//        new Microservice(key, name, )
//
//
//
//
//
////
////
////        String id = node.get("id").asText();
////        boolean isNonMigratable = node.get("isNonMigratable").asBoolean();
////        JsonNode affinitiesNode = node.get("affinities");
////
////        Iterator<String> fieldNames = affinitiesNode.fieldNames();
////        while (fieldNames.hasNext()) {
////            String affinityMicroserviceId = fieldNames.next();
////            JsonNode affinityMicroserviceNode = affinitiesNode.get(affinityMicroserviceId);
////
////            // Parse affinity microservice details
////            String name = affinityMicroserviceNode.get("name").asText();
////            double affinityValue = affinityMicroserviceNode.get("value").asDouble();
////
////            // Parse and cache K8sCluster
////            JsonNode k8sClusterNode = affinityMicroserviceNode.get("k8sCluster");
////            String k8sClusterId = k8sClusterNode.get("id").asText();
////            K8sCluster k8sCluster = k8sClusterCache.computeIfAbsent(
////                    k8sClusterId,
////                    k8sId -> new K8sCluster(k8sId, k8sClusterNode.get("location").asText())
////            );
////
////            // Build affinity microservice
////            Map<Microservice, Double> affinity = new HashMap<>();
////            Microservice affinityMicroservice = Microservice.builder()
////                    .id(affinityMicroserviceId)
////                    .name(name)
////                    .affinities(affinity) // Initialize empty affinities for now
////                    .k8sCluster(k8sCluster)
////                    .build();
////
////            // Add bidirectional mapping
////            affinity.put(affinityMicroservice, affinityValue);
////
////            // Add to affinities map
////            affinitiesMap.put(affinityMicroservice, affinityValue);
////        }
//
//
//
//        // Build and return the main Microservice
//        return Microservice.builder()
//                .id(key)
//                .isNonMigratable(isNonMigratable)
//                .affinities(affinitiesMap)
//                .build();
//    }
//
//
////    @Override
////    public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
////        Map<Microservice, Double> affinitiesMap = new HashMap<>();
////
////        JsonNode node = ctxt.readTree(ctxt.getParser());
////        String id = node.get("id").asText();
////        boolean isNonMigratable = node.get("isNonMigratable").asBoolean();
////        JsonNode affinitiesNode = node.get("affinities");
////
////        Microservice microservice = Microservice.builder()
////                .id(id)
////                .isNonMigratable(isNonMigratable)
////                .build();
////
////        Iterator<String> fieldNames = affinitiesNode.fieldNames();
////        while (fieldNames.hasNext()) {
////            String affinityMicroserviceId = fieldNames.next();
////            JsonNode affinityMicroserviceNode = affinitiesNode.get(affinityMicroserviceId);
////            String name = affinityMicroserviceNode.get("name").asText();
////            double affinityValue = affinityMicroserviceNode.get("value").asDouble();
////            JsonNode k8sClusterNode = affinityMicroserviceNode.get("k8sCluster");
////            K8sCluster k8sCluster = new K8sCluster(k8sClusterNode.get("id").asText(), k8sClusterNode.get("location").asText());
////            Map<Microservice, Double> affinity = new HashMap<>();
////            affinity.put(microservice, affinityValue);
////
////            Microservice affinityMicroservice = Microservice.builder()
////                    .id(affinityMicroserviceId)
////                    .name(name)
////                    .affinities(affinity)
////                    .k8sCluster(k8sCluster)
////                    .build();
//
////            affinitiesMap.put(affinityMicroservice, affinityValue);
////        }
////
////        microservice.setAffinities(affinitiesMap);
////
////        return microservice;
////    }
//}
