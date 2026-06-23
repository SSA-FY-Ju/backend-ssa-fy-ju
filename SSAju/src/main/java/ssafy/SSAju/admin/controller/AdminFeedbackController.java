package ssafy.SSAju.admin.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import ssafy.SSAju.admin.dto.FeedbackDetailDTO;
import ssafy.SSAju.admin.dto.FeedbackListDTO;
import ssafy.SSAju.admin.dto.FeedbackStatDTO;
import ssafy.SSAju.admin.service.AdminFeedbackService;

import java.util.List;

@Slf4j
@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminFeedbackController {

    private static final int PAGE_SIZE = 20;

    private final AdminFeedbackService feedbackService;

    @GetMapping("/feedback")
    public String getFeedbackPage(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        String normalizedType = (type != null && !type.isBlank()) ? type : null;
        int validPage = Math.max(page, 0);

        Page<FeedbackListDTO> feedbackPage = feedbackService.getFeedbackList(normalizedType, validPage, PAGE_SIZE);
        FeedbackStatDTO stats = feedbackService.getFeedbackStats();

        model.addAttribute("feedbackList", feedbackPage.getContent());
        model.addAttribute("stats", stats);
        model.addAttribute("type", normalizedType);
        model.addAttribute("page", validPage);
        model.addAttribute("pageSize", PAGE_SIZE);
        model.addAttribute("hasPrev", validPage > 0);
        model.addAttribute("hasNext", feedbackPage.hasNext());
        model.addAttribute("pageTitle", "피드백 관리");
        model.addAttribute("currentMenu", "feedback");
        return "admin/feedback-management";
    }

    @GetMapping("/api/feedback")
    @ResponseBody
    public ResponseEntity<List<FeedbackListDTO>> getFeedbackApi(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page) {

        String normalizedType = (type != null && !type.isBlank()) ? type : null;
        Page<FeedbackListDTO> result = feedbackService.getFeedbackList(normalizedType, Math.max(page, 0), PAGE_SIZE);
        return ResponseEntity.ok(result.getContent());
    }

    @GetMapping("/api/feedback/stats")
    @ResponseBody
    public ResponseEntity<FeedbackStatDTO> getFeedbackStats() {
        return ResponseEntity.ok(feedbackService.getFeedbackStats());
    }

    @GetMapping("/api/feedback/{id}")
    @ResponseBody
    public ResponseEntity<FeedbackDetailDTO> getFeedbackDetail(@PathVariable Long id) {
        return ResponseEntity.ok(feedbackService.getFeedbackWithAnalysis(id));
    }
}
