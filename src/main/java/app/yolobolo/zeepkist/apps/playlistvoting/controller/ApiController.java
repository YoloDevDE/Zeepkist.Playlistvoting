package app.yolobolo.zeepkist.apps.playlistvoting.controller;

import app.yolobolo.zeepkist.apps.playlistvoting.model.*;
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
public class ApiController {

    @Autowired
    private VoteService voteService;

    // --- Host ---

    @GetMapping("/token")
    String token(HttpSession session) {
        String hostId = (String) session.getAttribute("hostId");
        if (hostId == null) return "Unauthorized";

        User user = voteService.findUserById(hostId);
        if (user == null) return "User not found";

        log.info("Token retrieved for user: {}", user.getId());
        return user.getToken();
    }

    // --- Session ---

    @PostMapping(value = "/session/create", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createSession(@RequestBody CreateSessionRequest request) {
        log.info("Create session requested - name: {}", request.getName());
        ZkSession session = voteService.createSession(request.getToken(), request.getName());
        if (session == null) {
            log.warn("Create session failed - token not found: {}", request.getToken());
            return ResponseEntity.badRequest().body("Token not found");
        }
        return ResponseEntity.ok("Session created: " + session.getDisplayName());
    }

    @PostMapping(value = "/session/rename", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> renameSession(@RequestBody RenameSessionRequest request) {
        log.info("Rename session requested - new name: {}", request.getName());
        ZkSession session = voteService.renameActiveSession(request.getToken(), request.getName());
        if (session == null) {
            log.warn("Rename session failed - no active session for token: {}", request.getToken());
            return ResponseEntity.badRequest().body("No active session found");
        }
        return ResponseEntity.ok("Session renamed to: " + session.getDisplayName());
    }

    @PostMapping(value = "/session/pause", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> pauseSession(@RequestBody TokenRequest request) {
        log.info("Pause session requested for token: {}", request.getToken());
        ZkSession session = voteService.pauseSession(request.getToken());
        if (session == null) {
            log.warn("Pause session failed - no active session for token: {}", request.getToken());
            return ResponseEntity.badRequest().body("No active session found");
        }
        return ResponseEntity.ok("Session paused: " + session.getDisplayName());
    }

    @PostMapping(value = "/session/resume", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> resumeSession(@RequestBody TokenRequest request) {
        log.info("Resume session requested for token: {}", request.getToken());
        ZkSession session = voteService.resumeSession(request.getToken());
        if (session == null) {
            log.warn("Resume session failed - no paused session for token: {}", request.getToken());
            return ResponseEntity.badRequest().body("No paused session found");
        }
        return ResponseEntity.ok("Session resumed: " + session.getDisplayName());
    }

    @GetMapping("/session/{id}/playlist/download")
    public ResponseEntity<byte[]> downloadPlaylist(
            @PathVariable String id,
            @RequestParam(defaultValue = "360") int roundLength,
            @RequestParam(defaultValue = "true") boolean shuffle,
            @RequestParam(required = false) String name,
            HttpSession session) throws Exception {

        String hostId = (String) session.getAttribute("hostId");
        if (hostId == null) return ResponseEntity.status(401).build();

        byte[] json = voteService.downloadPlaylist(id, roundLength, shuffle, name, hostId);
        if (json == null) return ResponseEntity.notFound().build();

        String filename = voteService.getPlaylistFilename(id, name);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(json);
    }
    // --- Voting ---

    @GetMapping("/vote")
    String vote(
            @RequestParam String token,
            @RequestParam String platformUserId,
           @RequestParam String  username,
            @RequestParam Platform platform,
            @RequestParam VoteOption vote) {
        log.info("Vote received - user: {} ({}),  platform: {}, option: {}", platformUserId, username, platform, vote);
        return voteService.vote(token, platformUserId, username, platform, vote);
    }

    @GetMapping( "/reset")
    String reset(@RequestParam String token) {
        log.info("Reset requested for token: {}", token);
        return voteService.reset(token);
    }

    // --- Get ---

    @GetMapping("/result")
    String getResult(@RequestParam String token, @RequestParam ResultOption result) {
        log.debug("Result requested - option: {}", result);
        return voteService.getResult(token, result);
    }

    @GetMapping("/currentLevel")
    ResponseEntity<String> getCurrentLevel(@RequestParam String token) {
        log.debug("Current level requested for token: {}", token);
        ZkLevel level = voteService.getCurrentLevel(token);
        if (level == null) return ResponseEntity.badRequest().body("No level currently set");
        return ResponseEntity.ok(level.toString());
    }

    @GetMapping("/currentLevel/name")
    ResponseEntity<String> getCurrentLevelName(@RequestParam String token) {
        log.debug("Current level name requested for token: {}", token);
        ZkLevel level = voteService.getCurrentLevel(token);
        if (level == null) return ResponseEntity.badRequest().body("No level currently set");
        return ResponseEntity.ok(level.getName());
    }

    @GetMapping("/currentLevel/author")
    ResponseEntity<String> getCurrentLevelAuthor(@RequestParam String token) {
        log.debug("Current level author requested for token: {}", token);
        ZkLevel level = voteService.getCurrentLevel(token);
        if (level == null) return ResponseEntity.badRequest().body("No level currently set");
        return ResponseEntity.ok(level.getAuthor());
    }

    @GetMapping("/playlist")
    public ResponseEntity<ZkPlaylistResponse> getPlaylist(@RequestParam String token) {
        log.debug("Playlist requested for token: {}", token);
        ZkPlaylistResponse playlist = voteService.getPlaylist(token);
        if (playlist == null) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(playlist);
    }

    /**
     * Live dashboard data â€” polled every few seconds by the frontend.
     */
    @GetMapping("/dashboard/live")
    public ResponseEntity<Map<String, Object>> liveDashboard(HttpSession session) {
        String token = (String) session.getAttribute("token");
        String hostId = (String) session.getAttribute("hostId");
        if (token == null || hostId == null) return ResponseEntity.status(401).build();

        return ResponseEntity.ok(voteService.getDashboardData(token, hostId));
    }
    // --- Set ---

    @PostMapping(value = "/currentLevel", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> setCurrentLevel(SetLevelRequest request) {
        log.info("Set level requested via FORM - token: {}, uid: {}, name: {}, author: {}, workshopID: {}",
                request.getToken(), request.getUid(), request.getName(), request.getAuthor(), request.getWorkshopID());
        return processSetLevel(request);
    }

    @PostMapping(value = "/currentLevel", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> setCurrentLevelJson(@RequestBody SetLevelRequest request) {
        log.info("Set level requested via JSON - token: {}, uid: {}, name: {}, author: {}, workshopID: {}",
                request.getToken(), request.getUid(), request.getName(), request.getAuthor(), request.getWorkshopID());
        return processSetLevel(request);
    }

    private ResponseEntity<String> processSetLevel(SetLevelRequest request) {
        ZkSession session = voteService.findActiveSession(request.getToken());
        if (session == null) {
            log.warn("Set level failed - no active session for token: {}", request.getToken());
            return ResponseEntity.badRequest().body("No active session found");
        }
        return ResponseEntity.ok(voteService.setCurrentLevel(
                request.getToken(),
                request.getUid(),
                request.getName(),
                request.getAuthor(),
                request.getWorkshopID()));
    }
}
