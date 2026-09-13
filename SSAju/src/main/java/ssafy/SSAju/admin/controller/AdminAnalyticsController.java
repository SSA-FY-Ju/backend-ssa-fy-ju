package ssafy.SSAju.admin.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import ssafy.SSAju.admin.dto.AnalyticsDetailDTO;
import ssafy.SSAju.admin.dto.AnalyticsListDTO;
import ssafy.SSAju.admin.service.AdminAnalyticsService;
import ssafy.SSAju.career.enums.AnalysisType;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Controller
@RequestMapping("/admin/analytics")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAnalyticsController {

    private static final int PAGE_SIZE = 20;

    private final AdminAnalyticsService adminAnalyticsService;

    @GetMapping
    public String getAnalyticsPage(
            @RequestParam(required = false) AnalysisType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        int validPage = Math.max(page, 0);
        List<AnalyticsListDTO> analytics = adminAnalyticsService.getAnalyticsHistory(
                type, dateFrom, dateTo, validPage, PAGE_SIZE);

        model.addAttribute("analytics", analytics);
        model.addAttribute("type", type);
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        model.addAttribute("page", validPage);
        model.addAttribute("pageSize", PAGE_SIZE);
        model.addAttribute("hasPrev", validPage > 0);
        model.addAttribute("hasNext", analytics.size() == PAGE_SIZE);
        model.addAttribute("pageTitle", "분석 기록");
        model.addAttribute("currentMenu", "analytics");
        return "admin/analytics-history";
    }

    @GetMapping("/api")
    @ResponseBody
    public ResponseEntity<List<AnalyticsListDTO>> getAnalyticsApi(
            @RequestParam(required = false) AnalysisType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page) {

        List<AnalyticsListDTO> analytics = adminAnalyticsService.getAnalyticsHistory(
                type, dateFrom, dateTo, page, PAGE_SIZE);
        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/{id}")
    @ResponseBody
    public ResponseEntity<AnalyticsDetailDTO> getAnalyticsDetail(
            @PathVariable Long id,
            @RequestParam AnalysisType type,
            @RequestParam Long userId) {
        AnalyticsDetailDTO detail = adminAnalyticsService.getAnalyticsDetail(id, type, userId);
        return ResponseEntity.ok(detail);
    }
}
