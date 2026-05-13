package app.yolobolo.zeepkist.apps.playlistvoting.service;

import app.yolobolo.zeepkist.apps.playlistvoting.model.Level;
import app.yolobolo.zeepkist.apps.playlistvoting.model.UserVote;
import app.yolobolo.zeepkist.apps.playlistvoting.model.VotingSession;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.ZeeplistDTO;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.request.CreateSessionRequest;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.request.LobbyTimerRequest;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.request.SessionSettingsRequest;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.request.UpdatePlaylistRequest;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.response.*;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.*;
import app.yolobolo.zeepkist.apps.playlistvoting.repository.UserVoteRepository;
import app.yolobolo.zeepkist.common.model.User;
import app.yolobolo.zeepkist.common.repository.UserRepository;
import app.yolobolo.zeepkist.common.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoteService
{
    private final UserRepository userRepo;
    private final UserVoteRepository voteRepository;
    private final SessionService sessionService;
    private final LevelService levelService;
    private final VoteProcessingService voteProcessingService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserService userService;

    // --- Token / User ---

    private void notifyUpdate(String hostId)
    {
        log.debug("Notifying update for host: {}", hostId);
        messagingTemplate.convertAndSend("/topic/dashboard/" + hostId + "/results", "update");
        messagingTemplate.convertAndSend("/topic/dashboard/global", "update");

        // Send results directly for mod clients
        VotingResultResponse result = getResultByHostId(hostId);
        if (result != null)
        {
            // Use steamId if available, fallback to internalId
            String topicId = hostId;
            User user = findUserById(hostId);
            if (user != null)
            {
                String steamId = user.getSteamId();
                if (steamId != null && !steamId.isEmpty())
                {
                    topicId = steamId;
                }
            }
            log.debug("Sending result to topic: /topic/votes/{}", topicId);
            messagingTemplate.convertAndSend("/topic/votes/" + topicId, result);
        }
    }

    private void notifyTimer(String hostId, VotingSession session)
    {
        Map<String, Object> timerData = new HashMap<>();
        timerData.put("roundTime", session.getRoundTime());
        timerData.put("levelLoadedAtTime", session.getLevelLoadedAtTime());
        timerData.put("currentTime", session.getCurrentTime());
        timerData.put("gameState", session.getLobbyGameState());
        messagingTemplate.convertAndSend("/topic/dashboard/" + hostId + "/timer", (Object) timerData);
    }

    public User findUserByToken(String token)
    {
        return userRepo.findByToken(token).orElse(null);
    }

    public User findUserById(String id)
    {
        return userRepo.findById(id).orElse(null);
    }

    public User findUserBySteamId(String steamId)
    {
        return userRepo.findByIdentity("STEAM", steamId).orElse(null);
    }

    public void saveUser(User user)
    {
        userRepo.save(user);
        log.info("User saved: {}", user.getId());
    }

    public void deleteUser(String userId)
    {
        User user = findUserById(userId);
        if (user == null)
        {
            return;
        }

        // 1. Delete all votes by this user (Steam)
        String steamId = user.getSteamId();
        if (steamId != null)
        {
            voteRepository.deleteByPlatformAndPlatformUserId(Platform.STEAM, steamId);
        }

        // 2. Delete all sessions hosted by this user
        List<VotingSession> sessions = sessionService.findByHostId(userId);
        for (VotingSession session : sessions)
        {
            // Delete votes for each session
            voteRepository.deleteBySessionId(session.getId());
        }
        sessionService.deleteByHostId(userId);

        // 3. Delete the user itself
        userRepo.deleteById(userId);

        // 4. Remove this user as a manager from others
        if (steamId != null)
        {
            List<User> managedByThisUser = userRepo.findByManagerIdsContaining(steamId);
            for (User hostUser : managedByThisUser)
            {
                if (hostUser.getManagerIds() != null)
                {
                    hostUser.getManagerIds().remove(steamId);
                    userRepo.save(hostUser);
                }
            }
        }

        log.info("User {} and all relations deleted", userId);
    }

    // --- Session ---

    public VotingSession findActiveSession(String token)
    {
        User user = findUserByToken(token);
        if (user == null)
        {
            log.warn("No user found for token: {}", token);
            return null;
        }
        return sessionService.findActiveOrPausedSession(user.getId());
    }

    public VotingSession createSession(String token, CreateSessionRequest request)
    {
        User user = findUserByToken(token);
        if (user == null)
        {
            log.warn("Cannot create session - no user found for token: {}", token);
            return null;
        }
        VotingSession session = sessionService.createSession(user, request.getName());
        if (session == null)
        {
            notifyUpdate(user.getId());
            return null;
        }

        session.setPlaylistMode(request.isPlaylistMode());
        if (request.getVotingMode() != null)
        {
            session.setVotingMode(request.getVotingMode());
            session.setAllowAbstain(request.getVotingMode() == app.yolobolo.zeepkist.apps.playlistvoting.model.enums.VotingMode.ABSTAIN_ENABLED);
        }
        if (request.getZeeplist() != null)
        {
            applyZeeplistToSession(session, request.getZeeplist());
        }
        sessionService.save(session);
        notifyUpdate(user.getId());
        return session;
    }

    private void applyZeeplistToSession(VotingSession session, ZeeplistDTO zeeplist)
    {
        Set<String> uniqueUids = new LinkedHashSet<>();
        if (zeeplist.getLevels() != null)
        {
            for (ZeeplistDTO.ZeeplistLevelDTO levelDto : zeeplist.getLevels())
            {
                String uid = levelDto.getUid();
                if (uid == null || uniqueUids.contains(uid))
                {
                    continue;
                }

                levelService.getOrCreateLevel(
                        uid,
                        levelDto.getName(),
                        levelDto.getAuthor(),
                        levelDto.getWorkshopId()
                );
                uniqueUids.add(uid);

                if (levelDto.isPlayed())
                {
                    if (!session.getPlayedLevels().contains(uid))
                    {
                        session.getPlayedLevels().add(uid);
                    }
                    session.getLevelStatuses().put(uid, LevelStatus.VOTING_FINISHED);
                }
            }
        }
        session.setPlaylist(new ArrayList<>(uniqueUids));
    }

    public void renameSession(String id, String newName, String hostId)
    {
        sessionService.renameSession(id, newName, hostId);
        notifyUpdate(hostId);
    }

    public void updateSessionState(String id, SessionState state, String hostId)
    {
        sessionService.updateSessionState(id, state, hostId);
        notifyUpdate(hostId);
    }

    public void deleteSession(String id, String hostId)
    {
        sessionService.deleteSession(id, hostId);
        notifyUpdate(hostId);
    }

    public VotingSession renameActiveSession(String token, String newName)
    {
        VotingSession session = findActiveSession(token);
        if (session == null)
        {
            return null;
        }
        session.setDisplayName(newName);
        sessionService.save(session);
        log.info("Active session renamed to '{}'", newName);
        notifyUpdate(session.getHostId());
        return session;
    }

    public VotingSession pauseSession(String token)
    {
        VotingSession session = findActiveSession(token);
        if (session == null)
        {
            return null;
        }
        session.setState(SessionState.PAUSED);
        sessionService.save(session);
        log.info("Session '{}' paused", session.getDisplayName());
        notifyUpdate(session.getHostId());
        return session;
    }

    public VotingSession resumeSession(String token)
    {
        VotingSession session = findActiveSession(token);
        if (session == null || session.getState() != SessionState.PAUSED)
        {
            return null;
        }
        session.setState(SessionState.ACTIVE);
        sessionService.save(session);
        log.info("Session '{}' resumed", session.getDisplayName());
        notifyUpdate(session.getHostId());
        return session;
    }

    // --- Level ---

    public Level getCurrentLevel(String token)
    {
        VotingSession session = findActiveSession(token);
        if (session == null || session.getCurrentLevelUid() == null)
        {
            return null;
        }
        return levelService.findById(session.getCurrentLevelUid()).orElse(null);
    }

    public String setCurrentLevel(String token, String uid, String name, String author, Long workshopID)
    {
        VotingSession session = findActiveSession(token);
        if (session == null)
        {
            log.info("No active session found for token, creating default session...");
            CreateSessionRequest createReq = new CreateSessionRequest();
            createReq.setName("Default Session");
            session = createSession(token, createReq);
            if (session == null)
            {
                return "No active session found and failed to create default session";
            }
        }
        if (session.getState() == SessionState.PAUSED)
        {
            return "Session is currently paused";
        }

        Level level = levelService.getOrCreateLevel(uid, name, author, workshopID);

        if (session.getCurrentLevelUid() != null && !session.getCurrentLevelUid().equals(uid))
        {
            session.getLevelStatuses().put(session.getCurrentLevelUid(), LevelStatus.VOTING_FINISHED);

            if (session.isPlaylistMode())
            {
                final Map<String, LevelStatus> levelStatuses = session.getLevelStatuses();
                List<String> playlist = session.getPlaylist() != null ? session.getPlaylist() : List.of();
                boolean allPlaylistLevelsFinished = playlist.stream()
                        .allMatch(pUid -> levelStatuses.get(pUid) == LevelStatus.VOTING_FINISHED);
                if (allPlaylistLevelsFinished && !session.getPlaylist().contains(uid))
                {
                    session.setState(SessionState.FINISHED);
                    sessionService.save(session);
                    notifyUpdate(session.getHostId());
                    return "Session finished as all playlist levels were voted.";
                }
            }
        }

        if (!session.getPlayedLevels().contains(uid))
        {
            session.getPlayedLevels().add(uid);
        }
        session.setCurrentLevelUid(uid);
        session.getLevelStatuses().put(uid, LevelStatus.VOTING_ACTIVE);
        sessionService.save(session);
        log.info("Current level set to '{}' in session '{}'", name, session.getDisplayName());
        notifyUpdate(session.getHostId());
        return "Level set to: " + level.getName() + " by " + level.getAuthor();
    }

    public void updateLobbyTimer(String token, LobbyTimerRequest request)
    {
        VotingSession session = findActiveSession(token);
        if (session == null)
        {
            log.info("No active session found for token, creating default session for timer update...");
            CreateSessionRequest createReq = new CreateSessionRequest();
            createReq.setName("Default Session");
            session = createSession(token, createReq);
        }

        if (session == null)
        {
            return;
        }

        session.setRoundTime(request.getRoundTime());
        session.setLevelLoadedAtTime(request.getLevelLoadedAtTime());
        session.setCurrentTime(request.getCurrentTime());
        session.setLobbyGameState(LobbyGameState.fromInt(request.getGameState()));
        session.setLobbyTimer(formatLobbyTimer(session));
        session.setLastUsed(Instant.now());
        sessionService.save(session);

        notifyTimer(session.getHostId(), session);
    }

    private String formatLobbyTimer(VotingSession session)
    {
        if (session.getLobbyGameState() == app.yolobolo.zeepkist.apps.playlistvoting.model.enums.LobbyGameState.ROUND_ENDING)
        {
            return "Round Ending";
        }
        if (session.getLobbyGameState() == app.yolobolo.zeepkist.apps.playlistvoting.model.enums.LobbyGameState.PODIUM)
        {
            return "Podium";
        }

        double timeLeft = (session.getLevelLoadedAtTime() + session.getRoundTime()) - session.getCurrentTime();
        if (timeLeft < 0)
        {
            timeLeft = 0;
        }

        long seconds = (long) timeLeft;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0)
        {
            return String.format("%02d:%02d:%02d", hours, minutes, secs);
        }
        else
        {
            return String.format("%02d:%02d", minutes, secs);
        }
    }

    // --- Voting ---

    public String vote(String token, String platformUserId, String platformUsername, Platform platform, VoteOption option)
    {
        return processVote(findActiveSession(token), platformUserId, platformUsername, platform, option);
    }

    public String voteByHostId(String hostId, String platformUserId, String platformUsername, Platform platform, VoteOption option)
    {
        return processVote(sessionService.findActiveOrPausedSession(hostId), platformUserId, platformUsername, platform, option);
    }

    private String processVote(VotingSession session, String platformUserId, String platformUsername, Platform platform, VoteOption option)
    {
        if (session == null)
        {
            return "No active session found";
        }
        if (session.getState() == SessionState.PAUSED)
        {
            return "Session is currently paused";
        }
        if (session.getCurrentLevelUid() == null)
        {
            return "No level currently set";
        }

        String result;
        if (option == VoteOption.REMOVE)
        {
            result = voteProcessingService.removeVote(session.getId(), session.getCurrentLevelUid(), platformUserId, platform);
        }
        else
        {
            VoteOption actualOption = (option == VoteOption.IDK) ? (new Random().nextBoolean() ? VoteOption.YES : VoteOption.NO) : option;
            boolean canAbstain = session.getVotingMode() == VotingMode.ABSTAIN_ENABLED || session.isAllowAbstain();
            if (actualOption == VoteOption.ABSTAIN && !canAbstain)
            {
                return "Abstain votes are disabled for this session | " + getVoteSummary(session);
            }

            Optional<UserVote> existing = voteProcessingService.getVote(session.getId(), session.getCurrentLevelUid(), platformUserId, platform);
            if (existing.isPresent() && existing.get().getVote() == actualOption)
            {
                result = voteProcessingService.removeVote(session.getId(), session.getCurrentLevelUid(), platformUserId, platform);
            }
            else
            {
                result = voteProcessingService.castVote(session.getId(), session.getCurrentLevelUid(), platformUserId, platformUsername, platform, actualOption);
            }
        }

        sessionService.save(session);
        String summary = getVoteSummary(session);
        notifyUpdate(session.getHostId());
        return result + " | " + summary;
    }

    // --- Result ---

    public VotingResultResponse getResult(String token)
    {
        User user = findUserByToken(token);
        if (user == null)
        {
            return null;
        }
        return getResultByHostId(user.getId());
    }

    public VotingResultResponse getResultByHostId(String hostId)
    {
        VotingSession session = sessionService.findActiveOrPausedSession(hostId);
        return getResultBySession(session);
    }

    public VotingResultResponse getResultBySession(VotingSession session)
    {
        if (session == null)
        {
            return null;
        }
        return getResultBySessionAndLevel(session, session.getCurrentLevelUid());
    }

    public VotingResultResponse getResultBySessionAndLevel(VotingSession session, String levelUid)
    {
        if (session == null)
        {
            return null;
        }

        Level level = levelUid != null ? levelService.findById(levelUid).orElse(null) : null;
        List<UserVote> votes = levelUid != null ? voteProcessingService.getVotes(session.getId(), levelUid) : List.of();

        boolean canAbstain = session.getVotingMode() == VotingMode.ABSTAIN_ENABLED || session.isAllowAbstain();
        VotesResponse votesResponse = voteProcessingService.calculateVotesResponse(votes, canAbstain);

        return VotingResultResponse.builder()
                .sessionName(session.getDisplayName())
                .sessionState(session.getState().name())
                .roundTime(session.getRoundTime())
                .levelLoadedAtTime(session.getLevelLoadedAtTime())
                .currentTime(session.getCurrentTime())
                .lobbyGameState(session.getLobbyGameState())
                .lobbyTimer(session.getLobbyTimer())
                .level(LevelResponse.builder()
                        .levelUid(levelUid)
                        .levelName(level != null ? level.getName() : "None")
                        .levelAuthor(level != null ? level.getAuthor() : "None")
                        .workshopId(level != null ? level.getWorkshopID() : null)
                        .status(session.getLevelStatuses().getOrDefault(levelUid, LevelStatus.VOTING_ACTIVE))
                        .build())
                .votes(votesResponse)
                .veto(session.getVetoes() != null ? session.getVetoes().get(levelUid) : null)
                .build();
    }

    private String getVoteSummary(VotingSession session)
    {
        List<UserVote> votes = voteProcessingService.getVotes(session.getId(), session.getCurrentLevelUid());
        boolean canAbstain = session.getVotingMode() == VotingMode.ABSTAIN_ENABLED || session.isAllowAbstain();
        VotesResponse votesResponse = voteProcessingService.calculateVotesResponse(votes, canAbstain);
        if (canAbstain)
        {
            return "%d/%d/%d (y/n/a)".formatted(votesResponse.getYes(), votesResponse.getNo(), votesResponse.getAbstain());
        }
        else
        {
            return "%d/%d (y/n)".formatted(votesResponse.getYes(), votesResponse.getNo());
        }
    }

    // --- Reset ---

    public String reset(String token)
    {
        VotingSession session = findActiveSession(token);
        if (session == null)
        {
            return "No active session found";
        }
        if (session.getState() == SessionState.PAUSED)
        {
            return "Session is currently paused";
        }

        Level level = getCurrentLevel(token);
        String levelInfo = level != null ? level.getName() + " by " + level.getAuthor() : "Previous Level";

        List<UserVote> votes = level != null
                ? voteProcessingService.getVotes(session.getId(), level.getUid())
                : List.of();

        VotesResponse votesResponse = voteProcessingService.calculateVotesResponse(votes, session.isAllowAbstain());

        session.setCurrentLevelUid(null);
        sessionService.save(session);

        String voteSummary = session.isAllowAbstain()
                ? "%d/%d/%d (y/n/a)".formatted(votesResponse.getYes(), votesResponse.getNo(), votesResponse.getAbstain())
                : "%d/%d (y/n)".formatted(votesResponse.getYes(), votesResponse.getNo());

        String message = "RESULT<br>%s<br>----------------<br>%s".formatted(levelInfo, voteSummary);
        log.info("Reset - {} | {}", levelInfo, voteSummary);
        notifyUpdate(session.getHostId());
        return message;
    }

    public PlaylistResponse getPlaylist(String token)
    {
        VotingSession session = findActiveSession(token);
        if (session == null)
        {
            return null;
        }

        PlaylistResponse response = new PlaylistResponse();
        response.setSessionName(session.getDisplayName());

        List<String> playedLevels = session.getPlayedLevels() != null ? session.getPlayedLevels() : List.of();
        List<PlaylistResponse.LevelWithVotes> levels = playedLevels.stream()
                .map(uid ->
                {
                    Level level = levelService.findById(uid).orElse(null);
                    if (level == null)
                    {
                        return null;
                    }

                    PlaylistResponse.LevelWithVotes lwv = new PlaylistResponse.LevelWithVotes();
                    lwv.setUid(uid);
                    lwv.setName(level.getName());
                    lwv.setAuthor(level.getAuthor());
                    lwv.setVotes(voteProcessingService.getVotes(session.getId(), uid));
                    return lwv;
                })
                .filter(Objects::nonNull)
                .toList();

        response.setLevels(levels);
        log.debug("Playlist requested for session '{}', {} levels", session.getDisplayName(), levels.size());
        return response;
    }

    public Map<String, Object> getDashboardData(String token, String hostId)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        User viewer = findUserByToken(token);
        String viewerId = viewer != null ? viewer.getId() : null;

        VotingSession activeSession = null;
        if (hostId != null)
        {
            activeSession = sessionService.findActiveOrPausedSession(hostId);
        }
        else if (token != null)
        {
            activeSession = findActiveSession(token);
        }

        boolean isOwner = activeSession != null && activeSession.getHostId().equals(viewerId);
        boolean isConnected = activeSession != null && activeSession.isConnected();

        data.put("isConnected", isConnected);

        if (activeSession != null && (isOwner || isConnected))
        {
            data.put("activeSessionName", activeSession.getDisplayName());
            data.put("roundTime", activeSession.getRoundTime());
            data.put("levelLoadedAtTime", activeSession.getLevelLoadedAtTime());
            data.put("currentTime", activeSession.getCurrentTime());
            data.put("lobbyGameState", activeSession.getLobbyGameState());
            data.put("lobbyTimer", activeSession.getLobbyTimer());
            data.put("playlistMode", activeSession.isPlaylistMode());
            data.put("playlist", activeSession.getPlaylist());

            if (token != null)
            {
                if (viewer != null && viewer.getSteamId() != null && activeSession.getCurrentLevelUid() != null)
                {
                    Optional<UserVote> viewerVote = voteProcessingService.getVote(activeSession.getId(), activeSession.getCurrentLevelUid(), viewer.getSteamId(), Platform.STEAM);
                    data.put("currentVote", viewerVote.map(v -> v.getVote().name()).orElse(null));
                }
            }

            if (activeSession.getCurrentLevelUid() != null)
            {
                Level currentLevel = levelService.findById(activeSession.getCurrentLevelUid()).orElse(null);
                if (currentLevel != null)
                {
                    Map<String, Object> lvl = new LinkedHashMap<>();
                    lvl.put("name", currentLevel.getName());
                    lvl.put("author", currentLevel.getAuthor());
                    lvl.put("uid", currentLevel.getUid());
                    lvl.put("workshopID", currentLevel.getWorkshopID() != null ? currentLevel.getWorkshopID() : 0L);
                    lvl.put("thumbnailUrl", currentLevel.getThumbnailUrl());
                    data.put("currentLevel", lvl);
                }
                else
                {
                    data.put("currentLevel", null);
                }
                data.put("currentVotes", getVotesMap(activeSession.getId(), activeSession.getCurrentLevelUid()));
                data.put("currentVeto", activeSession.getVetoes() != null ? activeSession.getVetoes().get(activeSession.getCurrentLevelUid()) : null);
            }
            else
            {
                data.put("currentLevel", null);
                data.put("currentVotes", null);
            }
        }
        else
        {
            data.put("activeSessionName", null);
            data.put("currentLevel", null);
            data.put("currentVotes", null);
        }

        // All sessions (either for host, or all active for guests)
        List<VotingSession> sessionsToMap;
        if (hostId != null)
        {
            if (isOwner)
            {
                sessionsToMap = sessionService.findByHostId(hostId);
            }
            else
            {
                // For guests, only show the current session if it's connected
                if (activeSession != null && isConnected)
                {
                    sessionsToMap = List.of(activeSession);
                }
                else
                {
                    sessionsToMap = List.of();
                }
            }
        }
        else
        {
            // Global view - only show connected active sessions
            sessionsToMap = sessionService.findAllActive().stream()
                    .filter(VotingSession::isConnected)
                    .collect(Collectors.toList());
        }

        var sessions = sessionsToMap.stream().map(s ->
        {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("id", s.getId());
            sm.put("name", s.getDisplayName());
            sm.put("displayName", s.getDisplayName());
            sm.put("state", s.getState() != null ? s.getState().name() : "ACTIVE");
            sm.put("lobbyTimer", s.getLobbyTimer());
            sm.put("playlistMode", s.isPlaylistMode());
            sm.put("votingMode", s.getVotingMode() != null ? s.getVotingMode().name() : "NORMAL");
            sm.put("allowAbstain", s.isAllowAbstain());
            sm.put("playlist", s.getPlaylist() != null ? s.getPlaylist() : List.of());
            sm.put("playedLevels", s.getPlayedLevels() != null ? s.getPlayedLevels() : List.of());
            sm.put("levelStatuses", s.getLevelStatuses() != null ? s.getLevelStatuses() : Map.of());
            sm.put("vetoes", s.getVetoes() != null ? s.getVetoes() : Map.of());
            sm.put("createdAt", s.getCreatedAt() != null ? s.getCreatedAt().toEpochMilli() : null);
            sm.put("connected", s.isConnected());

            if (s.getCurrentLevelUid() != null)
            {
                levelService.findById(s.getCurrentLevelUid()).ifPresent(levelInfo ->
                {
                    Map<String, Object> clm = new LinkedHashMap<>();
                    clm.put("uid", levelInfo.getUid());
                    clm.put("name", levelInfo.getName());
                    clm.put("author", levelInfo.getAuthor());
                    clm.put("workshopID", levelInfo.getWorkshopID() != null ? levelInfo.getWorkshopID() : 0L);
                    clm.put("thumbnailUrl", levelInfo.getThumbnailUrl());
                    sm.put("currentLevel", clm);
                });
            }

            List<String> playlistUids = s.getPlaylist() != null ? s.getPlaylist() : List.of();
            List<String> playedUids = s.getPlayedLevels() != null ? s.getPlayedLevels() : List.of();

            // All levels associated with this session
            Set<String> allUids = new LinkedHashSet<>();
            if (s.getCurrentLevelUid() != null)
            {
                allUids.add(s.getCurrentLevelUid());
            }
            allUids.addAll(playlistUids);
            allUids.addAll(playedUids);

            var levels = allUids.stream().map(uid ->
            {
                Level levelInfo = levelService.findById(uid).orElse(null);
                var lvlVotes = voteProcessingService.getVotes(s.getId(), uid);

                Map<String, Object> lm = getVotesMap(lvlVotes, s.isAllowAbstain());
                lm.put("uid", uid);
                lm.put("name", levelInfo != null ? levelInfo.getName() : uid);
                lm.put("author", levelInfo != null ? levelInfo.getAuthor() : "");
                lm.put("workshopID", (levelInfo != null && levelInfo.getWorkshopID() != null) ? levelInfo.getWorkshopID() : 0L);
                lm.put("thumbnailUrl", levelInfo != null ? levelInfo.getThumbnailUrl() : null);
                lm.put("isCurrent", uid.equals(s.getCurrentLevelUid()));
                lm.put("status", s.getLevelStatuses() != null ? s.getLevelStatuses().getOrDefault(uid, LevelStatus.VOTING_ACTIVE) : LevelStatus.VOTING_ACTIVE);
                lm.put("veto", s.getVetoes() != null ? s.getVetoes().get(uid) : null);

                var individualVotes = lvlVotes.stream()
                        .sorted(Comparator.comparing(UserVote::getModifiedAt).reversed())
                        .map(v ->
                        {
                            Map<String, Object> vm = new LinkedHashMap<>();
                            vm.put("id", v.getId());
                            vm.put("user", v.getPlatformUserId());
                            vm.put("username", v.getPlatformUsername());
                            vm.put("platform", v.getPlatform().name());
                            vm.put("vote", v.getVote().name());
                            vm.put("timestamp", v.getModifiedAt() != null ? v.getModifiedAt().toEpochMilli() : null);
                            return vm;
                        }).collect(Collectors.toList());
                lm.put("votes", individualVotes);

                return lm;
            }).collect(Collectors.toList());

            sm.put("levels", levels);
            return sm;
        }).collect(Collectors.toList());

        data.put("sessions", sessions);
        return data;
    }

    public void resetVotes(String sessionId, String hostId)
    {
        VotingSession session = sessionService.findById(sessionId).orElseThrow();
        if (!session.getHostId().equals(hostId))
        {
            throw new SecurityException("Not the owner");
        }
        voteProcessingService.resetVotes(sessionId);
        if (session.getLevelStatuses() != null)
        {
            session.getLevelStatuses().clear();
            sessionService.save(session);
        }
        notifyUpdate(hostId);
    }

    public void deleteVote(String voteId, String hostId)
    {
        UserVote vote = voteRepository.findById(voteId).orElseThrow();
        VotingSession session = sessionService.findById(vote.getSessionId()).orElseThrow();
        if (!session.getHostId().equals(hostId))
        {
            throw new SecurityException("Not the owner");
        }
        voteProcessingService.deleteVoteById(voteId);
        notifyUpdate(hostId);
    }

    private Map<String, Object> getVotesMap(String sessionId, String levelUid)
    {
        VotingSession session = sessionService.findById(sessionId).orElse(null);
        boolean allowAbstain = session != null && session.isAllowAbstain();
        return getVotesMap(voteProcessingService.getVotes(sessionId, levelUid), allowAbstain);
    }

    private Map<String, Object> getVotesMap(List<UserVote> votes, boolean allowAbstain)
    {
        Map<String, Object> map = voteProcessingService.calculateVotesMap(votes, allowAbstain);
        if (map == null)
        {
            // Defensive for mocks or unexpected service failures
            map = new LinkedHashMap<>();
            map.put("yes", 0L);
            map.put("no", 0L);
            map.put("total", 0L);
            map.put("allowAbstain", allowAbstain);
        }
        long yes = map.get("yes") != null ? ((Number) map.get("yes")).longValue() : 0L;
        long no = map.get("no") != null ? ((Number) map.get("no")).longValue() : 0L;
        double yesPct = (yes + no) > 0 ? (yes * 100.0 / (yes + no)) : 0;
        map.put("yesPct", Math.round(yesPct));
        return map;
    }

    public List<VotingSession> findAllSessions(String hostId)
    {
        return sessionService.findByHostId(hostId);
    }

    public long countSessionsByHostId(String hostId)
    {
        return sessionService.findByHostId(hostId).size();
    }

    public long countTotalVotesByHostId(String hostId)
    {
        List<VotingSession> sessions = sessionService.findByHostId(hostId);
        long totalVotes = 0;
        for (VotingSession session : sessions)
        {
            totalVotes += voteRepository.countBySessionId(session.getId());
        }
        return totalVotes;
    }

    public Map<VoteOption, Long> getVoteStatsByHostId(String hostId)
    {
        List<VotingSession> sessions = sessionService.findByHostId(hostId);
        Map<VoteOption, Long> stats = new EnumMap<>(VoteOption.class);
        for (VoteOption option : VoteOption.values())
        {
            stats.put(option, 0L);
        }

        for (VotingSession session : sessions)
        {
            for (VoteOption option : VoteOption.values())
            {
                stats.put(option, stats.get(option) + voteRepository.countBySessionIdAndVote(session.getId(), option));
            }
        }
        return stats;
    }

    public List<VoteDetailResponse> getVoteDetails(String hostId, String sessionId, VoteOption option)
    {
        List<String> sessionIds;
        if (sessionId != null)
        {
            sessionIds = List.of(sessionId);
        }
        else
        {
            VotingSession active = sessionService.findActiveOrPausedSession(hostId);
            if (active == null)
            {
                return List.of();
            }
            sessionIds = List.of(active.getId());
        }

        List<UserVote> votesWithOption = voteRepository.findBySessionIdInAndVote(sessionIds, option);
        List<String> levelUids = votesWithOption.stream()
                .map(UserVote::getLevelUid)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<UserVote> allVotesForLevels = voteRepository.findBySessionIdInAndLevelUidIn(sessionIds, levelUids);

        Map<String, List<UserVote>> groupedAllVotes = allVotesForLevels.stream()
                .collect(Collectors.groupingBy(UserVote::getLevelUid));

        return levelUids.stream()
                .map(uid ->
                {
                    Level level = levelService.findById(uid).orElse(null);
                    List<UserVote> levelVotes = groupedAllVotes.getOrDefault(uid, List.of());

                    Map<VoteOption, Long> counts = new EnumMap<>(VoteOption.class);
                    for (VoteOption vo : VoteOption.values())
                    {
                        counts.put(vo, 0L);
                    }
                    levelVotes.forEach(v -> counts.put(v.getVote(), counts.get(v.getVote()) + 1));

                    Instant lastVoted = levelVotes.stream()
                            .map(UserVote::getCreatedAt)
                            .max(Comparator.naturalOrder())
                            .orElse(Instant.now());

                    List<VoteDetailResponse.IndividualVoteDTO> individual = levelVotes.stream()
                            .sorted(Comparator.comparing(UserVote::getCreatedAt).reversed())
                            .limit(50)
                            .map(v -> VoteDetailResponse.IndividualVoteDTO.builder()
                                    .username(v.getPlatformUsername())
                                    .vote(v.getVote())
                                    .date(v.getCreatedAt())
                                    .build())
                            .toList();

                    return VoteDetailResponse.builder()
                            .levelUid(uid)
                            .levelName(level != null ? level.getName() : "Unknown")
                            .levelAuthor(level != null ? level.getAuthor() : "Unknown")
                            .workshopID(level != null && level.getWorkshopID() != null ? String.valueOf(level.getWorkshopID()) : null)
                            .lastVotedAt(lastVoted)
                            .voteCounts(counts)
                            .individualVotes(individual)
                            .build();
                })
                .sorted(Comparator.comparing(VoteDetailResponse::getLastVotedAt).reversed())
                .toList();
    }

    public byte[] downloadPlaylist(String id, double roundLength, boolean shuffle, String name, String hostId, String type)
    {
        VotingSession votingSession = sessionService.findById(id).orElse(null);
        if (votingSession == null || !votingSession.getHostId().equals(hostId))
        {
            return null;
        }

        List<String> uidsToExport;
        if ("tovote".equalsIgnoreCase(type) && votingSession.getPlaylist() != null)
        {
            uidsToExport = votingSession.getPlaylist().stream()
                    .filter(uid -> votingSession.getLevelStatuses().get(uid) != LevelStatus.VOTING_FINISHED)
                    .toList();
        }
        else if ("yes".equalsIgnoreCase(type) && votingSession.getPlayedLevels() != null)
        {
            uidsToExport = votingSession.getPlayedLevels().stream()
                    .filter(uid ->
                    {
                        VetoValue veto = votingSession.getVetoes() != null ? votingSession.getVetoes().get(uid) : null;
                        if (veto == VetoValue.YES)
                        {
                            return true;
                        }
                        if (veto == VetoValue.NO)
                        {
                            return false;
                        }

                        List<UserVote> votes = voteProcessingService.getVotes(votingSession.getId(), uid);
                        VotesResponse vr = voteProcessingService.calculateVotesResponse(votes, votingSession.isAllowAbstain());
                        return vr.getYes() > vr.getNo();
                    })
                    .toList();
        }
        else if ("no".equalsIgnoreCase(type) && votingSession.getPlayedLevels() != null)
        {
            uidsToExport = votingSession.getPlayedLevels().stream()
                    .filter(uid ->
                    {
                        VetoValue veto = votingSession.getVetoes() != null ? votingSession.getVetoes().get(uid) : null;
                        if (veto == VetoValue.NO)
                        {
                            return true;
                        }
                        if (veto == VetoValue.YES)
                        {
                            return false;
                        }

                        List<UserVote> votes = voteProcessingService.getVotes(votingSession.getId(), uid);
                        VotesResponse votesResponse = voteProcessingService.calculateVotesResponse(votes, votingSession.isAllowAbstain());
                        return votesResponse.getNo() >= votesResponse.getYes() && votesResponse.getNo() > 0;
                    })
                    .toList();
        }
        else if ("all".equalsIgnoreCase(type) && votingSession.getPlaylist() != null)
        {
            uidsToExport = votingSession.getPlaylist();
        }
        else
        {
            uidsToExport = votingSession.getPlayedLevels() != null ? votingSession.getPlayedLevels() : new ArrayList<>();
        }

        var levels = uidsToExport.stream()
                .map(uid -> levelService.findById(uid).orElse(null))
                .filter(Objects::nonNull)
                .map(l ->
                {
                    var lm = new LinkedHashMap<String, Object>();
                    lm.put("UID", l.getUid());
                    lm.put("WorkshopID", l.getWorkshopID() != null ? l.getWorkshopID() : 0);
                    lm.put("Name", l.getName());
                    lm.put("Collaborators", "");
                    lm.put("OverrideAuthorName", "");
                    lm.put("Author", l.getAuthor());
                    lm.put("played", votingSession.getLevelStatuses().get(l.getUid()) == LevelStatus.VOTING_FINISHED);
                    return lm;
                }).toList();

        String playlistName = (name != null && !name.isBlank()) ? name : votingSession.getDisplayName();
        if ("tovote".equalsIgnoreCase(type))
        {
            playlistName += " (Remaining)";
        }
        else if ("yes".equalsIgnoreCase(type))
        {
            playlistName += " (Accepted)";
        }
        else if ("no".equalsIgnoreCase(type))
        {
            playlistName += " (Rejected)";
        }
        else if ("all".equalsIgnoreCase(type))
        {
            playlistName += " (Full)";
        }

        Map<String, Object> playlist = new LinkedHashMap<>();
        playlist.put("name", playlistName);
        playlist.put("amountOfLevels", levels.size());
        playlist.put("roundLength", roundLength);
        playlist.put("shufflePlaylist", shuffle);
        playlist.put("UID", new ArrayList<>());
        playlist.put("levels", levels);
        tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(playlist);
    }

    public String getPlaylistFilename(String sessionId, String name)
    {
        VotingSession votingSession = sessionService.findById(sessionId).orElse(null);
        String playlistName;
        if (name != null && !name.isBlank())
        {
            playlistName = name;
        }
        else if (votingSession != null)
        {
            playlistName = votingSession.getDisplayName();
        }
        else
        {
            playlistName = "playlist";
        }
        return playlistName.replaceAll("[^a-zA-Z0-9_-]", "_") + ".zeeplist";
    }

    public void updatePlaylist(String token, UpdatePlaylistRequest request)
    {
        VotingSession session = findActiveSession(token);
        if (session != null)
        {
            updateSessionPlaylist(session.getId(), session.getHostId(), request);
        }
    }

    public void updateSessionPlaylist(String sessionId, String hostId, UpdatePlaylistRequest request)
    {
        sessionService.findById(sessionId).ifPresent(session ->
        {
            if (session.getHostId().equals(hostId))
            {
                if (request.getZeeplist() != null)
                {
                    applyZeeplistToSession(session, request.getZeeplist());
                }
                else if (request.getPlaylist() != null)
                {
                    session.setPlaylist(request.getPlaylist());
                }

                session.setPlaylistMode(request.isPlaylistMode());
                sessionService.save(session);
                notifyUpdate(session.getHostId());
            }
        });
    }

    public void updateSessionSettings(String sessionId, String hostId, SessionSettingsRequest request)
    {
        sessionService.findById(sessionId).ifPresent(session ->
        {
            if (session.getHostId().equals(hostId))
            {
                if (request.getDisplayName() != null)
                {
                    session.setDisplayName(request.getDisplayName());
                }
                if (request.getPlaylist() != null)
                {
                    session.setPlaylist(request.getPlaylist());
                }
                session.setPlaylistMode(request.isPlaylistMode());
                if (request.getVotingMode() != null)
                {
                    session.setVotingMode(request.getVotingMode());
                    session.setAllowAbstain(request.getVotingMode() == app.yolobolo.zeepkist.apps.playlistvoting.model.enums.VotingMode.ABSTAIN_ENABLED);
                }

                if (request.getState() != null && request.getState() != session.getState())
                {
                    sessionService.updateSessionState(sessionId, request.getState(), hostId);
                }
                else
                {
                    sessionService.save(session);
                }
                notifyUpdate(session.getHostId());
            }
        });
    }

    public void vetoLevel(String token, String uid, VetoValue veto)
    {
        VotingSession session = findActiveSession(token);
        if (session == null)
        {
            return;
        }
        String levelUid = (uid != null && !uid.isEmpty()) ? uid : session.getCurrentLevelUid();
        if (levelUid != null)
        {
            vetoLevel(session.getId(), levelUid, veto, session.getHostId());
        }
    }

    public void vetoLevel(String sessionId, String levelUid, VetoValue veto, String hostId)
    {
        sessionService.findById(sessionId).ifPresent(session ->
        {
            if (!session.getHostId().equals(hostId))
            {
                return;
            }
            if (session.getVetoes() == null)
            {
                session.setVetoes(new HashMap<>());
            }
            session.getVetoes().put(levelUid, veto);
            sessionService.save(session);
            notifyUpdate(session.getHostId());
        });
    }

    public void updateLevelStatus(String token, String uid, LevelStatus status)
    {
        VotingSession session = findActiveSession(token);
        if (session == null)
        {
            return;
        }
        String levelUid = (uid != null && !uid.isEmpty()) ? uid : session.getCurrentLevelUid();
        if (levelUid != null)
        {
            updateLevelStatus(session.getId(), levelUid, status, session.getHostId());
        }
    }

    public void updateLevelStatus(String sessionId, String levelUid, LevelStatus status, String hostId)
    {
        sessionService.findById(sessionId).ifPresent(session ->
        {
            if (!session.getHostId().equals(hostId))
            {
                return;
            }
            if (session.getLevelStatuses() == null)
            {
                session.setLevelStatuses(new HashMap<>());
            }
            session.getLevelStatuses().put(levelUid, status != null ? status : LevelStatus.VOTING_ACTIVE);
            sessionService.save(session);
            notifyUpdate(session.getHostId());
        });
    }

    public List<Map<String, Object>> findAllHostsWithSessions()
    {
        List<VotingSession> allSessions = sessionService.findAll();
        Map<String, List<VotingSession>> sessionsByHost = allSessions.stream()
                .collect(Collectors.groupingBy(VotingSession::getHostId));

        return sessionsByHost.entrySet().stream()
                .map(entry ->
                {
                    String hostId = entry.getKey();
                    List<VotingSession> hostSessions = entry.getValue();
                    User host = userService.findById(hostId).orElse(null);

                    Map<String, Object> hostMap = new LinkedHashMap<>();
                    hostMap.put("hostId", hostId);
                    hostMap.put("displayName", host != null ? host.getDisplayName() : "Unknown");
                    hostMap.put("steamId", host != null ? host.getSteamId() : null);
                    hostMap.put("avatarUrl", host != null ? host.getAvatarUrl() : null);
                    hostMap.put("sessions", hostSessions);
                    hostMap.put("sessionCount", hostSessions.size());

                    VotingSession activeSession = sessionService.findActiveOrPausedSession(hostId);
                    if (activeSession != null && activeSession.isConnected())
                    {
                        Map<String, Object> sessionMap = new LinkedHashMap<>();
                        sessionMap.put("id", activeSession.getId());
                        sessionMap.put("displayName", activeSession.getDisplayName());
                        sessionMap.put("connected", true);
                        sessionMap.put("state", activeSession.getState().name());
                        sessionMap.put("totalLevels", (activeSession.getPlaylist() != null ? activeSession.getPlaylist().size() : 0));
                        sessionMap.put("currentLevel", null);

                        if (activeSession.getCurrentLevelUid() != null)
                        {
                            levelService.findById(activeSession.getCurrentLevelUid()).ifPresent(level ->
                            {
                                Map<String, Object> lm = new HashMap<>();
                                lm.put("name", level.getName());
                                lm.put("author", level.getAuthor());
                                sessionMap.put("currentLevel", lm);
                            });
                        }
                        hostMap.put("activeSession", sessionMap);
                    }
                    else
                    {
                        hostMap.put("activeSession", null);
                    }

                    return hostMap;
                })
                .sorted(Comparator.comparing(m -> (String) m.get("displayName")))
                .collect(Collectors.toList());
    }

    public PlaylistVotingSessionInfoDto getSessionInfo(String token)
    {
        VotingSession session = findActiveSession(token);
        return getPlaylistVotingSessionInfoDto(session);
    }

    public PlaylistVotingSessionInfoDto getPlaylistVotingSessionInfoDto(VotingSession session)
    {
        if (session == null)
        {
            return null;
        }

        List<String> playlist = session.getPlaylist() != null ? session.getPlaylist() : List.of();
        int totalLevels = playlist.size();
        int finalizedCount = (int) playlist.stream()
                .filter(uid -> session.getLevelStatuses().get(uid) == LevelStatus.VOTING_FINISHED)
                .count();

        int historyCount = session.getPlayedLevels() != null ? session.getPlayedLevels().size() : 0;

        return PlaylistVotingSessionInfoDto.builder()
                .id(session.getId())
                .displayName(session.getDisplayName())
                .state(session.getState())
                .playlistModeEnabled(session.isPlaylistMode())
                .hasPlaylist(!playlist.isEmpty())
                .playlistLevelCount(totalLevels)
                .currentLevelUid(session.getCurrentLevelUid())
                .totalLevelCount(totalLevels)
                .finalizedLevelCount(finalizedCount)
                .remainingLevelCount(totalLevels - finalizedCount)
                .historyLevelCount(historyCount)
                .latestResult(getResultBySession(session))
                .build();
    }

    public VotingSession findLatestActiveOrResumableSession(String token)
    {
        User user = findUserByToken(token);
        if (user == null)
        {
            return null;
        }
        return sessionService.findActiveOrPausedSession(user.getId());
    }

    public void setPlaylistMode(String token, boolean enabled)
    {
        VotingSession session = findActiveSession(token);
        if (session != null)
        {
            session.setPlaylistMode(enabled);
            sessionService.save(session);
            notifyUpdate(session.getHostId());
        }
    }

    public void finalizeLevel(String token, String levelUid)
    {
        VotingSession session = findActiveSession(token);
        if (session != null && levelUid != null)
        {
            if (!session.getPlayedLevels().contains(levelUid))
            {
                session.getPlayedLevels().add(levelUid);
            }
            session.getLevelStatuses().put(levelUid, LevelStatus.VOTING_FINISHED);
            sessionService.save(session);
            notifyUpdate(session.getHostId());
        }
    }

    public void resetLevelVotes(String token, String levelUid)
    {
        VotingSession session = findActiveSession(token);
        if (session != null && levelUid != null)
        {
            voteRepository.deleteBySessionIdAndLevelUid(session.getId(), levelUid);
            if (session.getLevelStatuses() != null)
            {
                session.getLevelStatuses().remove(levelUid);
                sessionService.save(session);
            }
            notifyUpdate(session.getHostId());
        }
    }

    public VotingResultResponse getLevelResult(String token, String levelUid)
    {
        VotingSession session = findActiveSession(token);
        if (session == null)
        {
            return null;
        }
        return getResultBySessionAndLevel(session, levelUid);
    }
}
