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
import app.yolobolo.zeepkist.common.model.SteamUserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/playlistvoting")
public class PlaylistVotingRestController
{

    @Autowired
    private VoteService voteService;

    // --- Host ---

    @GetMapping("/token")
    public ResponseEntity<String> getToken(@AuthenticationPrincipal SteamUserPrincipal principal)
    {
        if (principal == null)
        {
            return ResponseEntity.status(401).build();
        }

        log.info("Token retrieved for user: {}", principal.getHostId());
        return ResponseEntity.ok(principal.getToken());
    }

    // --- Session ---

    @PatchMapping("/sessions/{id}")
    public ResponseEntity<Void> updateSession(@PathVariable String id, @RequestBody RenameSessionRequest request, @AuthenticationPrincipal SteamUserPrincipal principal)
    {
        if (principal == null)
        {
            return ResponseEntity.status(401).build();
        }
        voteService.renameSession(id, request.getName(), principal.getHostId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/sessions/{id}/state")
    public ResponseEntity<Void> updateSessionState(@PathVariable String id, @RequestBody SessionStateRequest request, @AuthenticationPrincipal SteamUserPrincipal principal)
    {
        if (principal == null)
        {
            return ResponseEntity.status(401).build();
        }
        voteService.updateSessionState(id, request.getState(), principal.getHostId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sessions/{id}/votes")
    public ResponseEntity<Void> resetSessionVotes(@PathVariable String id, @AuthenticationPrincipal SteamUserPrincipal principal)
    {
        if (principal == null)
        {
            return ResponseEntity.status(401).build();
        }
        voteService.resetVotes(id, principal.getHostId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/votes/{id}")
    public ResponseEntity<Void> deleteVote(@PathVariable String id, @AuthenticationPrincipal SteamUserPrincipal principal)
    {
        if (principal == null)
        {
            return ResponseEntity.status(401).build();
        }
        voteService.deleteVote(id, principal.getHostId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> deleteSession(@PathVariable String id, @AuthenticationPrincipal SteamUserPrincipal principal)
    {
        if (principal == null)
        {
            return ResponseEntity.status(401).build();
        }
        voteService.deleteSession(id, principal.getHostId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions")
    public ResponseEntity<String> createSession(@RequestBody CreateSessionRequest request, @AuthenticationPrincipal SteamUserPrincipal principal)
    {
        if (principal == null)
        {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.info("Create session requested - name: {}, user: {}", request.getName(), principal.getHostId());

        return Optional.ofNullable(voteService.createSession(principal.getToken(), request.getName()))
                .map(session -> ResponseEntity.status(HttpStatus.CREATED)
                        .body("Session created: " + session.getDisplayName()))
                .orElseGet(() -> ResponseEntity.badRequest().body("Failed to create session"));
    }

    @PatchMapping("/sessions/active")
    public ResponseEntity<String> renameActiveSession(@RequestBody RenameSessionRequest request, @AuthenticationPrincipal SteamUserPrincipal principal)
    {
        if (principal == null)
        {
            return ResponseEntity.status(401).build();
        }

        log.info("Rename active session requested - new name: {}, user: {}", request.getName(), principal.getHostId());
        VotingSession votingSession = voteService.renameActiveSession(principal.getToken(), request.getName());
        if (votingSession == null)
        {
            return ResponseEntity.badRequest().body("No active session found");
        }
        return ResponseEntity.ok("Session renamed to: " + votingSession.getDisplayName());
    }

    @PostMapping("/sessions/active/pause")
    public ResponseEntity<String> pauseActiveSession(@AuthenticationPrincipal SteamUserPrincipal principal)
    {
        if (principal == null)
        {
            return ResponseEntity.status(401).build();
        }

        log.info("Pause session requested for user: {}", principal.getHostId());
        VotingSession votingSession = voteService.pauseSession(principal.getToken());
        if (votingSession == null)
        {
            return ResponseEntity.badRequest().body("No active session found");
        }
        return ResponseEntity.ok("Session paused: " + votingSession.getDisplayName());
    }

    @PostMapping("/sessions/active/resume")
    public ResponseEntity<String> resumeActiveSession(@AuthenticationPrincipal SteamUserPrincipal principal)
    {
        if (principal == null)
        {
            return ResponseEntity.status(401).build();
        }

        log.info("Resume session requested for user: {}", principal.getHostId());
        VotingSession votingSession = voteService.resumeSession(principal.getToken());
        if (votingSession == null)
        {
            return ResponseEntity.badRequest().body("No paused session found");
        }
        return ResponseEntity.ok("Session resumed: " + votingSession.getDisplayName());
    }

    @GetMapping("/sessions/{id}/playlist")
    public ResponseEntity<byte[]> downloadPlaylist(
            @PathVariable String id,
            @RequestParam(defaultValue = "360") int roundLength,
            @RequestParam(defaultValue = "true") boolean shuffle,
            @RequestParam(required = false) String name,
            @AuthenticationPrincipal SteamUserPrincipal principal) throws Exception
    {
        if (principal == null)
        {
            return ResponseEntity.status(401).build();
        }

        byte[] json = voteService.downloadPlaylist(id, roundLength, shuffle, name, principal.getHostId());
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

    @GetMapping("/votes")
    public ResponseEntity<String> vote(
            @AuthenticationPrincipal SteamUserPrincipal principal,
            @RequestParam(required = false) String token,
            @RequestParam String platformUserId,
            @RequestParam String username,
            @RequestParam Platform platform,
            @RequestParam VoteOption vote)
    {
        String effectiveToken = (principal != null) ? principal.getToken() : token;

        log.info("Vote received - user: {} ({}), platform: {}, option: {}, token provided: {}",
                platformUserId, username, platform, vote, token != null);

        if (effectiveToken == null)
        {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        return ResponseEntity.ok(voteService.vote(effectiveToken, platformUserId, username, platform, vote));
    }

    @DeleteMapping("/sessions/active/votes")
    public ResponseEntity<String> resetActiveSessionVotes(@AuthenticationPrincipal SteamUserPrincipal principal)
    {
        if (principal == null)
        {
            return ResponseEntity.status(401).build();
        }
        log.info("Reset active session votes requested for user: {}", principal.getHostId());
        return ResponseEntity.ok(voteService.reset(principal.getToken()));
    }

    // --- Get ---

    @GetMapping("/sessions/active/result")
    public ResponseEntity<VotingResultResponse> getActiveSessionResult(@AuthenticationPrincipal SteamUserPrincipal principal)
    {
        if (principal == null)
        {
            return ResponseEntity.status(401).build();
        }
        VotingResultResponse result = voteService.getResult(principal.getToken());
        if (result == null)
        {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/sessions/active/level")
    public ResponseEntity<String> getActiveSessionLevel(@AuthenticationPrincipal SteamUserPrincipal principal)
    {
        if (principal == null)
        {
            return ResponseEntity.status(401).build();
        }
        Level level = voteService.getCurrentLevel(principal.getToken());
        if (level == null)
        {
            return ResponseEntity.badRequest().body("No level currently set");
        }
        return ResponseEntity.ok(level.toString());
    }

    @GetMapping("/sessions/active/playlist")
    public ResponseEntity<PlaylistResponse> getActiveSessionPlaylist(@AuthenticationPrincipal SteamUserPrincipal principal)
    {
        if (principal == null)
        {
            return ResponseEntity.status(401).build();
        }
        PlaylistResponse playlist = voteService.getPlaylist(principal.getToken());
        if (playlist == null)
        {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(playlist);
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboardData(
            @RequestParam(required = false) String hostId,
            @AuthenticationPrincipal SteamUserPrincipal principal)
    {
        String token = principal != null ? principal.getToken() : null;
        String effectiveHostId = hostId != null ? hostId : (principal != null ? principal.getHostId() : null);

        return ResponseEntity.ok(voteService.getDashboardData(token, effectiveHostId));
    }

    @PostMapping("/dashboard/votes")
    public ResponseEntity<String> castDashboardVote(
            @RequestParam String hostId,
            @RequestParam VoteOption vote,
            @AuthenticationPrincipal SteamUserPrincipal principal)
    {
        if (principal == null)
        {
            return ResponseEntity.status(401).body("You must be logged in to vote");
        }

        log.info("Dashboard vote cast by user: {} (Steam: {}) for host: {} - option: {}",
                principal.getHostId(), principal.getSteamId(), hostId, vote);

        return ResponseEntity.ok(voteService.voteByHostId(hostId, principal.getSteamId(), principal.getDisplayName(), Platform.STEAM, vote));
    }

    // --- Set ---

    @PostMapping(value = "/sessions/active/level", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> setCurrentLevel(SetLevelRequest request, @AuthenticationPrincipal SteamUserPrincipal principal)
    {
        if (principal == null)
        {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        log.info("Set level requested via FORM - user: {}, uid: {}, name: {}",
                principal.getHostId(), request.getUid(), request.getName());
        return processSetLevel(request, principal.getToken());
    }

    @PostMapping(value = "/sessions/active/level", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> setCurrentLevelJson(@RequestBody SetLevelRequest request, @AuthenticationPrincipal SteamUserPrincipal principal)
    {
        if (principal == null)
        {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        log.info("Set level requested via JSON - user: {}, uid: {}, name: {}",
                principal.getHostId(), request.getUid(), request.getName());
        return processSetLevel(request, principal.getToken());
    }

    @PostMapping("/sessions/active/timer")
    public ResponseEntity<Void> updateLobbyTimer(@RequestParam String timer, @AuthenticationPrincipal SteamUserPrincipal principal)
    {
        if (principal == null)
        {
            return ResponseEntity.status(401).build();
        }
        voteService.updateLobbyTimer(principal.getToken(), timer);
        return ResponseEntity.noContent().build();
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
