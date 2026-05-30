package com.wattvue.controller;

import com.wattvue.dto.ApiResponse;
import com.wattvue.dto.FieldDocumentRequest;
import com.wattvue.dto.FieldDocumentResponse;
import com.wattvue.service.FieldDocumentService;
import com.wattvue.service.FieldPdfService;
import com.wattvue.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/field/documents")
@RequiredArgsConstructor
public class FieldDocumentController {

    private final FieldDocumentService documentService;
    private final FieldPdfService pdfService;
    private final EmailService emailService;

    // ─── Create new document ─────────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<ApiResponse<FieldDocumentResponse>> create(
            @RequestHeader("X-Technician-Id") Long technicianId,
            @RequestBody FieldDocumentRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Document created",
                documentService.createDocument(technicianId, req)));
    }

    // ─── Auto-save document ──────────────────────────────────────────────────────
    @PutMapping("/{id}/autosave")
    public ResponseEntity<ApiResponse<FieldDocumentResponse>> autoSave(
            @PathVariable Long id,
            @RequestHeader("X-Technician-Id") Long technicianId,
            @RequestBody FieldDocumentRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Saved",
                documentService.autoSave(id, technicianId, req)));
    }

    // ─── Submit document ─────────────────────────────────────────────────────────
    @PostMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<FieldDocumentResponse>> submit(
            @PathVariable Long id,
            @RequestHeader("X-Technician-Id") Long technicianId) {
        return ResponseEntity.ok(ApiResponse.success("Document submitted",
                documentService.submitDocument(id, technicianId)));
    }

    // ─── Upload photo ────────────────────────────────────────────────────────────
    @PostMapping("/{id}/photos")
    public ResponseEntity<ApiResponse<FieldDocumentResponse.PhotoResponse>> uploadPhoto(
            @PathVariable Long id,
            @RequestHeader("X-Technician-Id") Long technicianId,
            @RequestParam("fieldKey") String fieldKey,
            @RequestParam(value = "caption", required = false) String caption,
            @RequestParam("file") MultipartFile file) throws Exception {
        return ResponseEntity.ok(ApiResponse.success("Photo uploaded",
                documentService.uploadPhoto(id, technicianId, fieldKey, caption, file)));
    }

    // ─── Toggle field visibility ─────────────────────────────────────────────────
    @PatchMapping("/{id}/fields/{fieldKey}/visibility")
    public ResponseEntity<ApiResponse<Void>> toggleVisibility(
            @PathVariable Long id,
            @PathVariable String fieldKey,
            @RequestHeader("X-Technician-Id") Long technicianId,
            @RequestParam boolean visible) {
        documentService.toggleFieldVisibility(id, technicianId, fieldKey, visible);
        return ResponseEntity.ok(ApiResponse.success("Visibility updated", null));
    }

    // ─── Get my documents ────────────────────────────────────────────────────────
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<FieldDocumentResponse>>> getMyDocuments(
            @RequestHeader("X-Technician-Id") Long technicianId) {
        return ResponseEntity.ok(ApiResponse.success("Documents retrieved",
                documentService.getByTechnician(technicianId)));
    }

    // ─── Get single document ─────────────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FieldDocumentResponse>> getById(
            @PathVariable Long id,
            @RequestHeader("X-Technician-Id") Long technicianId) {
        return ResponseEntity.ok(ApiResponse.success("Document retrieved",
                documentService.getById(id, technicianId)));
    }

    // ─── Get all documents (admin view) ──────────────────────────────────────────
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<FieldDocumentResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success("All documents",
                documentService.getAll()));
    }

    // ─── Generate PDF ────────────────────────────────────────────────────────────
    @PostMapping("/{id}/pdf")
    public ResponseEntity<ApiResponse<Map<String, String>>> generatePdf(
            @PathVariable Long id,
            @RequestHeader("X-Technician-Id") Long technicianId) throws Exception {
        FieldDocumentResponse doc = documentService.getById(id, technicianId);
        String pdfUrl = pdfService.generateFieldDocumentPdf(doc);
        return ResponseEntity.ok(ApiResponse.success("PDF generated",
                Map.of("pdfUrl", pdfUrl)));
    }

    // ─── Download PDF ────────────────────────────────────────────────────────────
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadPdf(
            @PathVariable Long id,
            @RequestHeader("X-Technician-Id") Long technicianId) throws Exception {
        FieldDocumentResponse doc = documentService.getById(id, technicianId);
        File pdf = pdfService.generateFieldDocumentPdfFile(doc);
        byte[] bytes = Files.readAllBytes(pdf.toPath());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + pdf.getName() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }

    // ─── Email PDF ───────────────────────────────────────────────────────────────
    @PostMapping("/{id}/email")
    public ResponseEntity<ApiResponse<Void>> emailDocument(
            @PathVariable Long id,
            @RequestHeader("X-Technician-Id") Long technicianId,
            @RequestBody Map<String, String> req) throws Exception {

        FieldDocumentResponse doc = documentService.getById(id, technicianId);
        File pdf = pdfService.generateFieldDocumentPdfFile(doc);

        emailService.sendReportEmail(
                req.get("toEmail"),
                req.getOrDefault("cc", ""),
                req.getOrDefault("subject", doc.getDocTypeLabel() + " — " + doc.getCustomerName()),
                req.getOrDefault("body", "Please find attached the field document."),
                pdf.getAbsolutePath(),
                pdf.getName()
        );

        return ResponseEntity.ok(ApiResponse.success("Email sent successfully", null));
    }
}
