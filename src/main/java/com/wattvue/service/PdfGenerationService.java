package com.wattvue.service;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.wattvue.model.Customer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfGenerationService {

    private static final DeviceRgb PRIMARY = new DeviceRgb(37, 161, 171);
    private static final DeviceRgb DARK = new DeviceRgb(30, 41, 59);
    private static final DeviceRgb LIGHT_GRAY = new DeviceRgb(248, 250, 252);

    private static final String OUTPUT_DIR = "generated-reports";

    private Path ensureOutputDir() throws Exception {
        Path dir = Paths.get(OUTPUT_DIR);
        if (!Files.exists(dir)) Files.createDirectories(dir);
        return dir;
    }

    private String filenameFor(Customer customer, String type) {
        String safe = customer.getName().replaceAll("[^a-zA-Z0-9]", "_");
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return String.format("%s_%s_%s.pdf", safe, type, ts);
    }

    // ─── Performance Report (ROI) ───────────────────────────────────────────────

    public File generatePerformanceReport(Customer customer, Map<String, Object> roi) throws Exception {
        Path dir = ensureOutputDir();
        String filename = filenameFor(customer, "performance");
        File file = dir.resolve(filename).toFile();

        try (PdfWriter writer = new PdfWriter(file);
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf)) {

            addHeader(doc, "Solar Performance Report");
            addCustomerInfo(doc, customer);

            doc.add(new Paragraph("Performance Summary")
                    .setFontSize(16).setBold().setFontColor(PRIMARY).setMarginTop(20));

            Table kpiTable = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                    .useAllAvailableWidth();

            kpiTable.addCell(kpiCell("Total Actual Production", roi.get("totalActualKwh") + " kWh"));
            kpiTable.addCell(kpiCell("Total Estimated Production", roi.get("totalEstimatedKwh") + " kWh"));
            kpiTable.addCell(kpiCell("Variance", roi.get("totalVarianceKwh") + " kWh"));
            kpiTable.addCell(kpiCell("Variance %", roi.get("totalVariancePct") + "%"));
            kpiTable.addCell(kpiCell("Performance", roi.get("performancePct") + "%"));
            kpiTable.addCell(kpiCell("Estimated Savings", "PKR " + roi.get("actualSavingsPKR")));

            doc.add(kpiTable);

            doc.add(new Paragraph("Monthly Breakdown")
                    .setFontSize(16).setBold().setFontColor(PRIMARY).setMarginTop(24));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> monthly = (List<Map<String, Object>>) roi.get("monthlyData");

            if (monthly != null && !monthly.isEmpty()) {
                Table table = new Table(UnitValue.createPercentArray(new float[]{2, 1, 1, 1, 1}))
                        .useAllAvailableWidth();
                addHeaderRow(table, "Month", "Estimated", "Actual", "Variance", "Variance %");

                for (Map<String, Object> row : monthly) {
                    table.addCell(dataCell(String.valueOf(row.get("month"))));
                    table.addCell(dataCell(String.valueOf(row.get("estimated"))));
                    table.addCell(dataCell(String.valueOf(row.get("actual"))));
                    table.addCell(dataCell(String.valueOf(row.get("variance"))));
                    table.addCell(dataCell(row.get("variancePct") + "%"));
                }
                doc.add(table);
            } else {
                doc.add(new Paragraph("No monthly data available."));
            }

            addFooter(doc);
        }

        log.info("PDF generated: {}", file.getAbsolutePath());
        return file;
    }

    // ─── Loss Calculation Report ────────────────────────────────────────────────

    public File generateLossReport(Customer customer, Map<String, Object> loss) throws Exception {
        Path dir = ensureOutputDir();
        File file = dir.resolve(filenameFor(customer, "loss")).toFile();

        try (PdfWriter writer = new PdfWriter(file);
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf)) {

            addHeader(doc, "System Loss Calculation Report");
            addCustomerInfo(doc, customer);

            doc.add(new Paragraph("Loss Analysis Period")
                    .setFontSize(16).setBold().setFontColor(PRIMARY).setMarginTop(20));

            doc.add(new Paragraph("From: " + loss.get("startDate") + "  To: " + loss.get("endDate"))
                    .setFontSize(12));

            Table table = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                    .useAllAvailableWidth().setMarginTop(12);
            table.addCell(kpiCell("Estimated Production", loss.get("estimatedProductionKwh") + " kWh"));
            table.addCell(kpiCell("Actual Production", loss.get("actualProductionKwh") + " kWh"));
            table.addCell(kpiCell("kWh Loss", loss.get("kwhLoss") + " kWh"));
            table.addCell(kpiCell("Cost Impact", "PKR " + loss.get("estimatedCostImpactPKR")));
            table.addCell(kpiCell("Performance", loss.get("performancePct") + "%"));
            table.addCell(kpiCell("Electricity Rate", "PKR " + loss.get("electricityRatePKR") + " /kWh"));
            doc.add(table);

            doc.add(new Paragraph("Customer Summary").setBold().setFontSize(14)
                    .setFontColor(PRIMARY).setMarginTop(20));
            doc.add(new Paragraph(String.valueOf(loss.get("customerSummary"))).setFontSize(11));

            addFooter(doc);
        }
        return file;
    }

    // ─── Cleaning Analysis Report ───────────────────────────────────────────────

    public File generateCleaningReport(Customer customer, Map<String, Object> cleaning) throws Exception {
        Path dir = ensureOutputDir();
        File file = dir.resolve(filenameFor(customer, "cleaning")).toFile();

        try (PdfWriter writer = new PdfWriter(file);
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf)) {

            addHeader(doc, "Panel Cleaning Impact Report");
            addCustomerInfo(doc, customer);

            doc.add(new Paragraph("Cleaning Date: " + cleaning.get("cleaningDate"))
                    .setFontSize(13).setBold().setMarginTop(16));

            Table table = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                    .useAllAvailableWidth().setMarginTop(12);
            table.addCell(kpiCell("Pre-Cleaning Avg", cleaning.get("preAvgKwh") + " kWh/day"));
            table.addCell(kpiCell("Post-Cleaning Avg", cleaning.get("postAvgKwh") + " kWh/day"));
            table.addCell(kpiCell("Daily Gain", cleaning.get("dailyKwhGain") + " kWh"));
            table.addCell(kpiCell("Improvement", cleaning.get("improvementPct") + "%"));
            table.addCell(kpiCell("Total kWh Gain", cleaning.get("totalKwhGain") + " kWh"));
            table.addCell(kpiCell("Estimated Savings", "PKR " + cleaning.get("estimatedSavingsPKR")));
            doc.add(table);

            doc.add(new Paragraph("Customer Letter").setBold().setFontSize(14)
                    .setFontColor(PRIMARY).setMarginTop(20));
            doc.add(new Paragraph(String.valueOf(cleaning.get("customerLetter"))).setFontSize(11));

            addFooter(doc);
        }
        return file;
    }

    // ─── Utility Validation Report ──────────────────────────────────────────────

    public File generateUtilityReport(Customer customer, Map<String, Object> utility,
                                       Map<String, Object> comparison) throws Exception {
        Path dir = ensureOutputDir();
        File file = dir.resolve(filenameFor(customer, "utility")).toFile();

        try (PdfWriter writer = new PdfWriter(file);
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf)) {

            addHeader(doc, "Utility Data Validation Report");
            addCustomerInfo(doc, customer);

            doc.add(new Paragraph("Export Validation").setFontSize(16).setBold()
                    .setFontColor(PRIMARY).setMarginTop(20));

            Table t1 = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                    .useAllAvailableWidth();
            t1.addCell(kpiCell("Total Readings", String.valueOf(utility.get("totalReadings"))));
            t1.addCell(kpiCell("Export Readings (negative)", String.valueOf(utility.get("exportReadings"))));
            t1.addCell(kpiCell("Export Rate", utility.get("exportPercentage") + "%"));
            t1.addCell(kpiCell("Status", String.valueOf(utility.get("status"))));
            doc.add(t1);

            doc.add(new Paragraph(String.valueOf(utility.get("message")))
                    .setFontSize(11).setMarginTop(12));

            if (comparison != null) {
                doc.add(new Paragraph("Utility vs System Comparison")
                        .setFontSize(16).setBold().setFontColor(PRIMARY).setMarginTop(20));

                Table t2 = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                        .useAllAvailableWidth();
                t2.addCell(kpiCell("System Production", comparison.get("systemProductionKwh") + " kWh"));
                t2.addCell(kpiCell("Utility Export", comparison.get("utilityExportKwh") + " kWh"));
                t2.addCell(kpiCell("Utility Import", comparison.get("utilityImportKwh") + " kWh"));
                t2.addCell(kpiCell("Alignment", comparison.get("alignmentPct") + "%"));
                doc.add(t2);
            }

            addFooter(doc);
        }
        return file;
    }

    // ─── Styled cell helpers ────────────────────────────────────────────────────

    private Cell kpiCell(String label, String value) {
        Cell c = new Cell();
        c.add(new Paragraph(label).setFontSize(10).setFontColor(ColorConstants.GRAY));
        c.add(new Paragraph(value != null ? value : "—").setFontSize(14).setBold().setFontColor(DARK));
        c.setBackgroundColor(LIGHT_GRAY);
        c.setBorder(com.itextpdf.layout.borders.Border.NO_BORDER);
        c.setPadding(10);
        return c;
    }

    private Cell dataCell(String text) {
        Cell c = new Cell();
        c.add(new Paragraph(text != null ? text : "—").setFontSize(10));
        c.setPadding(6);
        return c;
    }

    private void addHeaderRow(Table table, String... headers) {
        for (String h : headers) {
            Cell c = new Cell();
            c.add(new Paragraph(h).setBold().setFontColor(ColorConstants.WHITE).setFontSize(11));
            c.setBackgroundColor(PRIMARY);
            c.setPadding(8);
            table.addCell(c);
        }
    }

    private void addHeader(Document doc, String title) {
        doc.add(new Paragraph("WattVue Solar")
                .setFontSize(20).setBold().setFontColor(PRIMARY).setTextAlignment(TextAlignment.CENTER));
        doc.add(new Paragraph(title).setFontSize(14).setFontColor(DARK)
                .setTextAlignment(TextAlignment.CENTER).setMarginBottom(20));
        doc.add(new LineSeparator(new com.itextpdf.kernel.pdf.canvas.draw.SolidLine()).setMarginBottom(20));
    }

    private void addCustomerInfo(Document doc, Customer c) {
        doc.add(new Paragraph("Customer: " + c.getName()).setFontSize(13).setBold());
        if (c.getEmail() != null) doc.add(new Paragraph("Email: " + c.getEmail()).setFontSize(11));
        if (c.getCity() != null) doc.add(new Paragraph("City: " + c.getCity()).setFontSize(11));
        if (c.getSystemSizeKw() != null)
            doc.add(new Paragraph("System Size: " + c.getSystemSizeKw() + " kW").setFontSize(11));
        doc.add(new Paragraph("Report Date: " +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")))
                .setFontSize(11));
    }

    private void addFooter(Document doc) {
        doc.add(new LineSeparator(new com.itextpdf.kernel.pdf.canvas.draw.SolidLine())
                .setMarginTop(30));
        doc.add(new Paragraph("Generated by WattVue Solar Performance Intelligence Platform")
                .setFontSize(9).setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER).setMarginTop(8));
    }
}
