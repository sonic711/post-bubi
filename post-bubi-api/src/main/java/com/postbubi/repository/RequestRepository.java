package com.postbubi.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.postbubi.domain.RequestEntity;

public interface RequestRepository extends JpaRepository<RequestEntity, Long> {

    List<RequestEntity> findByCollectionIdOrderBySortOrderAscIdAsc(Long collectionId);

    boolean existsByCollectionIdAndFolderId(Long collectionId, Long folderId);

    void deleteByCollectionId(Long collectionId);

    void deleteByFolderId(Long folderId);
}
