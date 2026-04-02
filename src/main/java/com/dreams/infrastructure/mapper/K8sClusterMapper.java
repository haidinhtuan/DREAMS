package com.dreams.infrastructure.mapper;

import com.dreams.domain.model.K8sCluster;
import com.dreams.infrastructure.serialization.protobuf.EvaluateMigrationProposalOuterClass;
import com.dreams.infrastructure.serialization.protobuf.MigrationActionOuterClass;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "cdi")
public interface K8sClusterMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "location", source = "location")
    K8sCluster toDomainModel(MigrationActionOuterClass.K8sCluster protoK8sCluster);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "location", source = "location")
    K8sCluster toDomainModel(EvaluateMigrationProposalOuterClass.K8sCluster protoK8sCluster);

    default MigrationActionOuterClass.K8sCluster toMigrationActionOuterClassK8sClusterProto(K8sCluster k8sCluster) {
        if (k8sCluster == null) {
            return MigrationActionOuterClass.K8sCluster.getDefaultInstance();
        }
        return MigrationActionOuterClass.K8sCluster.newBuilder()
                .setId(k8sCluster.getId())
                .setLocation(k8sCluster.getLocation())
                .build();
    }

    default EvaluateMigrationProposalOuterClass.K8sCluster toEvaluateMigrationProposalOuterClassK8sClusterProto(K8sCluster k8sCluster) {
        if (k8sCluster == null) {
            return EvaluateMigrationProposalOuterClass.K8sCluster.getDefaultInstance();
        }
        return EvaluateMigrationProposalOuterClass.K8sCluster.newBuilder()
                .setId(k8sCluster.getId())
                .setLocation(k8sCluster.getLocation())
                .build();
    }
}
