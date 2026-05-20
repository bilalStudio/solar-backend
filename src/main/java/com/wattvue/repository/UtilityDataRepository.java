package com.wattvue.repository;

import com.wattvue.model.UtilityData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UtilityDataRepository extends JpaRepository<UtilityData, Long> {

    List<UtilityData> findByCustomerIdOrderByTimestampAsc(Long customerId);

    @Query("SELECT u FROM UtilityData u WHERE u.customer.id = :customerId " +
           "AND u.timestamp BETWEEN :start AND :end ORDER BY u.timestamp ASC")
    List<UtilityData> findByCustomerAndRange(@Param("customerId") Long customerId,
                                              @Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(u) FROM UtilityData u WHERE u.customer.id = :customerId AND u.isExport = true")
    long countExportReadings(@Param("customerId") Long customerId);

    long countByCustomerId(Long customerId);
}
