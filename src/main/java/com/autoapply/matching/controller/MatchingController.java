package com.autoapply.matching.controller;
import com.autoapply.matching.entity.JobMatch;
import com.autoapply.matching.service.MatchingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/matches") @RequiredArgsConstructor
public class MatchingController {
    private final MatchingService matchingService;

    @PostMapping("/run/{userId}")
    public ResponseEntity<List<JobMatch>> runMatching(@PathVariable String userId) {
        return ResponseEntity.ok(matchingService.matchJobsForUser(userId));
    }
    @GetMapping("/{userId}")
    public ResponseEntity<List<JobMatch>> getMatches(@PathVariable String userId) {
        return ResponseEntity.ok(matchingService.getMatchesForUser(userId));
    }
    @GetMapping("/{userId}/recommended")
    public ResponseEntity<List<JobMatch>> getRecommended(@PathVariable String userId) {
        return ResponseEntity.ok(matchingService.getRecommendedForUser(userId));
    }
}
