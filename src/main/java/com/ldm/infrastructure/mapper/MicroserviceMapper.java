package com.ldm.infrastructure.mapper;

import com.ldm.domain.model.K8sCluster;
import com.ldm.domain.model.Microservice;
import com.ldm.infrastructure.serialization.protobuf.MigrationActionOuterClass;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Map;
import java.util.stream.Collectors;

@Mapper(componentModel = "cdi", uses = {K8sClusterMapper.class})
public interface MicroserviceMapper {

    @Mapping(target = "k8sCluster", source = "protoMicroservice.k8SCluster")
    @Mapping(target = "isNonMigratable", source = "protoMicroservice.nonMigratable")
    @Mapping(target = "affinities", expression = "java(mapProtoToDomainAffinities(protoMicroservice))")
    @Mapping(target = "dataExchangedWithServices", expression = "java(mapProtoToDomainDataExchanged(protoMicroservice))")
    Microservice toDomainModel(MigrationActionOuterClass.Microservice protoMicroservice);

    @Mapping(target = "k8SCluster", source = "microservice.k8sCluster")
    @Mapping(target = "nonMigratable", source = "microservice.nonMigratable")
    @Mapping(target = "affinities", expression = "java(mapDomainToProtoAffinities(microservice.getAffinities()))")
    @Mapping(target = "dataExchangedWithServices", expression = "java(mapDomainToProtoDataExchanged(microservice.getDataExchangedWithServices()))")
    MigrationActionOuterClass.Microservice toProto(Microservice microservice);


    // Mapping Protobuf affinities map to Domain model affinities map
    default Map<Microservice, Double> mapProtoToDomainAffinities(MigrationActionOuterClass.Microservice protoMicroservice) {
        if (protoMicroservice.getAffinitiesMap() == null) {
            return Map.of();
        }

        return protoMicroservice.getAffinitiesMap().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> buildMicroservice(entry.getKey(), protoMicroservice), // Build full Microservice object
                        Map.Entry::getValue
                ));
    }

    // Mapping Domain model affinities map to Protobuf affinities map
    default Map<String, Double> mapDomainToProtoAffinities(Map<Microservice, Double> affinities) {
        if (affinities == null) {
            return Map.of();
        }

        return affinities.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().getId(), // Map Microservice key to String
                        Map.Entry::getValue
                ));
    }

    // Mapping Protobuf dataExchangedWithServices map to Domain model
    default Map<Microservice, Double> mapProtoToDomainDataExchanged(MigrationActionOuterClass.Microservice protoMicroservice) {
        if (protoMicroservice.getDataExchangedWithServicesMap() == null) {
            return Map.of();
        }

        return protoMicroservice.getDataExchangedWithServicesMap().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> buildMicroservice(entry.getKey(), protoMicroservice), // Build full Microservice object
                        Map.Entry::getValue
                ));
    }

    // Mapping Domain model dataExchangedWithServices map to Protobuf
    default Map<String, Double> mapDomainToProtoDataExchanged(Map<Microservice, Double> dataExchangedWithServices) {
        if (dataExchangedWithServices == null) {
            return Map.of();
        }

        return dataExchangedWithServices.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().getId(), // Map Microservice key to String
                        Map.Entry::getValue
                ));
    }

    // Build a full Microservice object for affinities or dataExchangedWithServices keys
    default Microservice buildMicroservice(String microserviceId, MigrationActionOuterClass.Microservice protoMicroservice) {
        return Microservice.builder()
                .id(microserviceId)
                .name(protoMicroservice.getName())
                .isNonMigratable(protoMicroservice.getNonMigratable())
                .k8sCluster(new K8sCluster(
                        protoMicroservice.getK8SCluster().getId(),
                        protoMicroservice.getK8SCluster().getLocation()
                ))
                .cpuUsage(protoMicroservice.getCpuUsage())
                .memoryUsage(protoMicroservice.getMemoryUsage())
                .build();
    }
}
