package com.postbubi.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.postbubi.domain.GrpcBatchItemEntity;

public interface GrpcBatchItemRepository extends JpaRepository<GrpcBatchItemEntity, Long> {

    List<GrpcBatchItemEntity> findByBatchRunIdOrderBySequenceNumberAsc(Long batchRunId);

    Page<GrpcBatchItemEntity> findByBatchRunIdOrderBySequenceNumberAsc(Long batchRunId, Pageable pageable);

    long deleteByBatchRunIdIn(List<Long> batchRunIds);
}
