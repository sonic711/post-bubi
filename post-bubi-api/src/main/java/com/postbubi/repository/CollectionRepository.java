package com.postbubi.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.postbubi.domain.CollectionEntity;

public interface CollectionRepository extends JpaRepository<CollectionEntity, Long> {
}
