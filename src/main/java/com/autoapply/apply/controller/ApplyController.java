package com.autoapply.apply.controller;
import com.autoapply.apply.entity.Application;
import com.autoapply.apply.service.AutoApplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/apply") @RequiredArgsConstructor
public class ApplyController {
    private final AutoApplyService autoApplyService;

    @PostMapping("/run/{userId}")
    public ResponseEntity<String> triggerAutoApply(@PathVariable String userId) {
        new Thread(() -> autoApplyService.runAutoApplyForUser(userId)).start();
        return ResponseEntity.ok("Auto apply started for: " + userId);
    }

    @GetMapping("/history/{userId}")
    public ResponseEntity<List<Application>> getHistory(@PathVariable String userId) {
        return ResponseEntity.ok(autoApplyService.getUserApplications(userId));
    }
}
