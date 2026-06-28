package com.postbubi.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.postbubi.domain.RequestHistoryEntity;

public interface RequestHistoryRepository extends JpaRepository<RequestHistoryEntity, Long> {

    List<RequestHistoryEntity> findTop50ByOrderByCreatedAtDesc();
}
