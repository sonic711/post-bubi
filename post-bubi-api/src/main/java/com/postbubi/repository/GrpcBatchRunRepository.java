package com.postbubi.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.postbubi.domain.GrpcBatchProtocol;
import com.postbubi.domain.GrpcBatchRunEntity;
import com.postbubi.domain.HttpBatchRunStatus;

public interface GrpcBatchRunRepository extends JpaRepository<GrpcBatchRunEntity, Long> {

    Page<GrpcBatchRunEntity> findByProtocolOrderByCreatedAtDesc(GrpcBatchProtocol protocol, Pageable pageable);

    Page<GrpcBatchRunEntity> findByProtocolAndRequestIdOrderByCreatedAtDesc(GrpcBatchProtocol protocol, Long requestId, Pageable pageable);

    List<GrpcBatchRunEntity> findByProtocolAndRequestIdAndStatusNotOrderByCreatedAtDesc(
            GrpcBatchProtocol protocol,
            Long requestId,
            HttpBatchRunStatus status
    );
}
