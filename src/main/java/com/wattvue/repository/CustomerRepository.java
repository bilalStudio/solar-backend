package com.wattvue.repository;

import com.wattvue.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findByStatus(String status);

    List<Customer> findByNameContainingIgnoreCase(String name);

    long countByStatus(String status);

    boolean existsByEmail(String email);
}
