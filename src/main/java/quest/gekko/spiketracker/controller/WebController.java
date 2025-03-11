package quest.gekko.spiketracker.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import quest.gekko.spiketracker.model.match.MatchSegment;
import quest.gekko.spiketracker.service.MatchTrackingService;

import java.util.ArrayList;
import java.util.List;

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

    @GetMapping("/matches")
    @ResponseBody
    public List<MatchSegment> getMatches() {
        return new ArrayList<>(matchTrackingService.getLiveMatches().values());
    }
}