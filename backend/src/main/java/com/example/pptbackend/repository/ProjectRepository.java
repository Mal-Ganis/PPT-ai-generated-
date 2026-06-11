package com.example.pptbackend.repository;

import com.example.pptbackend.model.Project;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByOwnerUserId(Long ownerUserId, Sort sort);

    List<Project> findByTemplateProjectTrue(Sort sort);

    @Modifying
    @Query("UPDATE Project p SET p.templateProject = true WHERE p.ownerUserId IS NULL AND p.templateProject = false")
    int markOrphanProjectsAsTemplate();
}
