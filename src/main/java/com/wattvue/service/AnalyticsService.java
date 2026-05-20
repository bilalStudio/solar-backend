package com.wattvue.service;

import com.wattvue.dto.CleaningAnalysisRequest;
import com.wattvue.dto.LossCalculationRequest;
import com.wattvue.model.CleaningEvent;
import com.wattvue.model.Customer;
import com.wattvue.model.SolarData;
import com.wattvue.model.UtilityData;
import com.wattvue.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final SolarDataRepository solarDataRepository;
    private final CustomerRepository customerRepository;
    private final UploadRepository uploadRepository;
    private final ReportRepository reportRepository;
    private final UtilityDataRepository utilityDataRepository;
    private final CleaningEventRepository cleaningEventRepository;

    private static final double DEFAULT_ELECTRICITY_RATE_PKR = 40.0;

    // ─── Dashboard KPIs ─────────────────────────────────────────────────────────

    public Map<String, Object> getDashboardKPIs() {
        Map<String, Object> kpis = new HashMap<>();

        kpis.put("totalCustomers", customerRepository.count());
        kpis.put("activeCustomers", customerRepository.countByStatus("active"));
        kpis.put("pendingCustomers", customerRepository.countByStatus("pending"));
        kpis.put("totalUploads", uploadRepository.count());
        kpis.put("reportsSent", reportRepository.countByEmailStatus("SENT"));
        kpis.put("reportsPending", reportRepository.countByEmailStatus("PENDING"));

        Double avgVariance = solarDataRepository.avgVariancePct();
        kpis.put("avgVariancePct", avgVariance != null ? round2(avgVariance) : 0.0);

        return kpis;
    }

    // ─── ROI Analysis ───────────────────────────────────────────────────────────

    public Map<String, Object> getROIAnalysis(Long customerId) {
        Map<String, Object> result = new HashMap<>();

        List<SolarData> dataList = solarDataRepository.findByCustomerIdOrderByMonthAsc(customerId);

        if (dataList.isEmpty()) {
            result.put("message", "No solar data available for this customer yet.");
            result.put("totalActualKwh", 0.0);
            result.put("totalEstimatedKwh", 0.0);
            result.put("monthlyData", List.of());
            return result;
        }

        double totalActual = dataList.stream()
                .mapToDouble(d -> d.getActualKwh() != null ? d.getActualKwh() : 0.0).sum();
        double totalEstimated = dataList.stream()
                .mapToDouble(d -> d.getEstimatedKwh() != null ? d.getEstimatedKwh() : 0.0).sum();

        double varKwh = totalActual - totalEstimated;
        double varPct = totalEstimated > 0 ? (varKwh / totalEstimated) * 100.0 : 0.0;
        double actualSavings = totalActual * DEFAULT_ELECTRICITY_RATE_PKR;
        double estimatedSavings = totalEstimated * DEFAULT_ELECTRICITY_RATE_PKR;
        double performance = totalEstimated > 0 ? (totalActual / totalEstimated) * 100.0 : 0.0;

        result.put("totalActualKwh", round2(totalActual));
        result.put("totalEstimatedKwh", round2(totalEstimated));
        result.put("totalVarianceKwh", round2(varKwh));
        result.put("totalVariancePct", round2(varPct));
        result.put("actualSavingsPKR", round2(actualSavings));
        result.put("estimatedSavingsPKR", round2(estimatedSavings));
        result.put("performancePct", round2(performance));
        result.put("monthlyData", buildMonthlyBreakdown(dataList));
        result.put("months", dataList.size());

        return result;
    }

    public List<Map<String, Object>> getVarianceAnalysis(Long customerId) {
        List<SolarData> dataList = solarDataRepository.findByCustomerIdOrderByMonthAsc(customerId);

        return dataList.stream().map(d -> {
            Map<String, Object> row = new HashMap<>();
            row.put("month", d.getMonth());
            row.put("actualKwh", round2(d.getActualKwh()));
            row.put("estimatedKwh", round2(d.getEstimatedKwh()));
            row.put("varianceKwh", round2(d.getVarianceKwh()));
            row.put("variancePct", round2(d.getVariancePct()));
            return row;
        }).toList();
    }

    // ─── Drag-and-Drop Comparison: Utility vs System ────────────────────────────

    public Map<String, Object> compareUtilityVsSystem(Long customerId) {
        Map<String, Object> result = new HashMap<>();

        List<SolarData> systemData = solarDataRepository.findByCustomerIdOrderByMonthAsc(customerId);
        List<UtilityData> utilityData = utilityDataRepository.findByCustomerIdOrderByTimestampAsc(customerId);

        double systemTotal = systemData.stream()
                .mapToDouble(d -> d.getActualKwh() != null ? d.getActualKwh() : 0.0).sum();

        // Total kWh exported to grid (negative readings)
        double utilityExportTotal = utilityData.stream()
                .filter(u -> u.getKwh() != null && u.getKwh() < 0)
                .mapToDouble(u -> Math.abs(u.getKwh())).sum();

        double utilityImportTotal = utilityData.stream()
                .filter(u -> u.getKwh() != null && u.getKwh() > 0)
                .mapToDouble(UtilityData::getKwh).sum();

        long exportReadings = utilityData.stream().filter(u -> u.getKwh() != null && u.getKwh() < 0).count();

        double alignmentPct = systemTotal > 0
                ? Math.min(100, (utilityExportTotal / systemTotal) * 100.0)
                : 0.0;

        result.put("systemProductionKwh", round2(systemTotal));
        result.put("utilityExportKwh", round2(utilityExportTotal));
        result.put("utilityImportKwh", round2(utilityImportTotal));
        result.put("alignmentPct", round2(alignmentPct));
        result.put("exportReadingsCount", exportReadings);
        result.put("totalUtilityReadings", utilityData.size());
        result.put("isExporting", exportReadings > 0);
        result.put("systemMonthlyData", buildMonthlyBreakdown(systemData));

        return result;
    }

    // ─── Utility Export Validation ──────────────────────────────────────────────

    public Map<String, Object> validateUtilityExport(Long customerId) {
        long total = utilityDataRepository.countByCustomerId(customerId);
        long exports = utilityDataRepository.countExportReadings(customerId);

        Map<String, Object> result = new HashMap<>();
        result.put("totalReadings", total);
        result.put("exportReadings", exports);
        result.put("exportPercentage", total > 0 ? round2((exports * 100.0) / total) : 0.0);
        result.put("isExporting", exports > 0);
        result.put("status", exports > 0 ? "CONFIRMED" : "NOT_DETECTED");
        result.put("message", exports > 0
                ? "System is actively exporting power to the grid. " + exports + " export events detected."
                : "No solar export detected in the utility data uploaded.");

        return result;
    }

    public List<Map<String, Object>> getUtilityChartData(Long customerId) {
        List<UtilityData> readings = utilityDataRepository.findByCustomerIdOrderByTimestampAsc(customerId);
        return readings.stream().map(u -> {
            Map<String, Object> row = new HashMap<>();
            row.put("timestamp", u.getTimestamp() != null ? u.getTimestamp().toString() : null);
            row.put("kwh", round2(u.getKwh()));
            row.put("isExport", u.getIsExport() != null && u.getIsExport());
            return row;
        }).toList();
    }

    // ─── System Loss Calculator ─────────────────────────────────────────────────

    public Map<String, Object> calculateLoss(LossCalculationRequest req) {
        Customer customer = customerRepository.findById(req.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        if (req.getStartDate().isAfter(req.getEndDate())) {
            throw new RuntimeException("Start date must be before end date");
        }

        double rate = req.getElectricityRate() != null ? req.getElectricityRate() : DEFAULT_ELECTRICITY_RATE_PKR;

        Double estimated = solarDataRepository.sumEstimatedInRange(
                req.getCustomerId(), req.getStartDate(), req.getEndDate());
        Double actual = solarDataRepository.sumActualInRange(
                req.getCustomerId(), req.getStartDate(), req.getEndDate());

        if (estimated == null) estimated = 0.0;
        if (actual == null) actual = 0.0;

        // If no date-tagged data, estimate from system size
        if (estimated == 0.0 && customer.getSystemSizeKw() != null) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(req.getStartDate(), req.getEndDate()) + 1;
            // Rough estimate: 4 sun-hours/day average for Pakistan
            estimated = customer.getSystemSizeKw() * 4.0 * days;
        }

        double kwhLoss = Math.max(0, estimated - actual);
        double costImpact = kwhLoss * rate;

        Map<String, Object> result = new HashMap<>();
        result.put("customerId", customer.getId());
        result.put("customerName", customer.getName());
        result.put("startDate", req.getStartDate().toString());
        result.put("endDate", req.getEndDate().toString());
        result.put("estimatedProductionKwh", round2(estimated));
        result.put("actualProductionKwh", round2(actual));
        result.put("kwhLoss", round2(kwhLoss));
        result.put("electricityRatePKR", rate);
        result.put("estimatedCostImpactPKR", round2(costImpact));
        result.put("performancePct", estimated > 0 ? round2((actual / estimated) * 100.0) : 0.0);
        result.put("customerSummary", buildCustomerLossSummary(customer, kwhLoss, costImpact, req));

        return result;
    }

    // ─── Before/After Cleaning Analysis ─────────────────────────────────────────

    public Map<String, Object> analyzeCleaning(CleaningAnalysisRequest req) {
        Customer customer = customerRepository.findById(req.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        int window = req.getWindowDays() != null ? req.getWindowDays() : 14;
        LocalDate cleaningDate = req.getCleaningDate();

        LocalDate preStart = cleaningDate.minusDays(window);
        LocalDate preEnd = cleaningDate.minusDays(1);
        LocalDate postStart = cleaningDate.plusDays(1);
        LocalDate postEnd = cleaningDate.plusDays(window);

        Double preAvg = solarDataRepository.avgActualKwhInRange(req.getCustomerId(), preStart, preEnd);
        Double postAvg = solarDataRepository.avgActualKwhInRange(req.getCustomerId(), postStart, postEnd);

        if (preAvg == null) preAvg = 0.0;
        if (postAvg == null) postAvg = 0.0;

        double kwhGain = postAvg - preAvg;
        double improvementPct = preAvg > 0 ? (kwhGain / preAvg) * 100.0 : 0.0;
        double totalGainOverWindow = kwhGain * window;

        // Persist the cleaning event
        CleaningEvent event = CleaningEvent.builder()
                .customer(customer)
                .cleaningDate(cleaningDate)
                .preAvgKwh(round2(preAvg))
                .postAvgKwh(round2(postAvg))
                .kwhGain(round2(kwhGain))
                .improvementPct(round2(improvementPct))
                .notes(req.getNotes())
                .build();
        cleaningEventRepository.save(event);

        Map<String, Object> result = new HashMap<>();
        result.put("cleaningEventId", event.getId());
        result.put("customerId", customer.getId());
        result.put("customerName", customer.getName());
        result.put("cleaningDate", cleaningDate.toString());
        result.put("windowDays", window);
        result.put("preAvgKwh", round2(preAvg));
        result.put("postAvgKwh", round2(postAvg));
        result.put("dailyKwhGain", round2(kwhGain));
        result.put("totalKwhGain", round2(totalGainOverWindow));
        result.put("improvementPct", round2(improvementPct));
        result.put("estimatedSavingsPKR", round2(totalGainOverWindow * DEFAULT_ELECTRICITY_RATE_PKR));
        result.put("wasValuable", improvementPct > 2.0);
        result.put("customerLetter", buildCleaningLetter(customer, cleaningDate, improvementPct, totalGainOverWindow));

        return result;
    }

    public List<CleaningEvent> getCleaningHistory(Long customerId) {
        return cleaningEventRepository.findByCustomerIdOrderByCleaningDateDesc(customerId);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private List<Map<String, Object>> buildMonthlyBreakdown(List<SolarData> dataList) {
        return dataList.stream().map(d -> {
            Map<String, Object> row = new HashMap<>();
            row.put("month", d.getMonth());
            row.put("actual", round2(d.getActualKwh()));
            row.put("estimated", round2(d.getEstimatedKwh()));
            row.put("variance", round2(d.getVarianceKwh()));
            row.put("variancePct", round2(d.getVariancePct()));
            row.put("savings", round2(d.getActualKwh() != null ? d.getActualKwh() * DEFAULT_ELECTRICITY_RATE_PKR : 0));
            return row;
        }).toList();
    }

    private String buildCustomerLossSummary(Customer c, double kwhLoss, double cost, LossCalculationRequest req) {
        return String.format(
                "Dear %s,\n\nDuring the period %s to %s, our analysis shows your solar system underperformed by approximately %.2f kWh, equivalent to PKR %.2f in lost savings (at PKR %.2f/kWh).\n\nWe recommend a follow-up inspection to identify the cause.\n\nRegards,\nWattVue Team",
                c.getName(),
                req.getStartDate(),
                req.getEndDate(),
                kwhLoss,
                cost,
                req.getElectricityRate() != null ? req.getElectricityRate() : DEFAULT_ELECTRICITY_RATE_PKR
        );
    }

    private String buildCleaningLetter(Customer c, LocalDate date, double improvementPct, double totalGain) {
        boolean valuable = improvementPct > 2.0;
        return String.format(
                "Dear %s,\n\nFollowing the panel cleaning service on %s, your solar system shows %s%.2f%% performance change. Total kWh gained: %.2f.\n\n%s\n\nRegards,\nWattVue Team",
                c.getName(),
                date,
                improvementPct >= 0 ? "+" : "",
                improvementPct,
                totalGain,
                valuable
                        ? "The cleaning provided clear value and we recommend scheduling the next service within 3-6 months."
                        : "The cleaning showed minimal impact, which may indicate the panels were not significantly soiled. We recommend longer intervals between cleanings."
        );
    }

    private double round2(Double v) {
        if (v == null) return 0.0;
        return Math.round(v * 100.0) / 100.0;
    }
}
