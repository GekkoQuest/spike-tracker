package quest.gekko.spiketracker.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import quest.gekko.spiketracker.model.match.MatchSegment;
import quest.gekko.spiketracker.service.MatchTrackingService;

import java.util.Collection;

@Controller
public class WebController {
    private final MatchTrackingService matchTrackingService;

    public WebController(final MatchTrackingService matchTrackingService) {
        this.matchTrackingService = matchTrackingService;
    }

    @GetMapping("/")
    public String index(final Model model) {
        model.addAttribute("matches", matchTrackingService.getLiveMatches());
        return "index";
    }

    @GetMapping("/api/matches")
    @ResponseBody
    public Collection<MatchSegment> matches() {
        return matchTrackingService.getLiveMatches().values();
    }
}