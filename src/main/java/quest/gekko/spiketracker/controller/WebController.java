package quest.gekko.spiketracker.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import quest.gekko.spiketracker.model.match.MatchHistory;
import quest.gekko.spiketracker.model.match.MatchSegment;
import quest.gekko.spiketracker.service.MatchHistoryService;
import quest.gekko.spiketracker.service.MatchTrackingService;

import java.util.Collection;
import java.util.List;

@Controller
public class WebController {
    private final MatchTrackingService matchTrackingService;
    private final MatchHistoryService matchHistoryService;

    public WebController(final MatchTrackingService matchTrackingService, final MatchHistoryService matchHistoryService) {
        this.matchTrackingService = matchTrackingService;
        this.matchHistoryService = matchHistoryService;
    }

    @GetMapping("/")
    public String index(final Model model) {
        model.addAttribute("matches", matchTrackingService.getLiveMatches());
        model.addAttribute("recentMatches", matchHistoryService.getRecentCompletedMatches(5));
        return "index";
    }

    @GetMapping("/api/matches")
    @ResponseBody
    public Collection<MatchSegment> matches() {
        return matchTrackingService.getLiveMatches().values();
    }

    @GetMapping("/api/matches/history")
    @ResponseBody
    public List<MatchHistory> matchHistory() {
        return matchHistoryService.getRecentCompletedMatches(20);
    }

    @GetMapping("/api/health")
    @ResponseBody
    public HealthStatus health() {
        return new HealthStatus("UP", matchTrackingService.getLastUpdateTime());
    }

    @MessageMapping("/matches/subscribe")
    @SendTo("/topic/matches")
    public Collection<MatchSegment> subscribeToMatches() {
        return matchTrackingService.getLiveMatches().values();
    }

    public record HealthStatus(String status, long lastUpdate) {}
}