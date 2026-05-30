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
import com.wattvue.dto.FieldDocumentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FieldPdfService {

    private static final DeviceRgb PRIMARY    = new DeviceRgb(37, 161, 171);
    private static final DeviceRgb DARK       = new DeviceRgb(30, 41, 59);
    private static final DeviceRgb LIGHT_GRAY = new DeviceRgb(248, 250, 252);
    private static final DeviceRgb BORDER_CLR = new DeviceRgb(226, 232, 240);
    private static final String OUTPUT_DIR    = "generated-reports";

    private Path ensureOutputDir() throws Exception {
        Path dir = Paths.get(OUTPUT_DIR);
        if (!Files.exists(dir)) Files.createDirectories(dir);
        return dir;
    }

    public String generateFieldDocumentPdf(FieldDocumentResponse doc) throws Exception {
        File file = generateFieldDocumentPdfFile(doc);
        return file.getAbsolutePath();
    }

    public File generateFieldDocumentPdfFile(FieldDocumentResponse doc) throws Exception {
        Path dir = ensureOutputDir();
        String safe = (doc.getDocTypeLabel() != null ? doc.getDocTypeLabel() : "doc")
                .replaceAll("[^a-zA-Z0-9]", "_");
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File file = dir.resolve(safe + "_" + ts + ".pdf").toFile();

        try (PdfWriter writer = new PdfWriter(file);
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf)) {

            addLetterhead(document, pdf);

            // Top padding for letterhead header
            document.add(new Paragraph(" ").setHeight(155));

            // Title
            document.add(new Paragraph(doc.getDocTypeLabel())
                    .setFontSize(18).setBold().setFontColor(PRIMARY)
                    .setTextAlignment(TextAlignment.CENTER).setMarginBottom(4));

            document.add(new LineSeparator(new SolidLine(1f)).setMarginBottom(12));

            // Customer + tech info
            Table info = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                    .useAllAvailableWidth().setMarginBottom(16);

            Cell left = new Cell().setBorder(Border.NO_BORDER);
            left.add(new Paragraph("Customer").setFontSize(10).setFontColor(ColorConstants.GRAY));
            left.add(new Paragraph(doc.getCustomerName() != null ? doc.getCustomerName() : "—")
                    .setFontSize(13).setBold().setFontColor(DARK));
            info.addCell(left);

            Cell right = new Cell().setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.RIGHT);
            right.add(new Paragraph("Technician").setFontSize(10).setFontColor(ColorConstants.GRAY));
            right.add(new Paragraph(doc.getTechnicianName() != null ? doc.getTechnicianName() : "—")
                    .setFontSize(13).setBold().setFontColor(DARK));
            right.add(new Paragraph(LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")))
                    .setFontSize(10).setFontColor(ColorConstants.GRAY));
            info.addCell(right);
            document.add(info);

            document.add(new LineSeparator(new SolidLine(0.5f)).setMarginBottom(16));

            // Field values — only visible ones
            List<FieldDocumentResponse.FieldValueResponse> fields = doc.getFieldValues();
            if (fields != null) {
                for (FieldDocumentResponse.FieldValueResponse field : fields) {
                    if (Boolean.FALSE.equals(field.getIsVisible())) continue;

                    if ("PHOTO".equals(field.getFieldType())) {
                        // Photo fields — find matching photos
                        document.add(new Paragraph(field.getFieldLabel() != null ? field.getFieldLabel() : field.getFieldKey())
                                .setFontSize(11).setBold().setFontColor(PRIMARY).setMarginTop(10));

                        if (doc.getPhotos() != null) {
                            doc.getPhotos().stream()
                                .filter(p -> field.getFieldKey().equals(p.getFieldKey()))
                                .forEach(photo -> {
                                    try {
                                        if (photo.getS3Url() != null && photo.getS3Url().startsWith("http")) {
                                            byte[] imgBytes = new URL(photo.getS3Url()).openStream().readAllBytes();
                                            Image img = new Image(ImageDataFactory.create(imgBytes));
                                            img.setMaxWidth(300).setMarginBottom(8);
                                            document.add(img);
                                        }
                                        if (photo.getCaption() != null) {
                                            document.add(new Paragraph(photo.getCaption())
                                                    .setFontSize(9).setFontColor(ColorConstants.GRAY).setMarginBottom(4));
                                        }
                                    } catch (Exception e) {
                                        log.warn("Could not embed photo {}: {}", photo.getS3Url(), e.getMessage());
                                    }
                                });
                        }
                    } else {
                        // Regular field
                        Table row = new Table(UnitValue.createPercentArray(new float[]{2, 3}))
                                .useAllAvailableWidth().setMarginBottom(6);
                        Cell labelCell = new Cell().setBorder(Border.NO_BORDER)
                                .setBackgroundColor(LIGHT_GRAY).setPadding(8);
                        labelCell.add(new Paragraph(field.getFieldLabel() != null ? field.getFieldLabel() : field.getFieldKey())
                                .setFontSize(10).setBold().setFontColor(DARK));
                        row.addCell(labelCell);

                        Cell valueCell = new Cell().setBorder(Border.NO_BORDER).setPadding(8);
                        valueCell.add(new Paragraph(field.getFieldValue() != null ? field.getFieldValue() : "—")
                                .setFontSize(10).setFontColor(DARK));
                        row.addCell(valueCell);
                        document.add(row);
                    }
                }
            }

            // Notes
            if (doc.getNotes() != null && !doc.getNotes().isBlank()) {
                document.add(new Paragraph("Notes").setFontSize(12).setBold()
                        .setFontColor(PRIMARY).setMarginTop(16));
                document.add(new Paragraph(doc.getNotes()).setFontSize(10).setMarginBottom(12));
            }

            // Footer
            document.add(new LineSeparator(new SolidLine(0.5f)).setMarginTop(24));
            document.add(new Paragraph("Generated by WattVue Field Portal  |  info@wattvue.com  |  877-928-8883  |  www.wattvue.com")
                    .setFontSize(8).setFontColor(ColorConstants.GRAY)
                    .setTextAlignment(TextAlignment.CENTER).setMarginTop(6));
        }

        return file;
    }

    private void addLetterhead(Document doc, PdfDocument pdf) {
        try (InputStream is = getClass().getResourceAsStream("/static/wattvue_letterhead.png")) {
            if (is == null) return;
            byte[] imgBytes = is.readAllBytes();
            PageSize pageSize = pdf.getDefaultPageSize();
            Image img = new Image(ImageDataFactory.create(imgBytes));
            img.setFixedPosition(0, 0);
            img.setWidth(pageSize.getWidth());
            img.setHeight(pageSize.getHeight());
            doc.add(img);
        } catch (Exception e) {
            log.warn("Could not add letterhead: {}", e.getMessage());
        }
    }
}
