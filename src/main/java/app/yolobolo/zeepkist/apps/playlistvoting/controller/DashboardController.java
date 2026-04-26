package app.yolobolo.zeepkist.apps.playlistvoting.controller;

import app.yolobolo.zeepkist.apps.playlistvoting.model.Level;
import app.yolobolo.zeepkist.apps.playlistvoting.model.VotingSession;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.SessionState;
import app.yolobolo.zeepkist.apps.playlistvoting.service.VoteService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/playlistvoting/dashboard")
public class DashboardController
{

    private final VoteService voteService;

    @GetMapping
    public String dashboard(HttpSession session, Model model)
    {
        String hostId = (String) session.getAttribute("hostId");
        String token = (String) session.getAttribute("token");
        boolean loggedIn = hostId != null;

        List<VotingSession> allSessions = loggedIn ? voteService.findAllSessions(hostId) : List.of();
        Level currentLevel = loggedIn ? voteService.getCurrentLevel(token) : null;

        model.addAttribute("loggedIn", loggedIn);
        model.addAttribute("steamName", session.getAttribute("steamName"));
        model.addAttribute("displayName", session.getAttribute("displayName"));
        model.addAttribute("token", token);
        model.addAttribute("sessions", allSessions);
        model.addAttribute("currentLevel", currentLevel);
        model.addAttribute("states", SessionState.values());

        return "apps/playlistvoting/dashboard";
    }
}
