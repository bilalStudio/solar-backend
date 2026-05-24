package com.wattvue.service;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.wattvue.model.Customer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
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

    private static final DeviceRgb PRIMARY    = new DeviceRgb(37, 161, 171);   // teal
    private static final DeviceRgb DARK       = new DeviceRgb(30, 41, 59);     // dark blue
    private static final DeviceRgb LIGHT_GRAY = new DeviceRgb(248, 250, 252);
    private static final DeviceRgb BORDER_CLR = new DeviceRgb(226, 232, 240);
    private static final DeviceRgb BEFORE_CLR = new DeviceRgb(245, 158, 11);   // amber
    private static final DeviceRgb AFTER_CLR  = new DeviceRgb(84, 168, 119);   // green

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

    // ─── Load letterhead image bytes from resources ─────────────────────────────
    private byte[] loadLetterhead() {
        try (InputStream is = getClass().getResourceAsStream("/static/wattvue_letterhead.png")) {
            if (is != null) return is.readAllBytes();
        } catch (Exception e) {
            log.warn("Letterhead not found in resources: {}", e.getMessage());
        }
        return null;
    }

    // ─── Add letterhead background to document ──────────────────────────────────
    private void addLetterhead(Document doc, PdfDocument pdf) {
        try {
            byte[] imgBytes = loadLetterhead();
            if (imgBytes == null) return;

            PageSize pageSize = pdf.getDefaultPageSize();
            com.itextpdf.layout.element.Image img =
                new com.itextpdf.layout.element.Image(ImageDataFactory.create(imgBytes));

            img.setFixedPosition(0, 0);
            img.setWidth(pageSize.getWidth());
            img.setHeight(pageSize.getHeight());
            img.setOpacity(1f);

            // Add as background on page 1
            doc.add(img);
        } catch (Exception e) {
            log.warn("Could not add letterhead: {}", e.getMessage());
        }
    }

    // ─── Performance Report (ROI) ───────────────────────────────────────────────
    public File generatePerformanceReport(Customer customer, Map<String, Object> roi) throws Exception {
        Path dir = ensureOutputDir();
        File file = dir.resolve(filenameFor(customer, "performance")).toFile();

        try (PdfWriter writer = new PdfWriter(file);
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf)) {

            addLetterheadPage(doc, pdf, "Solar Performance Report", customer);

            doc.add(sectionTitle("Performance Summary"));

            Table kpiTable = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                    .useAllAvailableWidth().setMarginTop(10);
            kpiTable.addCell(kpiCell("Total Actual Production", roi.get("totalActualKwh") + " kWh"));
            kpiTable.addCell(kpiCell("Total Estimated Production", roi.get("totalEstimatedKwh") + " kWh"));
            kpiTable.addCell(kpiCell("Variance", roi.get("totalVarianceKwh") + " kWh"));
            kpiTable.addCell(kpiCell("Variance %", roi.get("totalVariancePct") + "%"));
            kpiTable.addCell(kpiCell("Performance", roi.get("performancePct") + "%"));
            kpiTable.addCell(kpiCell("Estimated Savings", "PKR " + roi.get("actualSavingsPKR")));
            doc.add(kpiTable);

            doc.add(sectionTitle("Monthly Breakdown"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> monthly = (List<Map<String, Object>>) roi.get("monthlyData");

            if (monthly != null && !monthly.isEmpty()) {
                Table table = new Table(UnitValue.createPercentArray(new float[]{2, 1, 1, 1, 1}))
                        .useAllAvailableWidth().setMarginTop(8);
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
                doc.add(new Paragraph("No monthly data available.").setFontSize(11));
            }

            addFooter(doc);
        }
        return file;
    }

    // ─── Loss Calculation Report ────────────────────────────────────────────────
    public File generateLossReport(Customer customer, Map<String, Object> loss) throws Exception {
        Path dir = ensureOutputDir();
        File file = dir.resolve(filenameFor(customer, "loss")).toFile();

        try (PdfWriter writer = new PdfWriter(file);
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf)) {

            addLetterheadPage(doc, pdf, "System Loss Calculation Report", customer);

            doc.add(sectionTitle("Loss Analysis Period"));
            doc.add(new Paragraph("From: " + loss.get("startDate") + "   To: " + loss.get("endDate"))
                    .setFontSize(12).setMarginBottom(10));

            Table table = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                    .useAllAvailableWidth().setMarginTop(8);
            table.addCell(kpiCell("Estimated Production", loss.get("estimatedProductionKwh") + " kWh"));
            table.addCell(kpiCell("Actual Production", loss.get("actualProductionKwh") + " kWh"));
            table.addCell(kpiCell("kWh Loss", loss.get("kwhLoss") + " kWh"));
            table.addCell(kpiCell("Cost Impact", "PKR " + loss.get("estimatedCostImpactPKR")));
            table.addCell(kpiCell("Performance", loss.get("performancePct") + "%"));
            table.addCell(kpiCell("Electricity Rate", "PKR " + loss.get("electricityRatePKR") + " /kWh"));
            doc.add(table);

            doc.add(sectionTitle("Customer Summary"));
            doc.add(new Paragraph(String.valueOf(loss.get("customerSummary"))).setFontSize(11).setMarginTop(8));

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

            addLetterheadPage(doc, pdf, "Panel Cleaning Impact Report", customer);

            doc.add(new Paragraph("Cleaning Date: " + cleaning.get("cleaningDate"))
                    .setFontSize(13).setBold().setMarginTop(4).setMarginBottom(14));

            // ── KPI grid ──────────────────────────────────────────────────────
            Table kpiTable = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                    .useAllAvailableWidth().setMarginBottom(20);
            kpiTable.addCell(kpiCell("Pre-Cleaning Avg",  cleaning.get("preAvgKwh")  + " kWh/day"));
            kpiTable.addCell(kpiCell("Post-Cleaning Avg", cleaning.get("postAvgKwh") + " kWh/day"));
            kpiTable.addCell(kpiCell("Daily kWh Gain",    cleaning.get("dailyKwhGain") + " kWh"));
            kpiTable.addCell(kpiCell("Improvement",       cleaning.get("improvementPct") + "%"));
            kpiTable.addCell(kpiCell("Total kWh Gain",    cleaning.get("totalKwhGain") + " kWh"));
            kpiTable.addCell(kpiCell("Estimated Savings", "PKR " + cleaning.get("estimatedSavingsPKR")));
            doc.add(kpiTable);

            // ── Bar chart: Before vs After ────────────────────────────────────
            doc.add(sectionTitle("Production Comparison: Before vs After Cleaning"));
            doc.add(buildCleaningBarChart(cleaning));

            // ── Customer letter ───────────────────────────────────────────────
            doc.add(sectionTitle("Customer Letter"));
            doc.add(new Paragraph(String.valueOf(cleaning.get("customerLetter")))
                    .setFontSize(11).setMarginTop(8).setMarginBottom(20));

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

            addLetterheadPage(doc, pdf, "Utility Data Validation Report", customer);

            doc.add(sectionTitle("Export Validation"));

            Table t1 = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth();
            t1.addCell(kpiCell("Total Readings",          String.valueOf(utility.get("totalReadings"))));
            t1.addCell(kpiCell("Export Readings (neg.)",  String.valueOf(utility.get("exportReadings"))));
            t1.addCell(kpiCell("Export Rate",             utility.get("exportPercentage") + "%"));
            t1.addCell(kpiCell("Status",                  String.valueOf(utility.get("status"))));
            doc.add(t1);

            doc.add(new Paragraph(String.valueOf(utility.get("message"))).setFontSize(11).setMarginTop(12));

            if (comparison != null) {
                doc.add(sectionTitle("Utility vs System Comparison"));
                Table t2 = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth();
                t2.addCell(kpiCell("System Production", comparison.get("systemProductionKwh") + " kWh"));
                t2.addCell(kpiCell("Utility Export",    comparison.get("utilityExportKwh") + " kWh"));
                t2.addCell(kpiCell("Utility Import",    comparison.get("utilityImportKwh") + " kWh"));
                t2.addCell(kpiCell("Alignment",         comparison.get("alignmentPct") + "%"));
                doc.add(t2);
            }

            addFooter(doc);
        }
        return file;
    }

    // ─── Bar chart drawn with iText primitives ───────────────────────────────────
    private Table buildCleaningBarChart(Map<String, Object> cleaning) {
        double preAvg  = toDouble(cleaning.get("preAvgKwh"));
        double postAvg = toDouble(cleaning.get("postAvgKwh"));
        double maxVal  = Math.max(preAvg, postAvg) * 1.3;
        if (maxVal == 0) maxVal = 1;

        // Chart area: 400pt wide, 160pt tall
        float chartW = 380f;
        float chartH = 150f;
        float barW   = 70f;
        float gap    = 50f;
        float leftPad = 50f;

        // Use a single-cell table to hold the "chart" paragraph block
        // We'll build it as styled paragraphs + a table for bars

        // Labels row
        Table chart = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .useAllAvailableWidth()
                .setMarginTop(10)
                .setMarginBottom(20);

        // Before bar cell
        float beforeHeight = (float) (chartH * (preAvg / maxVal));
        Cell beforeCell = new Cell()
                .setBorder(Border.NO_BORDER)
                .setTextAlignment(TextAlignment.CENTER)
                .setPaddingTop(10);
        beforeCell.add(new Paragraph(String.format("%.1f kWh/day", preAvg))
                .setFontSize(11).setBold().setFontColor(BEFORE_CLR)
                .setTextAlignment(TextAlignment.CENTER));
        beforeCell.add(new Paragraph(" ").setHeight(Math.max(10, chartH - beforeHeight)));

        // Draw bar as colored background cell
        Table beforeBar = new Table(1).useAllAvailableWidth().setHeight(beforeHeight);
        Cell bCell = new Cell().setBackgroundColor(BEFORE_CLR)
                .setBorder(Border.NO_BORDER)
                .setHeight(beforeHeight);
        beforeBar.addCell(bCell);
        beforeCell.add(beforeBar);

        beforeCell.add(new Paragraph("Before Cleaning")
                .setFontSize(10).setFontColor(BEFORE_CLR).setBold()
                .setTextAlignment(TextAlignment.CENTER).setMarginTop(6));
        chart.addCell(beforeCell);

        // After bar cell
        float afterHeight = (float) (chartH * (postAvg / maxVal));
        Cell afterCell = new Cell()
                .setBorder(Border.NO_BORDER)
                .setTextAlignment(TextAlignment.CENTER)
                .setPaddingTop(10);
        afterCell.add(new Paragraph(String.format("%.1f kWh/day", postAvg))
                .setFontSize(11).setBold().setFontColor(AFTER_CLR)
                .setTextAlignment(TextAlignment.CENTER));
        afterCell.add(new Paragraph(" ").setHeight(Math.max(10, chartH - afterHeight)));

        Table afterBar = new Table(1).useAllAvailableWidth().setHeight(afterHeight);
        Cell aCell = new Cell().setBackgroundColor(AFTER_CLR)
                .setBorder(Border.NO_BORDER)
                .setHeight(afterHeight);
        afterBar.addCell(aCell);
        afterCell.add(afterBar);

        afterCell.add(new Paragraph("After Cleaning")
                .setFontSize(10).setFontColor(AFTER_CLR).setBold()
                .setTextAlignment(TextAlignment.CENTER).setMarginTop(6));
        chart.addCell(afterCell);

        // Improvement badge row
        double improvement = toDouble(cleaning.get("improvementPct"));
        String badge = String.format("⬆ %.2f%% improvement after cleaning", improvement);
        Table badgeTable = new Table(1).useAllAvailableWidth().setMarginTop(8).setMarginBottom(8);
        Cell badgeCell = new Cell()
                .setBackgroundColor(new DeviceRgb(220, 252, 231))
                .setBorder(new SolidBorder(AFTER_CLR, 1))
                .setPadding(10);
        badgeCell.add(new Paragraph(badge)
                .setFontSize(12).setBold().setFontColor(new DeviceRgb(22, 101, 52))
                .setTextAlignment(TextAlignment.CENTER));
        badgeTable.addCell(badgeCell);

        // Wrap both in a container cell
        Cell container = new Cell(1, 2).setBorder(Border.NO_BORDER);
        container.add(badgeTable);

        // Return chart table — caller adds badge separately
        // Instead, put everything in one outer table
        Table outer = new Table(1).useAllAvailableWidth().setMarginTop(10).setMarginBottom(16);
        Cell outerCell = new Cell().setBorder(new SolidBorder(BORDER_CLR, 1))
                .setBackgroundColor(LIGHT_GRAY).setPadding(16).setBorderRadius(new com.itextpdf.layout.properties.BorderRadius(8));
        outerCell.add(chart);
        outerCell.add(badgeTable);
        outer.addCell(outerCell);
        return outer;
    }

    private double toDouble(Object val) {
        if (val == null) return 0;
        try { return Double.parseDouble(String.valueOf(val)); }
        catch (Exception e) { return 0; }
    }

    // ─── Page helpers ─────────────────────────────────────────────────────────────

    private void addLetterheadPage(Document doc, PdfDocument pdf, String title, Customer customer) {
        // Add letterhead as background on current page
        addLetterhead(doc, pdf);

        // Top padding to clear the letterhead header area (~160pt)
        doc.add(new Paragraph(" ").setHeight(155));

        // Title
        doc.add(new Paragraph(title)
                .setFontSize(18).setBold().setFontColor(PRIMARY)
                .setTextAlignment(TextAlignment.CENTER).setMarginBottom(4));

        doc.add(new LineSeparator(new SolidLine(1f)).setMarginBottom(12));

        // Customer info row
        Table info = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .useAllAvailableWidth().setMarginBottom(8);

        Cell left = new Cell().setBorder(Border.NO_BORDER);
        left.add(new Paragraph("Customer").setFontSize(10).setFontColor(ColorConstants.GRAY));
        left.add(new Paragraph(customer.getName()).setFontSize(13).setBold().setFontColor(DARK));
        if (customer.getEmail() != null)
            left.add(new Paragraph(customer.getEmail()).setFontSize(10).setFontColor(ColorConstants.GRAY));
        info.addCell(left);

        Cell right = new Cell().setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.RIGHT);
        right.add(new Paragraph("Report Date").setFontSize(10).setFontColor(ColorConstants.GRAY));
        right.add(new Paragraph(LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")))
                .setFontSize(12).setBold().setFontColor(DARK));
        if (customer.getSystemSizeKw() != null)
            right.add(new Paragraph("System Size: " + customer.getSystemSizeKw() + " kW")
                    .setFontSize(10).setFontColor(ColorConstants.GRAY));
        info.addCell(right);
        doc.add(info);

        doc.add(new LineSeparator(new SolidLine(0.5f)).setMarginBottom(16));
    }

    private Paragraph sectionTitle(String text) {
        return new Paragraph(text)
                .setFontSize(14).setBold().setFontColor(PRIMARY)
                .setMarginTop(18).setMarginBottom(8);
    }

    private Cell kpiCell(String label, String value) {
        Cell c = new Cell();
        c.add(new Paragraph(label).setFontSize(10).setFontColor(ColorConstants.GRAY));
        c.add(new Paragraph(value != null ? value : "—").setFontSize(14).setBold().setFontColor(DARK));
        c.setBackgroundColor(LIGHT_GRAY);
        c.setBorder(Border.NO_BORDER);
        c.setBorderLeft(new SolidBorder(PRIMARY, 3));
        c.setPadding(10).setMarginBottom(4);
        return c;
    }

    private Cell dataCell(String text) {
        Cell c = new Cell();
        c.add(new Paragraph(text != null ? text : "—").setFontSize(10));
        c.setPadding(7);
        return c;
    }

    private void addHeaderRow(Table table, String... headers) {
        for (String h : headers) {
            Cell c = new Cell();
            c.add(new Paragraph(h).setBold().setFontColor(ColorConstants.WHITE).setFontSize(11));
            c.setBackgroundColor(PRIMARY);
            c.setPadding(8);
            c.setBorder(Border.NO_BORDER);
            table.addCell(c);
        }
    }

    private void addFooter(Document doc) {
        doc.add(new LineSeparator(new SolidLine(0.5f)).setMarginTop(28));
        doc.add(new Paragraph("Generated by WattVue Solar Performance Intelligence Platform  |  info@wattvue.com  |  877-928-8883  |  www.wattvue.com")
                .setFontSize(8).setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER).setMarginTop(6));
    }
}
