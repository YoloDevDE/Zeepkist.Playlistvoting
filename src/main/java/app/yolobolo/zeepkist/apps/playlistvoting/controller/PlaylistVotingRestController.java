package app.yolobolo.zeepkist.apps.playlistvoting.controller;

import app.yolobolo.zeepkist.apps.playlistvoting.model.Level;
import app.yolobolo.zeepkist.apps.playlistvoting.model.VotingSession;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.request.CreateSessionRequest;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.request.RenameSessionRequest;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.request.SessionStateRequest;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.request.SetLevelRequest;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.response.PlaylistResponse;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.response.VotingResultResponse;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.Platform;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.VoteOption;
import app.yolobolo.zeepkist.apps.playlistvoting.service.VoteService;
import app.yolobolo.zeepkist.common.model.User;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/playlistvoting")
public class PlaylistVotingRestController
{

    @Autowired
    private VoteService voteService;

    // --- Host ---

    @GetMapping("/token")
    String token(HttpSession session)
    {
        String hostId = (String) session.getAttribute("hostId");
        if (hostId == null)
        {
            return "Unauthorized";
        }

        User user = voteService.findUserById(hostId);
        if (user == null)
        {
            return "User not found";
        }

        log.info("Token retrieved for user: {}", user.getId());
        return user.getToken();
    }

    // --- Session ---

    @PostMapping(value = "/session/{id}/rename", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> renameSessionById(@PathVariable String id, @RequestBody RenameSessionRequest request, HttpSession session)
    {
        String hostId = (String) session.getAttribute("hostId");
        if (hostId == null)
        {
            return ResponseEntity.status(401).build();
        }
        voteService.renameSession(id, request.getName(), hostId);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/session/{id}/state", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> changeSessionState(@PathVariable String id, @RequestBody SessionStateRequest request, HttpSession session)
    {
        String hostId = (String) session.getAttribute("hostId");
        if (hostId == null)
        {
            return ResponseEntity.status(401).build();
        }
        voteService.updateSessionState(id, request.getState(), hostId);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/session/{id}/delete", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> deleteSession(@PathVariable String id, HttpSession session)
    {
        String hostId = (String) session.getAttribute("hostId");
        if (hostId == null)
        {
            return ResponseEntity.status(401).build();
        }
        voteService.deleteSession(id, hostId);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/session/create", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createSession(@RequestBody CreateSessionRequest request, HttpSession session)
    {
        String token = (String) session.getAttribute("token");
        log.info("Create session requested - name: {}, token: {}", request.getName(), token);
        if (token == null)
        {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        VotingSession votingSession = voteService.createSession(token, request.getName());
        if (votingSession == null)
        {
            log.warn("Create session failed - token not found or invalid");
            return ResponseEntity.badRequest().body("Token not found");
        }
        return ResponseEntity.ok("Session created: " + votingSession.getDisplayName());
    }

    @PostMapping(value = "/session/rename", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> renameSession(@RequestBody RenameSessionRequest request, HttpSession session)
    {
        String token = (String) session.getAttribute("token");
        log.info("Rename session requested - new name: {}, token: {}", request.getName(), token);
        if (token == null)
        {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        VotingSession votingSession = voteService.renameActiveSession(token, request.getName());
        if (votingSession == null)
        {
            log.warn("Rename session failed - no active session for token: {}", token);
            return ResponseEntity.badRequest().body("No active session found");
        }
        return ResponseEntity.ok("Session renamed to: " + votingSession.getDisplayName());
    }

    @PostMapping(value = "/session/pause", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> pauseSession(HttpSession session)
    {
        String token = (String) session.getAttribute("token");
        log.info("Pause session requested for token: {}", token);
        if (token == null)
        {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        VotingSession votingSession = voteService.pauseSession(token);
        if (votingSession == null)
        {
            log.warn("Pause session failed - no active session for token: {}", token);
            return ResponseEntity.badRequest().body("No active session found");
        }
        return ResponseEntity.ok("Session paused: " + votingSession.getDisplayName());
    }

    @PostMapping(value = "/session/resume", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> resumeSession(HttpSession session)
    {
        String token = (String) session.getAttribute("token");
        log.info("Resume session requested for token: {}", token);
        if (token == null)
        {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        VotingSession votingSession = voteService.resumeSession(token);
        if (votingSession == null)
        {
            log.warn("Resume session failed - no paused session for token: {}", token);
            return ResponseEntity.badRequest().body("No paused session found");
        }
        return ResponseEntity.ok("Session resumed: " + votingSession.getDisplayName());
    }

    @GetMapping("/session/{id}/playlist/download")
    public ResponseEntity<byte[]> downloadPlaylist(
            @PathVariable String id,
            @RequestParam(defaultValue = "360") int roundLength,
            @RequestParam(defaultValue = "true") boolean shuffle,
            @RequestParam(required = false) String name,
            HttpSession session) throws Exception
    {

        String hostId = (String) session.getAttribute("hostId");
        if (hostId == null)
        {
            return ResponseEntity.status(401).build();
        }

        byte[] json = voteService.downloadPlaylist(id, roundLength, shuffle, name, hostId);
        if (json == null)
        {
            return ResponseEntity.notFound().build();
        }

        String filename = voteService.getPlaylistFilename(id, name);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(json);
    }
    // --- Voting ---

    @GetMapping("/vote")
    String vote(
            HttpSession session,
            @RequestParam String platformUserId,
            @RequestParam String username,
            @RequestParam Platform platform,
            @RequestParam VoteOption vote)
    {
        String token = (String) session.getAttribute("token");
        log.info("Vote received - user: {} ({}),  platform: {}, option: {}", platformUserId, username, platform, vote);
        if (token == null)
        {
            return "Unauthorized";
        }
        return voteService.vote(token, platformUserId, username, platform, vote);
    }

    @PostMapping("/reset")
    String reset(HttpSession session)
    {
        String token = (String) session.getAttribute("token");
        log.info("Reset requested for token: {}", token);
        if (token == null)
        {
            return "Unauthorized";
        }
        return voteService.reset(token);
    }

    // --- Get ---

    @GetMapping("/result")
    ResponseEntity<VotingResultResponse> getResult(HttpSession session)
    {
        String token = (String) session.getAttribute("token");
        if (token == null)
        {
            return ResponseEntity.status(401).build();
        }
        VotingResultResponse result = voteService.getResult(token);
        if (result == null)
        {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/currentLevel")
    ResponseEntity<String> getCurrentLevel(HttpSession session)
    {
        String token = (String) session.getAttribute("token");
        log.debug("Current level requested for token: {}", token);
        if (token == null)
        {
            return ResponseEntity.status(401).build();
        }
        Level level = voteService.getCurrentLevel(token);
        if (level == null)
        {
            return ResponseEntity.badRequest().body("No level currently set");
        }
        return ResponseEntity.ok(level.toString());
    }

    @GetMapping("/playlist")
    public ResponseEntity<PlaylistResponse> getPlaylist(HttpSession session)
    {
        String token = (String) session.getAttribute("token");
        log.debug("Playlist requested for token: {}", token);
        if (token == null)
        {
            return ResponseEntity.status(401).build();
        }
        PlaylistResponse playlist = voteService.getPlaylist(token);
        if (playlist == null)
        {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(playlist);
    }

    /**
     * Live dashboard data â€” polled every few seconds by the frontend.
     */
    @GetMapping("/dashboard/live")
    public ResponseEntity<Map<String, Object>> liveDashboard(HttpSession session)
    {
        String token = (String) session.getAttribute("token");
        String hostId = (String) session.getAttribute("hostId");

        return ResponseEntity.ok(voteService.getDashboardData(token, hostId));
    }
    // --- Set ---

    @PostMapping(value = "/currentLevel", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> setCurrentLevel(SetLevelRequest request, HttpSession session)
    {
        String token = (String) session.getAttribute("token");
        log.info("Set level requested via FORM - token: {}, uid: {}, name: {}, author: {}, workshopID: {}",
                token, request.getUid(), request.getName(), request.getAuthor(), request.getWorkshopID());
        return processSetLevel(request, token);
    }

    @PostMapping(value = "/currentLevel", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> setCurrentLevelJson(@RequestBody SetLevelRequest request, HttpSession session)
    {
        String token = (String) session.getAttribute("token");
        log.info("Set level requested via JSON - token: {}, uid: {}, name: {}, author: {}, workshopID: {}",
                token, request.getUid(), request.getName(), request.getAuthor(), request.getWorkshopID());
        return processSetLevel(request, token);
    }

    private ResponseEntity<String> processSetLevel(SetLevelRequest request, String token)
    {
        if (token == null)
        {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        VotingSession votingSession = voteService.findActiveSession(token);
        if (votingSession == null)
        {
            log.warn("Set level failed - no active session for token: {}", token);
            return ResponseEntity.badRequest().body("No active session found");
        }
        return ResponseEntity.ok(voteService.setCurrentLevel(
                token,
                request.getUid(),
                request.getName(),
                request.getAuthor(),
                request.getWorkshopID()));
    }
}
