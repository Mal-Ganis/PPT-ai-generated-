package com.example.pptbackend.repository;

import com.example.pptbackend.model.Slide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SlideRepository extends JpaRepository<Slide, Long> {

    Optional<Slide> findByIdAndProject_Id(Long id, Long projectId);
}
