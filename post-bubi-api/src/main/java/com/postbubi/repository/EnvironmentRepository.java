package com.postbubi.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.postbubi.domain.EnvironmentEntity;

public interface EnvironmentRepository extends JpaRepository<EnvironmentEntity, Long> {

    List<EnvironmentEntity> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
