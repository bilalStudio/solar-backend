package com.wattvue.repository;

import com.wattvue.model.CleaningEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CleaningEventRepository extends JpaRepository<CleaningEvent, Long> {
    List<CleaningEvent> findByCustomerIdOrderByCleaningDateDesc(Long customerId);
}
