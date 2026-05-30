package com.wattvue.repository;

import com.wattvue.model.DocFieldValue;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DocFieldValueRepository extends JpaRepository<DocFieldValue, Long> {
    List<DocFieldValue> findByDocumentIdOrderBySortOrder(Long documentId);
    void deleteByDocumentId(Long documentId);
}
