package app.yolobolo.zeepkist.apps.playlistvoting.controller;

import app.yolobolo.zeepkist.apps.playlistvoting.model.*;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.CreateSessionRequest;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.RenameSessionRequest;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.SessionStateRequest;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.SessionState;
import app.yolobolo.zeepkist.apps.playlistvoting.service.VoteService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/playlistvoting/dashboard")
public class DashboardController {

    private final VoteService voteService;

    @GetMapping
    public String dashboard(HttpSession session, Model model) {
        String hostId = (String) session.getAttribute("hostId");
        String token = (String) session.getAttribute("token");

        List<ZkSession> allSessions = voteService.findAllSessions(hostId);
        ZkLevel currentLevel = voteService.getCurrentLevel(token);

        model.addAttribute("loggedIn", true);
        model.addAttribute("steamName", session.getAttribute("steamName"));
        model.addAttribute("displayName", session.getAttribute("displayName"));
        model.addAttribute("token", token);
        model.addAttribute("sessions", allSessions);
        model.addAttribute("currentLevel", currentLevel);
        model.addAttribute("states", SessionState.values());

        return "apps/playlistvoting/dashboard";
    }

    @PostMapping(value = "/session/create", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Void> createSession(@RequestBody CreateSessionRequest request, HttpSession session) {
        String token = (String) session.getAttribute("token");
        String hostId = (String) session.getAttribute("hostId");
        log.info("Creating session for hostId: {}, token: {}", hostId, token);
        if (token == null) {
            log.warn("Cannot create session: No token found in session for hostId {}", hostId);
            return ResponseEntity.status(401).build();
        }
        voteService.createSession(token, request.getName());
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/session/{id}/rename", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Void> renameSession(@PathVariable String id, @RequestBody RenameSessionRequest request, HttpSession session) {
        voteService.renameSession(id, request.getName(), (String) session.getAttribute("hostId"));
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/session/{id}/state", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Void> changeState(@PathVariable String id, @RequestBody SessionStateRequest request, HttpSession session) {
        voteService.updateSessionState(id, request.getState(), (String) session.getAttribute("hostId"));
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/session/{id}/delete", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Void> deleteSession(@PathVariable String id, HttpSession session) {
        voteService.deleteSession(id, (String) session.getAttribute("hostId"));
        return ResponseEntity.ok().build();
    }
}
