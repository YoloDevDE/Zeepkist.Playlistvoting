package app.yolobolo.zeepkist.apps.playlistvoting.service;

import app.yolobolo.zeepkist.apps.playlistvoting.model.Level;
import app.yolobolo.zeepkist.apps.playlistvoting.model.UserVote;
import app.yolobolo.zeepkist.apps.playlistvoting.model.VotingSession;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.ZeeplistDTO;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.request.CreateSessionRequest;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.request.SessionSettingsRequest;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.request.UpdatePlaylistRequest;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.response.LevelResponse;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.response.PlaylistResponse;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.response.VotesResponse;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.response.VotingResultResponse;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.Platform;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.SessionState;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.VoteOption;
import app.yolobolo.zeepkist.apps.playlistvoting.repository.UserVoteRepository;
import app.yolobolo.zeepkist.common.model.User;
import app.yolobolo.zeepkist.common.repository.UserRepository;
import app.yolobolo.zeepkist.common.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

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
        messagingTemplate.convertAndSend("/topic/dashboard/" + hostId, "update");
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
            log.info("Sending result to topic: /topic/votes/{}", topicId);
            messagingTemplate.convertAndSend("/topic/votes/" + topicId, result);
        }
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
        if (session != null)
        {
            session.setPlaylistMode(request.isPlaylistMode());
            session.setAllowAbstain(request.isAllowAbstain());
            if (request.getZeeplist() != null)
            {
                applyZeeplistToSession(session, request.getZeeplist());
            }
            sessionService.save(session);
        }
        notifyUpdate(user.getId());
        return session;
    }

    private void applyZeeplistToSession(VotingSession session, ZeeplistDTO zeeplist)
    {
        List<String> playlistUids = new ArrayList<>();
        if (zeeplist.getLevels() != null)
        {
            for (ZeeplistDTO.ZeeplistLevelDTO levelDto : zeeplist.getLevels())
            {
                levelService.getOrCreateLevel(
                        levelDto.getUid(),
                        levelDto.getName(),
                        levelDto.getAuthor(),
                        levelDto.getWorkshopId()
                );
                playlistUids.add(levelDto.getUid());

                if (levelDto.isPlayed())
                {
                    if (!session.getPlayedLevels().contains(levelDto.getUid()))
                    {
                        session.getPlayedLevels().add(levelDto.getUid());
                    }
                    session.getLevelStatuses().put(levelDto.getUid(), "VOTING_FINISHED");
                }
            }
        }
        session.setPlaylist(playlistUids);
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
            session.getLevelStatuses().put(session.getCurrentLevelUid(), "VOTING_FINISHED");

            if (session.isPlaylistMode())
            {
                final Map<String, String> levelStatuses = session.getLevelStatuses();
                List<String> playlist = session.getPlaylist() != null ? session.getPlaylist() : List.of();
                boolean allPlaylistLevelsFinished = playlist.stream()
                        .allMatch(pUid -> "VOTING_FINISHED".equals(levelStatuses.get(pUid)));
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
        session.getLevelStatuses().put(uid, "VOTING_ACTIVE");
        sessionService.save(session);
        log.info("Current level set to '{}' in session '{}'", name, session.getDisplayName());
        notifyUpdate(session.getHostId());
        return "Level set to: " + level.getName() + " by " + level.getAuthor();
    }

    public void updateLobbyTimer(String token, String timer)
    {
        VotingSession session = findActiveSession(token);
        if (session == null)
        {
            log.info("No active session found for token, creating default session for timer update...");
            CreateSessionRequest createReq = new CreateSessionRequest();
            createReq.setName("Default Session");
            session = createSession(token, createReq);
        }

        if (session != null)
        {
            session.setLobbyTimer(timer);
            sessionService.save(session);
            notifyUpdate(session.getHostId());
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
            if (actualOption == VoteOption.ABSTAIN && !session.isAllowAbstain())
            {
                return "Abstain votes are disabled for this session | " + getVoteSummary(session);
            }
            result = voteProcessingService.castVote(session.getId(), session.getCurrentLevelUid(), platformUserId, platformUsername, platform, actualOption);
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
        if (session == null)
        {
            return null;
        }

        Level level = session.getCurrentLevelUid() != null ? levelService.findById(session.getCurrentLevelUid()).orElse(null) : null;
        List<UserVote> votes = session.getCurrentLevelUid() != null ? voteProcessingService.getVotes(session.getId(), session.getCurrentLevelUid()) : List.of();

        VotesResponse votesResponse = voteProcessingService.calculateVotesResponse(votes, session.isAllowAbstain());

        return VotingResultResponse.builder()
                .sessionName(session.getDisplayName())
                .sessionState(session.getState().name())
                .lobbyTimer(session.getLobbyTimer())
                .level(LevelResponse.builder()
                        .levelUid(session.getCurrentLevelUid())
                        .levelName(level != null ? level.getName() : "None")
                        .levelAuthor(level != null ? level.getAuthor() : "None")
                        .workshopId(level != null ? level.getWorkshopID() : null)
                        .status(session.getLevelStatuses().getOrDefault(session.getCurrentLevelUid(), "VOTING_ACTIVE"))
                        .build())
                .votes(votesResponse)
                .veto(session.getVetoes() != null ? session.getVetoes().get(session.getCurrentLevelUid()) : null)
                .build();
    }

    private String getVoteSummary(VotingSession session)
    {
        List<UserVote> votes = voteProcessingService.getVotes(session.getId(), session.getCurrentLevelUid());
        VotesResponse votesResponse = voteProcessingService.calculateVotesResponse(votes, session.isAllowAbstain());
        if (session.isAllowAbstain())
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

        VotingSession activeSession = null;
        if (token != null)
        {
            activeSession = findActiveSession(token);
        }
        else if (hostId != null)
        {
            activeSession = sessionService.findActiveOrPausedSession(hostId);
        }

        if (activeSession != null)
        {
            data.put("activeSessionName", activeSession.getDisplayName());
            data.put("lobbyTimer", activeSession.getLobbyTimer());
            data.put("playlistMode", activeSession.isPlaylistMode());
            data.put("playlist", activeSession.getPlaylist());
            if (activeSession.getCurrentLevelUid() != null)
            {
                Level currentLevel = levelService.findById(activeSession.getCurrentLevelUid()).orElse(null);
                if (currentLevel != null)
                {
                    Map<String, Object> lvl = new LinkedHashMap<>();
                    lvl.put("name", currentLevel.getName());
                    lvl.put("author", currentLevel.getAuthor());
                    lvl.put("uid", currentLevel.getUid());
                    lvl.put("workshopID", currentLevel.getWorkshopID());
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
            sessionsToMap = sessionService.findByHostId(hostId);
        }
        else
        {
            sessionsToMap = sessionService.findAllActive();
        }

        var sessions = sessionsToMap.stream().map(s ->
        {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("id", s.getId());
            sm.put("name", s.getDisplayName());
            sm.put("state", s.getState().name());
            sm.put("lobbyTimer", s.getLobbyTimer());
            sm.put("createdAt", s.getCreatedAt() != null ? s.getCreatedAt().toEpochMilli() : null);

            List<String> playedLevels = s.getPlayedLevels() != null ? s.getPlayedLevels() : List.of();
            var levelsList = new ArrayList<>(playedLevels);
            Collections.reverse(levelsList);

            var levels = levelsList.stream().map(uid ->
            {
                Level levelInfo = levelService.findById(uid).orElse(null);
                var lvlVotes = voteProcessingService.getVotes(s.getId(), uid);

                Map<String, Object> lm = getVotesMap(lvlVotes, s.isAllowAbstain());
                lm.put("uid", uid);
                lm.put("name", levelInfo != null ? levelInfo.getName() : uid);
                lm.put("author", levelInfo != null ? levelInfo.getAuthor() : "");
                lm.put("workshopID", levelInfo != null ? levelInfo.getWorkshopID() : 0);
                lm.put("thumbnailUrl", levelInfo != null ? levelInfo.getThumbnailUrl() : null);
                lm.put("isCurrent", uid.equals(s.getCurrentLevelUid()));
                lm.put("status", s.getLevelStatuses().getOrDefault(uid, "VOTING_ACTIVE"));
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
        long yes = (Long) map.get("yes");
        long no = (Long) map.get("no");
        double yesPct = (yes + no) > 0 ? (yes * 100.0 / (yes + no)) : 0;
        map.put("yesPct", Math.round(yesPct));
        return map;
    }

    public List<VotingSession> findAllSessions(String hostId)
    {
        return sessionService.findByHostId(hostId);
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
                    .filter(uid -> !"VOTING_FINISHED".equals(votingSession.getLevelStatuses().get(uid)))
                    .toList();
        }
        else if ("yes".equalsIgnoreCase(type) && votingSession.getPlayedLevels() != null)
        {
            uidsToExport = votingSession.getPlayedLevels().stream()
                    .filter(uid ->
                    {
                        String veto = votingSession.getVetoes() != null ? votingSession.getVetoes().get(uid) : null;
                        if ("YES".equals(veto))
                        {
                            return true;
                        }
                        if ("NO".equals(veto))
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
                        String veto = votingSession.getVetoes() != null ? votingSession.getVetoes().get(uid) : null;
                        if ("NO".equals(veto))
                        {
                            return true;
                        }
                        if ("YES".equals(veto))
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
                    lm.put("played", "VOTING_FINISHED".equals(votingSession.getLevelStatuses().get(l.getUid())));
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
                session.setAllowAbstain(request.isAllowAbstain());

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

    public void vetoLevel(String token, String uid, String veto)
    {
        VotingSession session = findActiveSession(token);
        if (session != null)
        {
            String levelUid = (uid != null && !uid.isEmpty()) ? uid : session.getCurrentLevelUid();
            if (levelUid != null)
            {
                vetoLevel(session.getId(), levelUid, veto, session.getHostId());
            }
        }
    }

    public void vetoLevel(String sessionId, String levelUid, String veto, String hostId)
    {
        sessionService.findById(sessionId).ifPresent(session ->
        {
            if (session.getHostId().equals(hostId))
            {
                if (session.getVetoes() == null)
                {
                    session.setVetoes(new HashMap<>());
                }
                session.getVetoes().put(levelUid, veto != null ? veto.toUpperCase() : null);
                sessionService.save(session);
                notifyUpdate(session.getHostId());
            }
        });
    }

    public void updateLevelStatus(String token, String uid, String status)
    {
        VotingSession session = findActiveSession(token);
        if (session != null)
        {
            String levelUid = (uid != null && !uid.isEmpty()) ? uid : session.getCurrentLevelUid();
            if (levelUid != null)
            {
                updateLevelStatus(session.getId(), levelUid, status, session.getHostId());
            }
        }
    }

    public void updateLevelStatus(String sessionId, String levelUid, String status, String hostId)
    {
        sessionService.findById(sessionId).ifPresent(session ->
        {
            if (session.getHostId().equals(hostId))
            {
                if (session.getLevelStatuses() == null)
                {
                    session.setLevelStatuses(new HashMap<>());
                }
                session.getLevelStatuses().put(levelUid, status != null ? status.toUpperCase() : "VOTING_ACTIVE");
                sessionService.save(session);
                notifyUpdate(session.getHostId());
            }
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
                    hostMap.put("activeSession", sessionService.findActiveOrPausedSession(hostId));
                    return hostMap;
                })
                .sorted(Comparator.comparing(m -> (String) m.get("displayName")))
                .collect(Collectors.toList());
    }
}
