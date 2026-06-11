package ssafy.SSAju.admin.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import ssafy.SSAju.admin.dto.DashboardDTO;
import ssafy.SSAju.admin.service.AdminDashboardService;

@Slf4j
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public String getDashboard(Model model) {
        DashboardDTO data = adminDashboardService.getDashboardData();
        model.addAttribute("dashboard", data);
        model.addAttribute("pageTitle", "관리자 대시보드");
        model.addAttribute("currentMenu", "dashboard");
        return "admin/dashboard";
    }

    @GetMapping("/api/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<DashboardDTO> getDashboardJson() {
        return ResponseEntity.ok(adminDashboardService.getDashboardData());
    }
}
