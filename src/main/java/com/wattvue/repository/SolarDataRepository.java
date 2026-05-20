package com.wattvue.repository;

import com.wattvue.model.SolarData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SolarDataRepository extends JpaRepository<SolarData, Long> {

    List<SolarData> findByCustomerIdOrderByMonthAsc(Long customerId);

    List<SolarData> findByUploadId(Long uploadId);

    @Query("SELECT s FROM SolarData s WHERE s.customer.id = :customerId " +
           "AND s.dataDate BETWEEN :start AND :end ORDER BY s.dataDate ASC")
    List<SolarData> findByCustomerAndDateRange(@Param("customerId") Long customerId,
                                                @Param("start") LocalDate start,
                                                @Param("end") LocalDate end);

    @Query("SELECT SUM(s.actualKwh) FROM SolarData s WHERE s.customer.id = :customerId")
    Double sumActualKwhByCustomerId(@Param("customerId") Long customerId);

    @Query("SELECT SUM(s.estimatedKwh) FROM SolarData s WHERE s.customer.id = :customerId")
    Double sumEstimatedKwhByCustomerId(@Param("customerId") Long customerId);

    @Query("SELECT AVG(s.variancePct) FROM SolarData s")
    Double avgVariancePct();

    @Query("SELECT AVG(s.actualKwh) FROM SolarData s " +
           "WHERE s.customer.id = :customerId AND s.dataDate BETWEEN :start AND :end")
    Double avgActualKwhInRange(@Param("customerId") Long customerId,
                                @Param("start") LocalDate start,
                                @Param("end") LocalDate end);

    @Query("SELECT SUM(s.estimatedKwh) FROM SolarData s " +
           "WHERE s.customer.id = :customerId AND s.dataDate BETWEEN :start AND :end")
    Double sumEstimatedInRange(@Param("customerId") Long customerId,
                                @Param("start") LocalDate start,
                                @Param("end") LocalDate end);

    @Query("SELECT SUM(s.actualKwh) FROM SolarData s " +
           "WHERE s.customer.id = :customerId AND s.dataDate BETWEEN :start AND :end")
    Double sumActualInRange(@Param("customerId") Long customerId,
                             @Param("start") LocalDate start,
                             @Param("end") LocalDate end);
}
