package com.postbubi.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.postbubi.domain.HttpBatchItemEntity;

public interface HttpBatchItemRepository extends JpaRepository<HttpBatchItemEntity, Long> {

    List<HttpBatchItemEntity> findByBatchRunIdOrderBySequenceNumberAsc(Long batchRunId);

    Page<HttpBatchItemEntity> findByBatchRunIdOrderBySequenceNumberAsc(Long batchRunId, Pageable pageable);
}
