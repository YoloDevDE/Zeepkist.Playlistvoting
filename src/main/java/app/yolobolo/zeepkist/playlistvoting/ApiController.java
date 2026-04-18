package app.yolobolo.zeepkist.playlistvoting;

import app.yolobolo.zeepkist.playlistvoting.model.*;
import app.yolobolo.zeepkist.playlistvoting.service.VoteService;
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
@RequestMapping("/zeepkist/playlistvoting/api")
public class ApiController {

    @Autowired
    private VoteService voteService;

    // --- Host ---

    @GetMapping("/token")
    String token() {
        String token = voteService.createToken();
        log.info("New token created: {}", token);
        return token;
    }

    // --- Session ---

    @GetMapping("/session/create")
    public ResponseEntity<String> createSession(@RequestParam String token, @RequestParam String name) {
        log.info("Create session requested - name: {}", name);
        ZkSession session = voteService.createSession(token, name);
        if (session == null) {
            log.warn("Create session failed - token not found: {}", token);
            return ResponseEntity.badRequest().body("Token not found");
        }
        return ResponseEntity.ok("Session created: " + session.getDisplayName());
    }

    @GetMapping("/session/rename")
    public ResponseEntity<String> renameSession(@RequestParam String token, @RequestParam String name) {
        log.info("Rename session requested - new name: {}", name);
        ZkSession session = voteService.renameActiveSession(token, name);
        if (session == null) {
            log.warn("Rename session failed - no active session for token: {}", token);
            return ResponseEntity.badRequest().body("No active session found");
        }
        return ResponseEntity.ok("Session renamed to: " + session.getDisplayName());
    }

    @GetMapping("/session/pause")
    public ResponseEntity<String> pauseSession(@RequestParam String token) {
        log.info("Pause session requested for token: {}", token);
        ZkSession session = voteService.pauseSession(token);
        if (session == null) {
            log.warn("Pause session failed - no active session for token: {}", token);
            return ResponseEntity.badRequest().body("No active session found");
        }
        return ResponseEntity.ok("Session paused: " + session.getDisplayName());
    }

    @GetMapping("/session/resume")
    public ResponseEntity<String> resumeSession(@RequestParam String token) {
        log.info("Resume session requested for token: {}", token);
        ZkSession session = voteService.resumeSession(token);
        if (session == null) {
            log.warn("Resume session failed - no paused session for token: {}", token);
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
    String vote(@RequestParam String token,
                @RequestParam String platformUserId,
                @RequestParam Platform platform,
                @RequestParam VoteOption vote) {
        log.info("Vote received - user: {}, platform: {}, option: {}", platformUserId, platform, vote);
        return voteService.vote(token, platformUserId, platform, vote);
    }

    @GetMapping("/reset")
    String reset(@RequestParam String token) {
        log.info("Reset requested for token: {}", token);
        return voteService.reset(token);
    }

    // --- Get ---

    @GetMapping("/get/result")
    String getResult(@RequestParam String token, @RequestParam ResultOption result) {
        log.debug("Result requested - option: {}", result);
        return voteService.getResult(token, result);
    }

    @GetMapping("/get/currentLevel")
    ResponseEntity<String> getCurrentLevel(@RequestParam String token) {
        log.debug("Current level requested for token: {}", token);
        ZkLevel level = voteService.getCurrentLevel(token);
        if (level == null) return ResponseEntity.badRequest().body("No level currently set");
        return ResponseEntity.ok(level.toString());
    }

    @GetMapping("/get/currentLevel/name")
    ResponseEntity<String> getCurrentLevelName(@RequestParam String token) {
        log.debug("Current level name requested for token: {}", token);
        ZkLevel level = voteService.getCurrentLevel(token);
        if (level == null) return ResponseEntity.badRequest().body("No level currently set");
        return ResponseEntity.ok(level.getName());
    }

    @GetMapping("/get/currentLevel/author")
    ResponseEntity<String> getCurrentLevelAuthor(@RequestParam String token) {
        log.debug("Current level author requested for token: {}", token);
        ZkLevel level = voteService.getCurrentLevel(token);
        if (level == null) return ResponseEntity.badRequest().body("No level currently set");
        return ResponseEntity.ok(level.getAuthor());
    }

    @GetMapping("/get/playlist")
    public ResponseEntity<ZkPlaylistResponse> getPlaylist(@RequestParam String token) {
        log.debug("Playlist requested for token: {}", token);
        ZkPlaylistResponse playlist = voteService.getPlaylist(token);
        if (playlist == null) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(playlist);
    }

    /**
     * Live dashboard data — polled every few seconds by the frontend.
     */
    @GetMapping("/dashboard/live")
    public ResponseEntity<Map<String, Object>> liveDashboard(HttpSession session) {
        String token = (String) session.getAttribute("token");
        String hostId = (String) session.getAttribute("hostId");
        if (token == null || hostId == null) return ResponseEntity.status(401).build();

        return ResponseEntity.ok(voteService.getDashboardData(token, hostId));
    }
    // --- Set ---

    @GetMapping("/set/currentLevel")
    public ResponseEntity<String> setCurrentLevel(@RequestParam String token,
                                                  @RequestParam String uid,
                                                  @RequestParam String name,
                                                  @RequestParam String author,
                                                  @RequestParam Long workshopID) {
        log.info("Set level requested - uid: {}, name: {}, author: {}, workshopID: {}", uid, name, author, workshopID);
        ZkSession session = voteService.findActiveSession(token);
        if (session == null) {
            log.warn("Set level failed - no active session for token: {}", token);
            return ResponseEntity.badRequest().body("No active session found");
        }
        return ResponseEntity.ok(voteService.setCurrentLevel(token, uid, name, author, workshopID));
    }
}
