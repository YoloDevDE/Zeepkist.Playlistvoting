package app.yolobolo.zeepkist.apps.playlistvoting.controller;

import app.yolobolo.zeepkist.apps.playlistvoting.model.Level;
import app.yolobolo.zeepkist.apps.playlistvoting.model.VotingSession;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.request.*;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.response.PlaylistResponse;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.response.VoteDetailResponse;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.response.VotingResultResponse;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.Platform;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.VoteOption;
import app.yolobolo.zeepkist.apps.playlistvoting.service.VoteService;
import app.yolobolo.zeepkist.common.model.SteamUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/playlistvoting")
@Tag(name = "Playlist Voting", description = "Endpoints for managing voting sessions and casting votes")
public class PlaylistVotingRestController
{

    @Autowired
    private VoteService voteService;

    // --- Host ---

    @GetMapping("/token")
    @Operation(summary = "Get current user's auth token", description = "Returns the auth token for the currently authenticated user (host).")
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
    @Operation(summary = "Rename a specific session", description = "Renames a session by its ID for the authenticated host.")
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
    @Operation(summary = "Update session state", description = "Updates the state (ACTIVE, PAUSED, FINISHED) of a specific session.")
    public ResponseEntity<Void> updateSessionState(@PathVariable String id, @RequestBody SessionStateRequest request, @AuthenticationPrincipal SteamUserPrincipal principal)
    {
        if (principal == null)
        {
            return ResponseEntity.status(401).build();
        }
        voteService.updateSessionState(id, request.getState(), principal.getHostId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/sessions/{id}/settings")
    @Operation(summary = "Update session settings", description = "Updates all session settings (name, playlist, mode, abstain, state) at once.")
    public ResponseEntity<Void> updateSessionSettings(@PathVariable String id, @RequestBody SessionSettingsRequest request, @AuthenticationPrincipal SteamUserPrincipal principal)
    {
        if (principal == null)
        {
            return ResponseEntity.status(401).build();
        }
        voteService.updateSessionSettings(id, principal.getHostId(), request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sessions/{id}/votes")
    @Operation(summary = "Reset votes for a session", description = "Deletes all votes recorded for a specific session.")
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
    @Operation(summary = "Delete a specific vote", description = "Deletes a specific vote record by its ID.")
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
    @Operation(summary = "Delete a session", description = "Permanently deletes a voting session and all its associated data.")
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
    @Operation(summary = "Create a new voting session", description = "Creates a new voting session for the authenticated host.")
    public ResponseEntity<String> createSession(@RequestBody CreateSessionRequest request, @AuthenticationPrincipal SteamUserPrincipal principal)
    {
        if (principal == null)
        {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.info("Create session requested - name: {}, user: {}", request.getName(), principal.getHostId());

        return Optional.ofNullable(voteService.createSession(principal.getToken(), request))
                .map(session -> ResponseEntity.status(HttpStatus.CREATED)
                        .body("Session created: " + session.getDisplayName()))
                .orElseGet(() -> ResponseEntity.badRequest().body("Failed to create session"));
    }

    @PatchMapping("/sessions/active")
    @Operation(summary = "Rename active session", description = "Renames the currently active session for the authenticated host.")
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
    @Operation(summary = "Pause active session", description = "Pauses the currently active session.")
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
    @Operation(summary = "Resume active session", description = "Resumes the currently paused session.")
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

    @PostMapping("/sessions/active/playlist")
    @Operation(summary = "Update active playlist", description = "Updates the playlist for the active session. Can be authenticated via token.")
    public ResponseEntity<Void> updateActivePlaylist(
            @RequestBody UpdatePlaylistRequest request,
            @AuthenticationPrincipal SteamUserPrincipal principal,
            @Parameter(description = "Host token") @RequestParam(required = false) String token)
    {
        String effectiveToken = (token != null) ? token : (principal != null ? principal.getToken() : null);
        if (effectiveToken == null)
        {
            return ResponseEntity.status(401).build();
        }
        voteService.updatePlaylist(effectiveToken, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions/{id}/playlist")
    @Operation(summary = "Update session playlist", description = "Updates the playlist for a specific session.")
    public ResponseEntity<Void> updateSessionPlaylist(
            @PathVariable String id,
            @RequestBody UpdatePlaylistRequest request,
            @AuthenticationPrincipal SteamUserPrincipal principal)
    {
        if (principal == null)
        {
            return ResponseEntity.status(401).build();
        }
        voteService.updateSessionPlaylist(id, principal.getHostId(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions/active/veto")
    @Operation(summary = "Veto a level", description = "Sets the veto status for a specific level in the active session.")
    public ResponseEntity<Void> vetoActiveLevel(
            @RequestBody VetoRequest request,
            @AuthenticationPrincipal SteamUserPrincipal principal,
            @Parameter(description = "Host token") @RequestParam(required = false) String token)
    {
        String effectiveToken = (token != null) ? token : (principal != null ? principal.getToken() : null);
        if (effectiveToken == null)
        {
            return ResponseEntity.status(401).build();
        }
        voteService.vetoLevel(effectiveToken, request.getUid(), request.getVeto());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions/{id}/levels/veto")
    @Operation(summary = "Veto a level in a specific session", description = "Sets the veto status for a specific level in any session owned by the host.")
    public ResponseEntity<Void> vetoLevel(
            @PathVariable String id,
            @RequestBody VetoRequest request,
            @AuthenticationPrincipal SteamUserPrincipal principal)
    {
        if (principal == null)
        {
            return ResponseEntity.status(401).build();
        }
        voteService.vetoLevel(id, request.getUid(), request.getVeto(), principal.getHostId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/sessions/{id}/levels/status")
    @Operation(summary = "Update level status", description = "Updates the status (e.g., VOTING_FINISHED, VOTING_ACTIVE) of a specific level in a session.")
    public ResponseEntity<Void> updateLevelStatus(
            @PathVariable String id,
            @RequestBody LevelStatusRequest request,
            @AuthenticationPrincipal SteamUserPrincipal principal)
    {
        if (principal == null)
        {
            return ResponseEntity.status(401).build();
        }
        voteService.updateLevelStatus(id, request.getUid(), request.getStatus(), principal.getHostId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions/{id}/playlist")
    @Operation(summary = "Download playlist file", description = "Generates and downloads a .zeeplevel or JSON playlist file for the session results.")
    public ResponseEntity<byte[]> downloadPlaylist(
            @Parameter(description = "Session ID") @PathVariable String id,
            @Parameter(description = "Seconds per round") @RequestParam(defaultValue = "360.0") double roundLength,
            @Parameter(description = "Whether to shuffle the playlist") @RequestParam(defaultValue = "true") boolean shuffle,
            @Parameter(description = "Custom filename") @RequestParam(required = false) String name,
            @Parameter(description = "Export format (e.g., 'json')") @RequestParam(required = false) String type,
            @AuthenticationPrincipal SteamUserPrincipal principal)
    {
        if (principal == null)
        {
            return ResponseEntity.status(401).build();
        }

        byte[] json = voteService.downloadPlaylist(id, roundLength, shuffle, name, principal.getHostId(), type);
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
    @Operation(summary = "Cast a vote", description = "Records a vote for the current level in the active session. Authentication via token required.")
    public ResponseEntity<String> vote(
            @AuthenticationPrincipal SteamUserPrincipal principal,
            @Parameter(description = "Host token (if not logged in via session)") @RequestParam(required = false) String token,
            @Parameter(description = "Platform-specific user ID") @RequestParam String platformUserId,
            @Parameter(description = "Display name of the voter") @RequestParam String username,
            @Parameter(description = "Source platform of the vote") @RequestParam Platform platform,
            @Parameter(description = "The vote option") @RequestParam VoteOption vote)
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
    @Operation(summary = "Reset votes for the active session", description = "Deletes all votes recorded for the currently active session.")
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
    @Operation(summary = "Get active session results", description = "Returns the current level info and vote counts for the active session.")
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
    @Operation(summary = "Get current level of active session", description = "Returns the details of the level currently being voted on.")
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
    @Operation(summary = "Get active session playlist", description = "Returns the list of levels and their vote results for the active session.")
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

    @GetMapping("/vote-details")
    @Operation(summary = "Get detailed vote statistics", description = "Returns levels and voters for a specific vote option across all host's sessions.")
    public ResponseEntity<List<VoteDetailResponse>> getVoteDetails(@RequestParam VoteOption option, @AuthenticationPrincipal SteamUserPrincipal principal)
    {
        if (principal == null)
        {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(voteService.getVoteDetails(principal.getHostId(), option));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Get dashboard data", description = "Returns consolidated data for the dashboard, including active sessions and hosts.")
    public ResponseEntity<Map<String, Object>> getDashboardData(
            @RequestParam(required = false) String hostId,
            @AuthenticationPrincipal SteamUserPrincipal principal)
    {
        String token = principal != null ? principal.getToken() : null;
        String effectiveHostId = hostId != null ? hostId : (principal != null ? principal.getHostId() : null);

        return ResponseEntity.ok(voteService.getDashboardData(token, effectiveHostId));
    }

    @PostMapping("/dashboard/votes")
    @Operation(summary = "Cast a vote via dashboard", description = "Records a vote for a specific host's active session using dashboard authentication.")
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
    @Operation(summary = "Set current level (Form)", description = "Sets the current level for the active session via URL-encoded form data.")
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
    @Operation(summary = "Set current level (JSON)", description = "Sets the current level for the active session via JSON body.")
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
    @Operation(summary = "Update lobby timer", description = "Updates the countdown timer for the active lobby.")
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
