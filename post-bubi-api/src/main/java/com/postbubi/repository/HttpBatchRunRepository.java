package com.postbubi.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.postbubi.domain.HttpBatchRunEntity;
import com.postbubi.domain.HttpBatchRunStatus;

public interface HttpBatchRunRepository extends JpaRepository<HttpBatchRunEntity, Long> {

    Page<HttpBatchRunEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<HttpBatchRunEntity> findByRequestIdOrderByCreatedAtDesc(Long requestId, Pageable pageable);

    List<HttpBatchRunEntity> findByRequestIdAndStatusNotOrderByCreatedAtDesc(Long requestId, HttpBatchRunStatus status);
}
