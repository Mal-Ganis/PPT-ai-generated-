package com.example.pptbackend.repository;

import com.example.pptbackend.model.EditorAccessRequest;
import com.example.pptbackend.model.EditorAccessRequestStatus;
import com.example.pptbackend.model.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EditorAccessRequestRepository extends JpaRepository<EditorAccessRequest, Long> {
    Optional<EditorAccessRequest> findTopByUserOrderByCreatedAtDesc(User user);

    List<EditorAccessRequest> findByStatusOrderByCreatedAtAsc(EditorAccessRequestStatus status);

    boolean existsByUserAndStatus(User user, EditorAccessRequestStatus status);
}
