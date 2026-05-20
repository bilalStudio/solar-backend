package com.wattvue.service;

import com.wattvue.model.Customer;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

@Service
public class ExcelExportService {

    public byte[] exportPerformanceData(Customer customer, Map<String, Object> roi) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet summary = wb.createSheet("Summary");

            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.TEAL.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            int r = 0;
            Row title = summary.createRow(r++);
            title.createCell(0).setCellValue("Customer Performance Report");

            r++;
            row(summary, r++, "Customer Name", customer.getName());
            row(summary, r++, "Email", customer.getEmail());
            row(summary, r++, "City", customer.getCity());
            row(summary, r++, "System Size (kW)", String.valueOf(customer.getSystemSizeKw()));
            r++;
            row(summary, r++, "Total Actual kWh", String.valueOf(roi.get("totalActualKwh")));
            row(summary, r++, "Total Estimated kWh", String.valueOf(roi.get("totalEstimatedKwh")));
            row(summary, r++, "Variance kWh", String.valueOf(roi.get("totalVarianceKwh")));
            row(summary, r++, "Variance %", String.valueOf(roi.get("totalVariancePct")));
            row(summary, r++, "Performance %", String.valueOf(roi.get("performancePct")));
            row(summary, r++, "Actual Savings (PKR)", String.valueOf(roi.get("actualSavingsPKR")));

            summary.autoSizeColumn(0);
            summary.autoSizeColumn(1);

            // Monthly sheet
            Sheet monthly = wb.createSheet("Monthly Data");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) roi.get("monthlyData");

            Row header = monthly.createRow(0);
            String[] headers = {"Month", "Estimated kWh", "Actual kWh", "Variance kWh", "Variance %", "Savings (PKR)"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            if (data != null) {
                int rr = 1;
                for (Map<String, Object> m : data) {
                    Row dr = monthly.createRow(rr++);
                    dr.createCell(0).setCellValue(String.valueOf(m.get("month")));
                    dr.createCell(1).setCellValue(toDouble(m.get("estimated")));
                    dr.createCell(2).setCellValue(toDouble(m.get("actual")));
                    dr.createCell(3).setCellValue(toDouble(m.get("variance")));
                    dr.createCell(4).setCellValue(toDouble(m.get("variancePct")));
                    dr.createCell(5).setCellValue(toDouble(m.get("savings")));
                }
            }
            for (int i = 0; i < headers.length; i++) monthly.autoSizeColumn(i);

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                wb.write(baos);
                return baos.toByteArray();
            }
        }
    }

    private void row(Sheet s, int r, String label, String value) {
        Row row = s.createRow(r);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value != null ? value : "");
    }

    private double toDouble(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return 0; }
    }
}
