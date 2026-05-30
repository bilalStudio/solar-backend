package com.wattvue.service;

import com.wattvue.dto.FieldDocumentRequest;
import com.wattvue.dto.FieldDocumentResponse;
import com.wattvue.model.*;
import com.wattvue.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FieldDocumentService {

    private final FieldDocumentRepository documentRepository;
    private final DocFieldValueRepository fieldValueRepository;
    private final DocPhotoRepository photoRepository;
    private final FieldUserRepository fieldUserRepository;
    private final CustomerRepository customerRepository;
    private final S3Service s3Service;

    // ─── Create new document (DRAFT) ────────────────────────────────────────────
    @Transactional
    public FieldDocumentResponse createDocument(Long technicianId, FieldDocumentRequest req) {
        FieldUser technician = fieldUserRepository.findById(technicianId)
                .orElseThrow(() -> new RuntimeException("Technician not found"));

        Customer customer = null;
        if (req.getCustomerId() != null) {
            customer = customerRepository.findById(req.getCustomerId()).orElse(null);
        }

        FieldDocument doc = FieldDocument.builder()
                .docType(req.getDocType())
                .customer(customer)
                .technician(technician)
                .status("DRAFT")
                .notes(req.getNotes())
                .build();
        doc = documentRepository.save(doc);

        // Save field values if provided
        if (req.getFieldValues() != null) {
            saveFieldValues(doc, req.getFieldValues());
        }

        return toResponse(documentRepository.findById(doc.getId()).orElseThrow());
    }

    // ─── Auto-save document (update fields) ─────────────────────────────────────
    @Transactional
    public FieldDocumentResponse autoSave(Long documentId, Long technicianId, FieldDocumentRequest req) {
        FieldDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        if (!doc.getTechnician().getId().equals(technicianId)) {
            throw new RuntimeException("Access denied");
        }

        doc.setNotes(req.getNotes());
        doc.setUpdatedAt(LocalDateTime.now());

        if (req.getCustomerId() != null) {
            Customer customer = customerRepository.findById(req.getCustomerId()).orElse(null);
            doc.setCustomer(customer);
        }

        documentRepository.save(doc);

        // Delete old values and replace with new ones
        if (req.getFieldValues() != null) {
            fieldValueRepository.deleteByDocumentId(documentId);
            saveFieldValues(doc, req.getFieldValues());
        }

        return toResponse(documentRepository.findById(doc.getId()).orElseThrow());
    }

    // ─── Submit document ─────────────────────────────────────────────────────────
    @Transactional
    public FieldDocumentResponse submitDocument(Long documentId, Long technicianId) {
        FieldDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        if (!doc.getTechnician().getId().equals(technicianId)) {
            throw new RuntimeException("Access denied");
        }

        doc.setStatus("SUBMITTED");
        doc.setSubmittedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(doc);

        return toResponse(doc);
    }

    // ─── Upload photo for a field ────────────────────────────────────────────────
    @Transactional
    public FieldDocumentResponse.PhotoResponse uploadPhoto(
            Long documentId, Long technicianId, String fieldKey,
            String caption, MultipartFile file) throws Exception {

        FieldDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        if (!doc.getTechnician().getId().equals(technicianId)) {
            throw new RuntimeException("Access denied");
        }

        // Upload to S3
        String s3Url = s3Service.uploadFieldPhoto(file, documentId, fieldKey);

        DocPhoto photo = DocPhoto.builder()
                .document(doc)
                .fieldKey(fieldKey)
                .s3Url(s3Url)
                .fileName(file.getOriginalFilename())
                .caption(caption)
                .build();
        photo = photoRepository.save(photo);

        return FieldDocumentResponse.PhotoResponse.builder()
                .id(photo.getId())
                .fieldKey(photo.getFieldKey())
                .s3Url(photo.getS3Url())
                .fileName(photo.getFileName())
                .caption(photo.getCaption())
                .build();
    }

    // ─── Toggle field visibility ─────────────────────────────────────────────────
    @Transactional
    public void toggleFieldVisibility(Long documentId, Long technicianId, String fieldKey, boolean visible) {
        FieldDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        if (!doc.getTechnician().getId().equals(technicianId)) {
            throw new RuntimeException("Access denied");
        }
        List<DocFieldValue> values = fieldValueRepository.findByDocumentIdOrderBySortOrder(documentId);
        values.stream()
                .filter(v -> v.getFieldKey().equals(fieldKey))
                .forEach(v -> {
                    v.setIsVisible(visible);
                    fieldValueRepository.save(v);
                });
    }

    // ─── Get documents by technician ─────────────────────────────────────────────
    public List<FieldDocumentResponse> getByTechnician(Long technicianId) {
        return documentRepository.findByTechnicianIdOrderByCreatedAtDesc(technicianId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ─── Get single document ─────────────────────────────────────────────────────
    public FieldDocumentResponse getById(Long documentId, Long technicianId) {
        FieldDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        if (!doc.getTechnician().getId().equals(technicianId)) {
            throw new RuntimeException("Access denied");
        }
        return toResponse(doc);
    }

    // ─── Get all documents (admin) ────────────────────────────────────────────────
    public List<FieldDocumentResponse> getAll() {
        return documentRepository.findAll().stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────
    private void saveFieldValues(FieldDocument doc, List<FieldDocumentRequest.FieldValueDto> dtos) {
        List<DocFieldValue> values = dtos.stream().map(dto ->
            DocFieldValue.builder()
                .document(doc)
                .fieldKey(dto.getFieldKey())
                .fieldLabel(dto.getFieldLabel())
                .fieldValue(dto.getFieldValue())
                .fieldType(dto.getFieldType() != null ? dto.getFieldType() : "TEXT")
                .isVisible(dto.getIsVisible() != null ? dto.getIsVisible() : true)
                .sortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0)
                .build()
        ).collect(Collectors.toList());
        fieldValueRepository.saveAll(values);
    }

    private String docTypeLabel(String type) {
        return switch (type) {
            case "HEALTH_CHECKLIST" -> "Health System Checklist";
            case "SITE_SURVEY"      -> "Site Survey / Assessment";
            case "INSTALL_SERVICE"  -> "Install / Service Document";
            case "CREW_UPDATE"      -> "Crew Update";
            default -> type;
        };
    }

    public FieldDocumentResponse toResponse(FieldDocument doc) {
        List<FieldDocumentResponse.FieldValueResponse> fieldValues =
            fieldValueRepository.findByDocumentIdOrderBySortOrder(doc.getId())
                .stream().map(v -> FieldDocumentResponse.FieldValueResponse.builder()
                    .id(v.getId())
                    .fieldKey(v.getFieldKey())
                    .fieldLabel(v.getFieldLabel())
                    .fieldValue(v.getFieldValue())
                    .fieldType(v.getFieldType())
                    .isVisible(v.getIsVisible())
                    .sortOrder(v.getSortOrder())
                    .build()
                ).collect(Collectors.toList());

        List<FieldDocumentResponse.PhotoResponse> photos =
            photoRepository.findByDocumentIdOrderByUploadedAt(doc.getId())
                .stream().map(p -> FieldDocumentResponse.PhotoResponse.builder()
                    .id(p.getId())
                    .fieldKey(p.getFieldKey())
                    .s3Url(p.getS3Url())
                    .fileName(p.getFileName())
                    .caption(p.getCaption())
                    .build()
                ).collect(Collectors.toList());

        return FieldDocumentResponse.builder()
                .id(doc.getId())
                .docType(doc.getDocType())
                .docTypeLabel(docTypeLabel(doc.getDocType()))
                .customerId(doc.getCustomer() != null ? doc.getCustomer().getId() : null)
                .customerName(doc.getCustomer() != null ? doc.getCustomer().getName() : null)
                .technicianId(doc.getTechnician() != null ? doc.getTechnician().getId() : null)
                .technicianName(doc.getTechnician() != null ? doc.getTechnician().getName() : null)
                .status(doc.getStatus())
                .pdfUrl(doc.getPdfUrl())
                .notes(doc.getNotes())
                .submittedAt(doc.getSubmittedAt())
                .createdAt(doc.getCreatedAt())
                .fieldValues(fieldValues)
                .photos(photos)
                .build();
    }
}
