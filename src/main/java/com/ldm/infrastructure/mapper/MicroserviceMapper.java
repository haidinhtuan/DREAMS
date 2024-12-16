package com.ldm.infrastructure.mapper;

import com.ldm.domain.model.K8sCluster;
import com.ldm.domain.model.Microservice;
import com.ldm.infrastructure.serialization.protobuf.EvaluateMigrationProposalOuterClass;
import com.ldm.infrastructure.serialization.protobuf.MigrationActionOuterClass;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Mapper(componentModel = "cdi", uses = {K8sClusterMapper.class})
public interface MicroserviceMapper {

    @Mapping(target = "k8sCluster", source = "protoMicroservice.k8SCluster")
    @Mapping(target = "isNonMigratable", source = "protoMicroservice.nonMigratable")
    @Mapping(target = "affinities", expression = "java(mapToDomainAffinities(protoMicroservice))")
    @Mapping(target = "dataExchangedWithServices", expression = "java(mapProtoToDomainDataExchangeMap(protoMicroservice))")
    Microservice toDomainModel(MigrationActionOuterClass.Microservice protoMicroservice);

    @Mapping(target = "k8sCluster", source = "k8SCluster")
    @Mapping(target = "isNonMigratable", source = "nonMigratable")
    @Mapping(target = "affinities", expression = "java(mapToDomainAffinities(protoMicroservice))")
    @Mapping(target = "dataExchangedWithServices", expression = "java(mapProtoToDomainDataExchangeMap(protoMicroservice))")
    Microservice toDomainModel(EvaluateMigrationProposalOuterClass.Microservice protoMicroservice);

    default void mapAffinities(MigrationActionOuterClass.Microservice.Builder builder, Map<Microservice, Double> affinities) {
        if (affinities != null) {
            builder.putAllAffinities(
                    affinities.entrySet().stream()
                            .collect(Collectors.toMap(
                                    entry -> entry.getKey().getId(),
                                    entry -> {
                                        MigrationActionOuterClass.K8sCluster k8sCluster = MigrationActionOuterClass.K8sCluster.newBuilder()
                                                .setId(entry.getKey().getK8sCluster().getId())
                                                .setLocation(entry.getKey().getK8sCluster().getLocation())
                                                .build();
                                        return MigrationActionOuterClass.Affinity.newBuilder()
                                                .setName(entry.getKey().getName())
                                                .setValue(entry.getValue())
                                                .setK8SCluster(k8sCluster)
                                                .build();
                                    }
                            ))
            );
        }
    }

    // Default method for handling data exchanged with services
    default void mapDataExchanged(MigrationActionOuterClass.Microservice.Builder builder, Map<Microservice, Double> dataExchanged) {
        if (dataExchanged != null) {
            builder.putAllDataExchangedWithServices(
                    dataExchanged.entrySet().stream()
                            .collect(Collectors.toMap(
                                    entry -> entry.getKey().getId(), // Convert Microservice key to String
                                    Map.Entry::getValue
                            ))
            );
        }
    }

    default MigrationActionOuterClass.Microservice toProto(Microservice microservice) {
        if (microservice == null) {
            return null;
        }

        MigrationActionOuterClass.Microservice.Builder builder = MigrationActionOuterClass.Microservice.newBuilder();

        // Map standard fields
        builder.setId(microservice.getId())
                .setName(microservice.getName())
                .setNonMigratable(microservice.isNonMigratable())
                .setCpuUsage(microservice.getCpuUsage())
                .setMemoryUsage(microservice.getMemoryUsage());

        if (microservice.getK8sCluster() != null) {
            builder.setK8SCluster(MigrationActionOuterClass.K8sCluster.newBuilder()
                    .setId(microservice.getK8sCluster().getId())
                    .setLocation(microservice.getK8sCluster().getLocation())
                    .build());
        }

        // Map custom fields
        mapAffinities(builder, microservice.getAffinities());
        mapDataExchanged(builder, microservice.getDataExchangedWithServices());

        return builder.build();
    }
    //-----

    default Map<Microservice, Double> mapToDomainAffinities(MigrationActionOuterClass.Microservice protoMicroservice) {
        if (protoMicroservice.getAffinitiesMap() == null) {
            return new HashMap<>(); // Use a mutable map
        }

        return protoMicroservice.getAffinitiesMap().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> buildMicroserviceAffinityKey(entry, protoMicroservice), // Build full Microservice object
                        entry -> entry.getValue().getValue(),
                        (existing, replacement) -> existing, // Handle duplicates if any
                        HashMap::new // Ensure the resulting map is mutable
                ));
    }

    default Map<Microservice, Double> mapToDomainAffinities(EvaluateMigrationProposalOuterClass.Microservice protoMicroservice) {
        if (protoMicroservice.getAffinitiesMap() == null) {
            return new HashMap<>(); // Use a mutable map
        }

        return protoMicroservice.getAffinitiesMap().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> buildMicroserviceAffinityKey(entry.getKey(), protoMicroservice), // Build full Microservice object
                        Map.Entry::getValue,
                        (existing, replacement) -> existing, // Handle duplicates if any
                        HashMap::new // Ensure the resulting map is mutable
                ));
    }


    default Map<Microservice, MigrationActionOuterClass.Affinity> mapToProtoAffinities(MigrationActionOuterClass.Microservice protoMicroservice) {
        if (protoMicroservice.getAffinitiesMap() == null) {
            return new HashMap<>(); // Use a mutable map
        }

        return protoMicroservice.getAffinitiesMap().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> buildMicroserviceAffinityKey(protoMicroservice), // Build full Microservice object
                        Map.Entry::getValue,
                        (existing, replacement) -> existing, // Handle duplicates if any
                        HashMap::new // Ensure the resulting map is mutable
                ));
    }

    // Mapping Protobuf dataExchangedWithServices map to Domain model
    default Map<Microservice, Double> mapProtoToDomainDataExchangeMap(MigrationActionOuterClass.Microservice protoMicroservice) {
        if (protoMicroservice.getDataExchangedWithServicesMap() == null) {
            return Map.of();
        }

        return protoMicroservice.getDataExchangedWithServicesMap().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> buildMicroserviceDataExchangeKey(entry.getKey()), // Build full Microservice object
                        Map.Entry::getValue
                ));
    }

    default Map<Microservice, Double> mapProtoToDomainDataExchangeMap(EvaluateMigrationProposalOuterClass.Microservice protoMicroservice) {
        if (protoMicroservice.getDataExchangedWithServicesMap() == null) {
            return Map.of();
        }

        return protoMicroservice.getDataExchangedWithServicesMap().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> buildMicroserviceAffinityKey(entry.getKey(), protoMicroservice), // Build full Microservice object
                        Map.Entry::getValue
                ));
    }

    private Microservice buildMicroserviceAffinityKey(MigrationActionOuterClass.Microservice protoMicroservice) {
        Microservice microservice = Microservice.builder()
                .id(protoMicroservice.getId())
                .build();

        Map<Microservice, MigrationActionOuterClass.Affinity> affinitiesInMicroservice = protoMicroservice
                .getAffinitiesMap()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        key -> Microservice.builder()
                                .id(key.getKey())
                                .name(key.getValue().getName())
                                .k8sCluster(new K8sCluster(
                                        key.getValue().getK8SCluster().getId(),
                                        key.getValue().getK8SCluster().getLocation()
                                ))
                                .build(),
                        Map.Entry::getValue
                ));

        Map<Microservice, Double> affinityMap = affinitiesInMicroservice.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().getValue()
                ));

        microservice.setAffinities(affinityMap);

        return microservice;
    }

    default Microservice buildMicroserviceDataExchangeKey(String microserviceId) {
        return Microservice.builder()
                .id(microserviceId)
                .build();
    }

    default Microservice buildMicroserviceAffinityKey(String microserviceId, EvaluateMigrationProposalOuterClass.Microservice protoMicroservice) {
        Microservice microservice = Microservice.builder()
                .id(microserviceId)
                .build();

        Map<Microservice, Double> affinitiesInMicroservice = protoMicroservice
                .getAffinitiesMap()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        key -> Microservice.builder()
                                .id(key.getKey())
                                .name(protoMicroservice.getName())
                                .isNonMigratable(protoMicroservice.getNonMigratable())
                                .k8sCluster(new K8sCluster(
                                        protoMicroservice.getK8SCluster().getId(),
                                        protoMicroservice.getK8SCluster().getLocation()
                                ))
                                .cpuUsage(protoMicroservice.getCpuUsage())
                                .memoryUsage(protoMicroservice.getMemoryUsage())
                                .build(),
                        Map.Entry::getValue
                ));

        microservice.setAffinities(affinitiesInMicroservice);

        return microservice;
    }

    default Microservice buildMicroserviceAffinityKey(Map.Entry<String, MigrationActionOuterClass.Affinity> affinityMicroservice, MigrationActionOuterClass.Microservice protoMicroservice) {
        MigrationActionOuterClass.K8sCluster k8SClusterProto = affinityMicroservice.getValue().getK8SCluster();

        return Microservice.builder()
                .id(affinityMicroservice.getKey())
                .name(affinityMicroservice.getKey())
                .k8sCluster(new K8sCluster(k8SClusterProto.getId(), k8SClusterProto.getLocation()))
                .build();
    }
}
