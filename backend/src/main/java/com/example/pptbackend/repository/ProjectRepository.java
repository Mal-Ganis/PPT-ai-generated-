package com.example.pptbackend.repository;

import com.example.pptbackend.model.Project;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByOwnerUserIdOrOwnerUserIdIsNull(Long ownerUserId, Sort sort);
}
