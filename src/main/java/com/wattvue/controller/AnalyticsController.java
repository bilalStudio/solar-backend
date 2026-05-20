package com.wattvue.controller;

import com.wattvue.dto.ApiResponse;
import com.wattvue.dto.CleaningAnalysisRequest;
import com.wattvue.dto.LossCalculationRequest;
import com.wattvue.model.CleaningEvent;
import com.wattvue.service.AnalyticsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/dashboard-kpis")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboardKPIs() {
        return ResponseEntity.ok(ApiResponse.success("Dashboard KPIs", analyticsService.getDashboardKPIs()));
    }

    @GetMapping("/roi/{customerId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getROIAnalysis(@PathVariable Long customerId) {
        return ResponseEntity.ok(ApiResponse.success("ROI analysis", analyticsService.getROIAnalysis(customerId)));
    }

    @GetMapping("/variance/{customerId}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getVarianceAnalysis(@PathVariable Long customerId) {
        return ResponseEntity.ok(ApiResponse.success("Variance analysis", analyticsService.getVarianceAnalysis(customerId)));
    }

    /** Drag-and-drop comparison: utility data vs system production */
    @GetMapping("/compare/{customerId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> compareUtilityVsSystem(@PathVariable Long customerId) {
        return ResponseEntity.ok(ApiResponse.success("Comparison result",
                analyticsService.compareUtilityVsSystem(customerId)));
    }

    /** Validate that the solar system is exporting power */
    @GetMapping("/utility-validation/{customerId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateUtility(@PathVariable Long customerId) {
        return ResponseEntity.ok(ApiResponse.success("Utility validation",
                analyticsService.validateUtilityExport(customerId)));
    }

    @GetMapping("/utility-chart/{customerId}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getUtilityChart(@PathVariable Long customerId) {
        return ResponseEntity.ok(ApiResponse.success("Utility chart data",
                analyticsService.getUtilityChartData(customerId)));
    }

    /** System loss calculator: kWh lost during a downtime window */
    @PostMapping("/loss-calculation")
    public ResponseEntity<ApiResponse<Map<String, Object>>> calculateLoss(
            @Valid @RequestBody LossCalculationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Loss calculation complete",
                analyticsService.calculateLoss(request)));
    }

    /** Before/after panel cleaning analysis */
    @PostMapping("/cleaning-analysis")
    public ResponseEntity<ApiResponse<Map<String, Object>>> analyzeCleaning(
            @Valid @RequestBody CleaningAnalysisRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cleaning analysis complete",
                analyticsService.analyzeCleaning(request)));
    }

    @GetMapping("/cleaning-history/{customerId}")
    public ResponseEntity<ApiResponse<List<CleaningEvent>>> getCleaningHistory(@PathVariable Long customerId) {
        return ResponseEntity.ok(ApiResponse.success("Cleaning history",
                analyticsService.getCleaningHistory(customerId)));
    }
}
