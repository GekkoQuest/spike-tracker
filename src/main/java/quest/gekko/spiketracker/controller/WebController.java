package quest.gekko.spiketracker.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import quest.gekko.spiketracker.service.MatchTrackingService;

@Controller
public class WebController {

    private final MatchTrackingService matchTrackingService;

    public WebController(MatchTrackingService matchTrackingService) {
        this.matchTrackingService = matchTrackingService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("matches", matchTrackingService.getLiveMatches());
        model.addAttribute("matchStreamLinks", matchTrackingService.getMatchStreamLinks());
        return "index";
    }
}