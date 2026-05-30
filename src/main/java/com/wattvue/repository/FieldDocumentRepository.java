package com.wattvue.repository;

import com.wattvue.model.FieldDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface FieldDocumentRepository extends JpaRepository<FieldDocument, Long> {
    List<FieldDocument> findByTechnicianIdOrderByCreatedAtDesc(Long technicianId);
    List<FieldDocument> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    List<FieldDocument> findByDocTypeOrderByCreatedAtDesc(String docType);

    @Query("SELECT d FROM FieldDocument d WHERE d.technician.id = :techId AND d.docType = :docType ORDER BY d.createdAt DESC")
    List<FieldDocument> findByTechnicianAndType(Long techId, String docType);
}
