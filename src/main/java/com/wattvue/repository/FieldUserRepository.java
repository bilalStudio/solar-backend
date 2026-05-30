package com.wattvue.repository;

import com.wattvue.model.FieldUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FieldUserRepository extends JpaRepository<FieldUser, Long> {
    Optional<FieldUser> findByEmail(String email);
    boolean existsByEmail(String email);
}
