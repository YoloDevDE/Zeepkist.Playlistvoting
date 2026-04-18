package app.yolobolo.zeepkist.playlistvoting;

import app.yolobolo.zeepkist.playlistvoting.model.SessionState;
import app.yolobolo.zeepkist.playlistvoting.model.ZkHost;
import app.yolobolo.zeepkist.playlistvoting.model.ZkLevel;
import app.yolobolo.zeepkist.playlistvoting.model.ZkSession;
import app.yolobolo.zeepkist.playlistvoting.service.VoteService;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@Slf4j
@Controller
@RequestMapping("/zeepkist/playlistvoting")
public class WebController {

    private static final String REDIRECT_DASHBOARD = "redirect:/zeepkist/playlistvoting/dashboard";
    private static final String REDIRECT_LOGIN = "redirect:/zeepkist/playlistvoting/";
    private static final String REDIRECT_PROFILE = "redirect:/zeepkist/playlistvoting/profile";

    @Autowired
    private VoteService voteService;

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        if (!isLoggedIn(session)) return REDIRECT_LOGIN;

        String hostId = (String) session.getAttribute("hostId");
        String token = (String) session.getAttribute("token");

        List<ZkSession> allSessions = voteService.findAllSessions(hostId);
        ZkLevel currentLevel = voteService.getCurrentLevel(token);

        model.addAttribute("steamName", session.getAttribute("steamName"));
        model.addAttribute("token", token);
        model.addAttribute("sessions", allSessions);
        model.addAttribute("currentLevel", currentLevel);
        model.addAttribute("states", SessionState.values());

        return "dashboard";
    }

    @PostMapping("/dashboard/session/create")
    public String createSession(@RequestParam String name, HttpSession session) {
        if (!isLoggedIn(session)) return REDIRECT_LOGIN;
        voteService.createSession((String) session.getAttribute("token"), name);
        return REDIRECT_DASHBOARD;
    }

    @PostMapping("/dashboard/session/{id}/rename")
    public String renameSession(@PathVariable String id, @RequestParam String name, HttpSession session) {
        if (!isLoggedIn(session)) return REDIRECT_LOGIN;
        voteService.renameSession(id, name, (String) session.getAttribute("hostId"));
        return REDIRECT_DASHBOARD;
    }

    @PostMapping("/dashboard/session/{id}/state")
    public String changeState(@PathVariable String id, @RequestParam SessionState state, HttpSession session) {
        if (!isLoggedIn(session)) return REDIRECT_LOGIN;
        voteService.updateSessionState(id, state, (String) session.getAttribute("hostId"));
        return REDIRECT_DASHBOARD;
    }

    @PostMapping("/dashboard/session/{id}/delete")
    public String deleteSession(@PathVariable String id, HttpSession session) {
        if (!isLoggedIn(session)) return REDIRECT_LOGIN;
        voteService.deleteSession(id, (String) session.getAttribute("hostId"));
        return REDIRECT_DASHBOARD;
    }

    @GetMapping("/profile")
    public String profile(HttpSession session, Model model) {
        if (!isLoggedIn(session)) return REDIRECT_LOGIN;
        String hostId = (String) session.getAttribute("hostId");
        ZkHost host = voteService.findHostById(hostId);

        List<ZkHost> managers = host != null
                ? host.getManagerSteamIds().stream()
                  .map(voteService::findHostBySteamId)
                  .filter(Objects::nonNull)
                  .toList()
                : List.of();

        model.addAttribute("steamName", session.getAttribute("steamName"));
        model.addAttribute("steamId", session.getAttribute("steamId"));
        model.addAttribute("token", session.getAttribute("token"));
        model.addAttribute("host", host);
        model.addAttribute("managers", managers);

        return "profile";
    }

    @PostMapping("/profile/manager/add")
    public String addManager(@RequestParam String steamId, HttpSession session) {
        if (!isLoggedIn(session)) return REDIRECT_LOGIN;
        String hostId = (String) session.getAttribute("hostId");
        ZkHost host = voteService.findHostById(hostId);
        if (host != null && !host.getManagerSteamIds().contains(steamId)) {
            host.getManagerSteamIds().add(steamId);
            voteService.saveHost(host);
            log.info("Added manager steamId {} to host {}", steamId, hostId);
        }
        return REDIRECT_PROFILE;
    }

    @PostMapping("/profile/manager/remove")
    public String removeManager(@RequestParam String steamId, HttpSession session) {
        if (!isLoggedIn(session)) return REDIRECT_LOGIN;
        String hostId = (String) session.getAttribute("hostId");
        ZkHost host = voteService.findHostById(hostId);
        if (host != null) {
            host.getManagerSteamIds().remove(steamId);
            voteService.saveHost(host);
            log.info("Removed manager steamId {} from host {}", steamId, hostId);
        }
        return REDIRECT_PROFILE;
    }

    @PostMapping("/profile/token/refresh")
    public String refreshToken(HttpSession session) {
        if (!isLoggedIn(session)) return REDIRECT_LOGIN;
        String hostId = (String) session.getAttribute("hostId");
        ZkHost host = voteService.findHostById(hostId);
        if (host != null) {
            host.setToken(java.util.UUID.randomUUID().toString().toUpperCase());
            voteService.saveHost(host);
            session.setAttribute("token", host.getToken());
            log.info("Token refreshed for host {}", hostId);
        }
        return REDIRECT_PROFILE;
    }

    private boolean isLoggedIn(HttpSession session) {
        return session.getAttribute("steamId") != null;
    }

}
