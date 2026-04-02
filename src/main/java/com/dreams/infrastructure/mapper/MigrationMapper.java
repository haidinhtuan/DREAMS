package com.dreams.infrastructure.mapper;

import com.google.protobuf.Timestamp;
import com.dreams.domain.model.MigrationAction;
import com.dreams.infrastructure.serialization.protobuf.MigrationActionOuterClass;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Mapper(componentModel = "cdi", uses = {MicroserviceMapper.class, K8sClusterMapper.class})
public interface MigrationMapper {

    @Mapping(target = "microservice", source = "protoMigrationAction.microservice")
    @Mapping(target = "targetK8sCluster", source = "protoMigrationAction.targetCluster")
    @Mapping(target = "suggestedAt", expression = "java(toLocalDateTime(protoMigrationAction.getSuggestedAt()))")
    @Mapping(target = "createdAt", expression = "java(toLocalDateTime(protoMigrationAction.getCreatedAt()))")
    MigrationAction toDomainModel(MigrationActionOuterClass.MigrationAction protoMigrationAction);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "microservice", source = "migrationAction.microservice")
    @Mapping(target = "targetCluster", source = "migrationAction.targetK8sCluster")
    @Mapping(target = "suggestedAt", expression = "java(toTimestamp(migrationAction.suggestedAt()))")
    @Mapping(target = "createdAt", expression = "java(toTimestamp(migrationAction.createdAt()))")
    MigrationActionOuterClass.MigrationAction toProto(MigrationAction migrationAction);

    default LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp != null ? LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos()), ZoneOffset.UTC) : null;
    }

    default Timestamp toTimestamp(LocalDateTime localDateTime) {
        return localDateTime != null ? Timestamp.newBuilder()
                .setSeconds(localDateTime.toInstant(ZoneOffset.UTC).getEpochSecond())
                .setNanos(localDateTime.toInstant(ZoneOffset.UTC).getNano())
                .build() : null;
    }
}


