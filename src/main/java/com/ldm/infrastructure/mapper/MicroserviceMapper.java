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
    @Mapping(target = "affinities", expression = "java(mapProtoToDomainAffinities(protoMicroservice))")
    @Mapping(target = "dataExchangedWithServices", expression = "java(mapProtoToDomainDataExchanged(protoMicroservice))")
    Microservice toDomainModel(MigrationActionOuterClass.Microservice protoMicroservice);

    @Mapping(target = "k8sCluster", source = "k8SCluster")
    @Mapping(target = "isNonMigratable", source = "nonMigratable")
    @Mapping(target = "affinities", expression = "java(mapProtoToDomainAffinities(protoMicroservice))")
    @Mapping(target = "dataExchangedWithServices", expression = "java(mapProtoToDomainDataExchanged(protoMicroservice))")
    Microservice toDomainModel(EvaluateMigrationProposalOuterClass.Microservice protoMicroservice);

//    @Mapping(target = "k8SCluster", source = "microservice.k8sCluster")
//    @Mapping(target = "nonMigratable", source = "microservice.nonMigratable")
//    @Mapping(target = "affinities", expression = "java(mapDomainToProtoAffinities(microservice.getAffinities()))")
//    @Mapping(target = "dataExchangedWithServices", expression = "java(mapDomainToProtoDataExchanged(microservice.getDataExchangedWithServices()))")
//    MigrationActionOuterClass.Microservice toProto(Microservice microservice);

//    @Mapping(target = "k8SCluster", source = "microservice.k8sCluster")
//    @Mapping(target = "nonMigratable", source = "microservice.nonMigratable")
//    MigrationActionOuterClass.Microservice toProto(Microservice microservice);

//    @Mapping(target = "k8SCluster", source = "k8sCluster")
//    @Mapping(target = "nonMigratable", source = "nonMigratable")
//    @Mapping(target = "affinities", ignore = true) // Handled via default method
//    @Mapping(target = "dataExchangedWithServices", ignore = true) // Handled via default method
//    MigrationActionOuterClass.Microservice toProto(Microservice microservice);

    default void mapAffinities(MigrationActionOuterClass.Microservice.Builder builder, Map<Microservice, Double> affinities) {
        if (affinities != null) {
            builder.putAllAffinities(
                    affinities.entrySet().stream()
                            .collect(Collectors.toMap(
                                    entry -> entry.getKey().getId(),
                                    Map.Entry::getValue
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



    default Map<Microservice, Double> mapProtoToDomainAffinities(EvaluateMigrationProposalOuterClass.Microservice protoMicroservice) {
        if (protoMicroservice.getAffinitiesMap() == null) {
            return new HashMap<>(); // Use a mutable map
        }

        return protoMicroservice.getAffinitiesMap().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> buildMicroservice(entry.getKey(), protoMicroservice), // Build full Microservice object
                        Map.Entry::getValue,
                        (existing, replacement) -> existing, // Handle duplicates if any
                        HashMap::new // Ensure the resulting map is mutable
                ));
    }

    default Map<Microservice, Double> mapProtoToDomainAffinities(MigrationActionOuterClass.Microservice protoMicroservice) {
        if (protoMicroservice.getAffinitiesMap() == null) {
            return new HashMap<>(); // Use a mutable map
        }

        return protoMicroservice.getAffinitiesMap().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> buildMicroservice(entry.getKey(), protoMicroservice), // Build full Microservice object
                        Map.Entry::getValue,
                        (existing, replacement) -> existing, // Handle duplicates if any
                        HashMap::new // Ensure the resulting map is mutable
                ));
    }


//    default Map<Microservice, Double> mapProtoToDomainAffinities(EvaluateMigrationProposalOuterClass.Microservice protoMicroservice) {
//        if (protoMicroservice.getAffinitiesMap() == null) {
//            return Map.of();
//        }
//
//        return protoMicroservice.getAffinitiesMap().entrySet().stream()
//                .collect(Collectors.toMap(
//                        entry -> buildMicroservice(entry.getKey(), protoMicroservice), // Build full Microservice object
//                        Map.Entry::getValue
//                ));
//    }
//
//
//    // Mapping Protobuf affinities map to Domain model affinities map
//    default Map<Microservice, Double> mapProtoToDomainAffinities(MigrationActionOuterClass.Microservice protoMicroservice) {
//        if (protoMicroservice.getAffinitiesMap() == null) {
//            return Map.of();
//        }
//
//        return protoMicroservice.getAffinitiesMap().entrySet().stream()
//                .collect(Collectors.toMap(
//                        entry -> buildMicroservice(entry.getKey(), protoMicroservice), // Build full Microservice object
//                        Map.Entry::getValue
//                ));
//    }

    // Mapping Domain model affinities map to Protobuf affinities map
//    default Map<String, Double> mapDomainToProtoAffinities(Map<Microservice, Double> affinities) {
//        if (affinities == null) {
//            return Map.of();
//        }
//
//        Map<String, Double> affinityMap = affinities.entrySet().stream()
//                .collect(Collectors.toMap(
//                        entry -> entry.getKey().getId(), // Map Microservice key to String
//                        Map.Entry::getValue
//                ));
//        return affinityMap;
//    }

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

    default Map<Microservice, Double> mapProtoToDomainDataExchanged(EvaluateMigrationProposalOuterClass.Microservice protoMicroservice) {
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
//    default Map<String, Double> mapDomainToProtoDataExchanged(Map<Microservice, Double> dataExchangedWithServices) {
//        if (dataExchangedWithServices == null) {
//            return Map.of();
//        }
//
//        return dataExchangedWithServices.entrySet().stream()
//                .collect(Collectors.toMap(
//                        entry -> entry.getKey().getId(), // Map Microservice key to String
//                        Map.Entry::getValue
//                ));
//    }

    // Build a full Microservice object for affinities or dataExchangedWithServices keys
    default Microservice buildMicroservice(String microserviceId, MigrationActionOuterClass.Microservice protoMicroservice) {
        Microservice microservice = Microservice.builder()
                .id(microserviceId)
//                .name(protoMicroservice.getName())
//                .isNonMigratable(protoMicroservice.getNonMigratable())
//                .k8sCluster(new K8sCluster(
//                        protoMicroservice.getK8SCluster().getId(),
//                        protoMicroservice.getK8SCluster().getLocation()
//                ))
//                .cpuUsage(protoMicroservice.getCpuUsage())
//                .memoryUsage(protoMicroservice.getMemoryUsage())
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
//        Microservice microservice = Microservice.builder()
//                .id(microserviceId)
//                .name(protoMicroservice.getName())
//                .isNonMigratable(protoMicroservice.getNonMigratable())
//                .k8sCluster(new K8sCluster(
//                        protoMicroservice.getK8SCluster().getId(),
//                        protoMicroservice.getK8SCluster().getLocation()
//                ))
//                .cpuUsage(protoMicroservice.getCpuUsage())
//                .memoryUsage(protoMicroservice.getMemoryUsage())
//                .build();
//
//        Map<Microservice, Double> affinitiesInMicroservice = protoMicroservice
//                .getAffinitiesMap()
//                .entrySet()
//                .stream()
//                .collect(Collectors.toMap(
//                        key ->microservice,
//                        Map.Entry::getValue
//                ));
//
//        microservice.setAffinities(affinitiesInMicroservice);
//
//        return microservice;
    }

    default Microservice buildMicroservice(String microserviceId, EvaluateMigrationProposalOuterClass.Microservice protoMicroservice) {
        Microservice microservice = Microservice.builder()
                .id(microserviceId)
//                .name(protoMicroservice.getName())
//                .isNonMigratable(protoMicroservice.getNonMigratable())
//                .k8sCluster(new K8sCluster(
//                        protoMicroservice.getK8SCluster().getId(),
//                        protoMicroservice.getK8SCluster().getLocation()
//                ))
//                .cpuUsage(protoMicroservice.getCpuUsage())
//                .memoryUsage(protoMicroservice.getMemoryUsage())
                .build();

        Double affinityValue = protoMicroservice.getAffinitiesMap().get(microserviceId);

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
}
