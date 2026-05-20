package com.wattvue.repository;

import com.wattvue.model.Upload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface UploadRepository extends JpaRepository<Upload, Long> {
    List<Upload> findByCustomerIdOrderByUploadedAtDesc(Long customerId);
}
