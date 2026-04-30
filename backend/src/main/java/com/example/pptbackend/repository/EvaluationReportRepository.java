package com.example.pptbackend.repository;

import com.example.pptbackend.model.EvaluationReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvaluationReportRepository extends JpaRepository<EvaluationReport, Long> {

    List<EvaluationReport> findByProjectIdOrderByEvaluationTimeDesc(Long projectId);
}
