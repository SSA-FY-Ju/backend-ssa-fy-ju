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
import ssafy.SSAju.admin.dto.UserSearchDTO;
import ssafy.SSAju.admin.service.AdminUserService;
import ssafy.SSAju.entity.enums.UserStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserManagementController {

    private final AdminUserService adminUserService;

    @GetMapping
    public String getUserManagementPage(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate joinDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate joinDateTo,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size,
            Model model) {

        List<UserSearchDTO> users = adminUserService.searchUsers(
                email, name,
                toInstant(joinDateFrom),
                toInstant(joinDateTo),
                status, cursor, size);

        Long nextCursor = users.size() == size ? users.get(users.size() - 1).id() : null;

        model.addAttribute("users", users);
        model.addAttribute("nextCursor", nextCursor);
        model.addAttribute("email", email);
        model.addAttribute("name", name);
        model.addAttribute("joinDateFrom", joinDateFrom);
        model.addAttribute("joinDateTo", joinDateTo);
        model.addAttribute("status", status);
        model.addAttribute("size", size);
        model.addAttribute("pageTitle", "유저 관리");
        model.addAttribute("currentMenu", "users");
        return "admin/user-management";
    }

    @GetMapping("/api")
    @ResponseBody
    public ResponseEntity<List<UserSearchDTO>> searchUsersApi(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate joinDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate joinDateTo,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {

        List<UserSearchDTO> users = adminUserService.searchUsers(
                email, name,
                toInstant(joinDateFrom),
                toInstant(joinDateTo),
                status, cursor, size);

        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    @ResponseBody
    public ResponseEntity<UserSearchDTO> getUserProfile(@PathVariable Long id) {
        UserSearchDTO profile = adminUserService.getUserProfile(id);
        return ResponseEntity.ok(profile);
    }

    private Instant toInstant(LocalDate date) {
        return date != null ? date.atStartOfDay().toInstant(ZoneOffset.UTC) : null;
    }
}
