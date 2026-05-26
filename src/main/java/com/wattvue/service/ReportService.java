package com.wattvue.service;

import com.wattvue.dto.CleaningAnalysisRequest;
import com.wattvue.dto.EmailReportRequest;
import com.wattvue.dto.LossCalculationRequest;
import com.wattvue.model.Customer;
import com.wattvue.model.Report;
import com.wattvue.repository.CustomerRepository;
import com.wattvue.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final CustomerRepository customerRepository;
    private final ReportRepository reportRepository;
    private final AnalyticsService analyticsService;
    private final PdfGenerationService pdfService;
    private final EmailService emailService;

    public Report generatePerformanceReport(Long customerId) throws Exception {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found"));
        Map<String, Object> roi = analyticsService.getROIAnalysis(customerId);
        File pdf = pdfService.generatePerformanceReport(customer, roi);
        return saveReport(customer, "Performance Report - " + customer.getName(), "PERFORMANCE", pdf);
    }

    public Report generateLossReport(LossCalculationRequest req) throws Exception {
        Customer customer = customerRepository.findById(req.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Customer not found"));
        Map<String, Object> loss = analyticsService.calculateLoss(req);
        File pdf = pdfService.generateLossReport(customer, loss);
        return saveReport(customer, "Loss Report - " + customer.getName(), "LOSS", pdf);
    }

    public Report generateCleaningReport(CleaningAnalysisRequest req) throws Exception {
        Customer customer = customerRepository.findById(req.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Customer not found"));
        Map<String, Object> cleaning = analyticsService.analyzeCleaning(req);
        File pdf = pdfService.generateCleaningReport(customer, cleaning);
        return saveReport(customer, "Cleaning Impact - " + customer.getName(), "CLEANING", pdf);
    }

    public Report generateUtilityReport(Long customerId) throws Exception {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found"));
        Map<String, Object> util = analyticsService.validateUtilityExport(customerId);
        Map<String, Object> cmp  = analyticsService.compareUtilityVsSystem(customerId);
        File pdf = pdfService.generateUtilityReport(customer, util, cmp);
        return saveReport(customer, "Utility Validation - " + customer.getName(), "UTILITY", pdf);
    }

    /**
     * Send a report by email.
     * If the saved PDF file no longer exists on disk (e.g. after a Railway redeploy),
     * we regenerate it fresh before attaching so the email always has the attachment.
     */
    public Report sendReportEmail(Long reportId, EmailReportRequest req) throws Exception {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Report not found"));

        File pdfFile = resolveOrRegeneratePdf(report);

        emailService.sendReportEmail(
                req.getToEmail(),
                req.getCc(),
                req.getSubject(),
                req.getBody(),
                pdfFile.getAbsolutePath(),
                pdfFile.getName()
        );

        report.setEmailStatus("SENT");
        report.setSentToEmail(req.getToEmail());
        report.setSentAt(LocalDateTime.now());
        return reportRepository.save(report);
    }

    public File getReportFile(Long reportId) throws Exception {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Report not found"));
        return resolveOrRegeneratePdf(report);
    }

    /**
     * Returns the PDF file for a report.
     * If the file no longer exists on disk, regenerates it from database data.
     */
    private File resolveOrRegeneratePdf(Report report) throws Exception {
        File file = new File(report.getFilePath());
        if (file.exists() && file.length() > 0) {
            return file;
        }

        log.warn("PDF not found at {}. Regenerating...", report.getFilePath());

        Customer customer = report.getCustomer();
        File regenerated;

        switch (report.getReportType()) {
            case "PERFORMANCE" -> {
                Map<String, Object> roi = analyticsService.getROIAnalysis(customer.getId());
                regenerated = pdfService.generatePerformanceReport(customer, roi);
            }
            case "LOSS" -> {
                // Regenerate with stored data — use basic loss request
                LossCalculationRequest req = new LossCalculationRequest();
                req.setCustomerId(customer.getId());
                Map<String, Object> loss = analyticsService.calculateLoss(req);
                regenerated = pdfService.generateLossReport(customer, loss);
            }
            case "CLEANING" -> {
                CleaningAnalysisRequest req = new CleaningAnalysisRequest();
                req.setCustomerId(customer.getId());
                Map<String, Object> cleaning = analyticsService.analyzeCleaning(req);
                regenerated = pdfService.generateCleaningReport(customer, cleaning);
            }
            case "UTILITY" -> {
                Map<String, Object> util = analyticsService.validateUtilityExport(customer.getId());
                Map<String, Object> cmp  = analyticsService.compareUtilityVsSystem(customer.getId());
                regenerated = pdfService.generateUtilityReport(customer, util, cmp);
            }
            default -> {
                Map<String, Object> roi = analyticsService.getROIAnalysis(customer.getId());
                regenerated = pdfService.generatePerformanceReport(customer, roi);
            }
        }

        // Update stored path so next call finds it
        report.setFilePath(regenerated.getAbsolutePath());
        report.setFileSizeBytes(regenerated.length());
        reportRepository.save(report);

        return regenerated;
    }

    private Report saveReport(Customer customer, String title, String type, File pdf) {
        Report report = Report.builder()
                .customer(customer)
                .reportTitle(title)
                .reportType(type)
                .filePath(pdf.getAbsolutePath())
                .fileSizeBytes(pdf.length())
                .emailStatus("PENDING")
                .build();
        return reportRepository.save(report);
    }
}