package com.example.pptbackend.repository;

import com.example.pptbackend.model.InviteCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InviteCodeRepository extends JpaRepository<InviteCode, Long> {
    Optional<InviteCode> findByCodeIgnoreCase(String code);
}
