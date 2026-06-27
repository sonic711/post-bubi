package com.postbubi.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.postbubi.domain.FolderEntity;

public interface FolderRepository extends JpaRepository<FolderEntity, Long> {

    List<FolderEntity> findByCollectionIdOrderBySortOrderAscIdAsc(Long collectionId);

    boolean existsByCollectionIdAndParentFolderId(Long collectionId, Long parentFolderId);

    void deleteByCollectionId(Long collectionId);
}
