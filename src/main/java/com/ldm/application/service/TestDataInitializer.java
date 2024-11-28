package com.ldm.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldm.domain.model.testdata.ClusterData;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;

//@IfBuildProfile("experiment")
@ApplicationScoped
@Slf4j
public class TestDataInitializer {
    @Inject
    ObjectMapper objectMapper; // Inject ObjectMapper provided by Quarkus

    /**
     * Loads test data from a JSON file located via the filePath
     * The data is read and each Microservice is populated with its corresponding K8sCluster details.
     *
     * @param filePath Path to the JSON resource file within the classpath.
     * @return List of Microservice objects initialized with cluster information.
     * @throws IOException if the resource file is not found or cannot be read.
     */
    public ClusterData getTestDataFromFile(String filePath) throws IOException {
        // Deserialize the JSON file to ClusterData structure

        return objectMapper.readValue(new File(filePath), ClusterData.class);

//        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
//            if (inputStream == null) {
//                throw new IOException("Resource not found: " + resourcePath);
//            }
//
//            // Deserialize the JSON file into ClusterData structure
//            ClusterData clusterData = objectMapper.readValue(inputStream, ClusterData.class);

//        }
    }
}
