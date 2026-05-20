package com.wattvue.service;

import com.wattvue.model.*;
import com.wattvue.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * FILE UPLOAD SERVICE
 *
 * Supports two data formats:
 *   SYSTEM   — Solar production data
 *              Columns: Month/Date | Estimated kWh | Actual kWh
 *   UTILITY  — 15-min utility-meter readings
 *              Columns: Timestamp | kWh (negative = export)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileUploadService {

    private final UploadRepository uploadRepository;
    private final CustomerRepository customerRepository;
    private final SolarDataRepository solarDataRepository;
    private final UtilityDataRepository utilityDataRepository;

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("d-MMM-yyyy"),
    };

    private static final DateTimeFormatter[] DATETIME_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
    };

    public Map<String, Object> processUpload(MultipartFile file, Long customerId, String dataType) {
        log.info("Processing {} upload for customer {}: {}", dataType, customerId, file.getOriginalFilename());

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found: " + customerId));

        Upload upload = Upload.builder()
                .customer(customer)
                .originalFilename(file.getOriginalFilename())
                .s3Key("uploads/" + System.currentTimeMillis() + "_" + file.getOriginalFilename())
                .dataType(dataType)
                .status("PROCESSING")
                .build();
        upload = uploadRepository.save(upload);

        try {
            int rowCount;
            if ("UTILITY".equalsIgnoreCase(dataType)) {
                List<UtilityData> rows = parseUtilityFile(file, customer, upload);
                utilityDataRepository.saveAll(rows);
                rowCount = rows.size();
            } else {
                List<SolarData> rows = parseSolarFile(file, customer, upload);
                solarDataRepository.saveAll(rows);
                rowCount = rows.size();
            }

            upload.setStatus("SUCCESS");
            upload.setRowsProcessed(rowCount);
            uploadRepository.save(upload);

            return Map.of(
                    "success", true,
                    "message", "File processed successfully",
                    "rowsProcessed", rowCount,
                    "uploadId", upload.getId(),
                    "dataType", dataType
            );

        } catch (Exception e) {
            upload.setStatus("FAILED");
            upload.setErrorMessage(e.getMessage());
            uploadRepository.save(upload);
            log.error("Failed to process file: {}", e.getMessage());
            throw new RuntimeException("Failed to process file: " + e.getMessage());
        }
    }

    // ─── Solar Data Parsing ─────────────────────────────────────────────────────

    private List<SolarData> parseSolarFile(MultipartFile file, Customer customer, Upload upload) throws Exception {
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";

        List<String[]> rows = name.endsWith(".csv")
                ? readCsvRows(file)
                : readExcelRows(file);

        List<SolarData> dataList = new ArrayList<>();

        for (int i = 1; i < rows.size(); i++) {  // skip header
            String[] cols = rows.get(i);
            if (cols.length < 3) continue;

            try {
                String monthRaw = cols[0].trim();
                Double estimated = parseDouble(cols[1]);
                Double actual = parseDouble(cols[2]);

                if (monthRaw.isEmpty() || estimated == null || actual == null) continue;

                LocalDate dataDate = tryParseDate(monthRaw);

                double varKwh = actual - estimated;
                double varPct = estimated != 0 ? (varKwh / estimated) * 100.0 : 0.0;

                dataList.add(SolarData.builder()
                        .customer(customer)
                        .upload(upload)
                        .month(monthRaw)
                        .dataDate(dataDate)
                        .actualKwh(actual)
                        .estimatedKwh(estimated)
                        .varianceKwh(round2(varKwh))
                        .variancePct(round2(varPct))
                        .build());

            } catch (Exception e) {
                log.warn("Skipping row {}: {}", i, e.getMessage());
            }
        }

        if (dataList.isEmpty()) {
            throw new RuntimeException(
                    "No valid solar data rows found. Expected columns: Month/Date | Estimated kWh | Actual kWh (with header row).");
        }

        return dataList;
    }

    // ─── Utility Data Parsing ───────────────────────────────────────────────────

    private List<UtilityData> parseUtilityFile(MultipartFile file, Customer customer, Upload upload) throws Exception {
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";

        List<String[]> rows = name.endsWith(".csv")
                ? readCsvRows(file)
                : readExcelRows(file);

        List<UtilityData> dataList = new ArrayList<>();

        for (int i = 1; i < rows.size(); i++) {  // skip header
            String[] cols = rows.get(i);
            if (cols.length < 2) continue;

            try {
                String tsRaw = cols[0].trim();
                Double kwh = parseDouble(cols[1]);

                if (tsRaw.isEmpty() || kwh == null) continue;

                LocalDateTime ts = tryParseDateTime(tsRaw);
                if (ts == null) continue;

                dataList.add(UtilityData.builder()
                        .customer(customer)
                        .upload(upload)
                        .timestamp(ts)
                        .kwh(kwh)
                        .isExport(kwh < 0)
                        .build());

            } catch (Exception e) {
                log.warn("Skipping utility row {}: {}", i, e.getMessage());
            }
        }

        if (dataList.isEmpty()) {
            throw new RuntimeException(
                    "No valid utility data rows found. Expected columns: Timestamp | kWh (negative kWh = solar export).");
        }

        return dataList;
    }

    // ─── Generic readers ────────────────────────────────────────────────────────

    private List<String[]> readCsvRows(MultipartFile file) throws Exception {
        List<String[]> rows = new ArrayList<>();
        try (InputStream is = file.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String delim = line.contains(";") ? ";" : ",";
                String[] cols = line.split(delim, -1);
                for (int j = 0; j < cols.length; j++) {
                    cols[j] = cols[j].trim().replaceAll("^\"|\"$", "");
                }
                rows.add(cols);
            }
        }
        return rows;
    }

    private List<String[]> readExcelRows(MultipartFile file) throws Exception {
        List<String[]> rows = new ArrayList<>();
        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                int lastCell = row.getLastCellNum();
                if (lastCell < 1) continue;

                String[] cols = new String[lastCell];
                for (int c = 0; c < lastCell; c++) {
                    cols[c] = cellToString(row.getCell(c));
                }
                rows.add(cols);
            }
        }
        return rows;
    }

    private String cellToString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toString();
                }
                double v = cell.getNumericCellValue();
                yield v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield cell.getStringCellValue().trim(); }
                catch (Exception e) {
                    try { yield String.valueOf(cell.getNumericCellValue()); }
                    catch (Exception ex) { yield ""; }
                }
            }
            default -> "";
        };
    }

    // ─── Parsing helpers ────────────────────────────────────────────────────────

    private Double parseDouble(String s) {
        if (s == null) return null;
        s = s.trim().replace(",", "");
        if (s.isEmpty()) return null;
        try { return Double.parseDouble(s); }
        catch (NumberFormatException e) { return null; }
    }

    private LocalDate tryParseDate(String s) {
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try { return LocalDate.parse(s, fmt); } catch (Exception ignored) {}
        }
        // try datetime first then strip
        LocalDateTime dt = tryParseDateTime(s);
        return dt != null ? dt.toLocalDate() : null;
    }

    private LocalDateTime tryParseDateTime(String s) {
        for (DateTimeFormatter fmt : DATETIME_FORMATS) {
            try { return LocalDateTime.parse(s, fmt); } catch (Exception ignored) {}
        }
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try { return LocalDate.parse(s, fmt).atStartOfDay(); } catch (Exception ignored) {}
        }
        return null;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
