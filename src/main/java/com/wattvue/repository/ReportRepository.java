package com.wattvue.repository;

import com.wattvue.model.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findByCustomerIdOrderByGeneratedAtDesc(Long customerId);
    long countByEmailStatus(String emailStatus);
    List<Report> findAllByOrderByGeneratedAtDesc();
}
