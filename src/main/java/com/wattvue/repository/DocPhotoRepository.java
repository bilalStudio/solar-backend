package com.wattvue.repository;

import com.wattvue.model.DocPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DocPhotoRepository extends JpaRepository<DocPhoto, Long> {
    List<DocPhoto> findByDocumentIdOrderByUploadedAt(Long documentId);
    List<DocPhoto> findByDocumentIdAndFieldKey(Long documentId, String fieldKey);
}
