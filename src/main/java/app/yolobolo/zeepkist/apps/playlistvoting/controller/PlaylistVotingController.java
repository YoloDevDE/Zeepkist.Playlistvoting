package app.yolobolo.zeepkist.apps.playlistvoting.controller;

import app.yolobolo.zeepkist.apps.playlistvoting.model.Level;
import app.yolobolo.zeepkist.apps.playlistvoting.model.VotingSession;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.SessionState;
import app.yolobolo.zeepkist.apps.playlistvoting.service.LevelService;
import app.yolobolo.zeepkist.apps.playlistvoting.service.SessionService;
import app.yolobolo.zeepkist.apps.playlistvoting.service.VoteService;
import app.yolobolo.zeepkist.common.model.SteamUserPrincipal;
import app.yolobolo.zeepkist.common.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/playlistvoting")
public class PlaylistVotingController
{

    private final VoteService voteService;
    private final SessionService sessionService;
    private final LevelService levelService;

    @GetMapping
    public String list(Model model)
    {
        List<Map<String, Object>> hosts = voteService.findAllHostsWithSessions();
        model.addAttribute("hosts", hosts);

        long activeSessionsCount = hosts.stream()
                .filter(h -> h.get("activeSession") != null)
                .count();
        model.addAttribute("activeSessionsCount", activeSessionsCount);

        return "apps/playlistvoting/list";
    }

    @GetMapping("/dashboard")
    public String myDashboard(@AuthenticationPrincipal SteamUserPrincipal principal)
    {
        if (principal != null && principal.getSteamId() != null)
        {
            return "redirect:/playlistvoting/" + principal.getSteamId();
        }
        return "redirect:/playlistvoting";
    }

    @GetMapping("/{steamId}")
    public String hostDashboard(@PathVariable String steamId,
                                @AuthenticationPrincipal SteamUserPrincipal principal,
                                Model model)
    {
        User host = voteService.findUserBySteamId(steamId);
        if (host == null)
        {
            return "redirect:/playlistvoting?error=host_not_found";
        }

        String loggedInHostId = principal != null ? principal.getHostId() : null;
        boolean isOwner = host.getId().equals(loggedInHostId);

        List<VotingSession> allSessions = voteService.findAllSessions(host.getId());
        VotingSession activeSession = sessionService.findActiveOrPausedSession(host.getId());

        Level currentLevel = null;
        if (activeSession != null && activeSession.getCurrentLevelUid() != null)
        {
            currentLevel = levelService.findById(activeSession.getCurrentLevelUid()).orElse(null);
        }

        model.addAttribute("host", host);
        model.addAttribute("isOwner", isOwner);
        model.addAttribute("sessions", allSessions);
        model.addAttribute("activeSession", activeSession);
        model.addAttribute("currentLevel", currentLevel);

        // Compatibility with existing template
        model.addAttribute("token", isOwner ? host.getToken() : null);
        model.addAttribute("states", SessionState.values());

        return "apps/playlistvoting/dashboard";
    }
}
