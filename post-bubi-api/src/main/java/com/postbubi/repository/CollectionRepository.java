package com.postbubi.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

import com.postbubi.domain.CollectionEntity;

public interface CollectionRepository extends JpaRepository<CollectionEntity, Long> {

    List<CollectionEntity> findAllByOrderBySortOrderAscIdAsc();
}
