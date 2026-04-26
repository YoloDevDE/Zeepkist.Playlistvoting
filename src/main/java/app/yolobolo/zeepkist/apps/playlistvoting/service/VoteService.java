package app.yolobolo.zeepkist.apps.playlistvoting.service;

import app.yolobolo.zeepkist.apps.playlistvoting.model.Level;
import app.yolobolo.zeepkist.apps.playlistvoting.model.UserVote;
import app.yolobolo.zeepkist.apps.playlistvoting.model.VotingSession;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.response.LevelResponse;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.response.PlaylistResponse;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.response.VotesResponse;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.response.VotingResultResponse;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.Platform;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.SessionState;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.VoteOption;
import app.yolobolo.zeepkist.common.model.User;
import app.yolobolo.zeepkist.common.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoteService
{
    private final UserRepository userRepo;
    private final SessionService sessionService;
    private final LevelService levelService;
    private final VoteProcessingService voteProcessingService;

    // --- Token / User ---

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

    public VotingSession createSession(String token, String displayName)
    {
        User user = findUserByToken(token);
        if (user == null)
        {
            log.warn("Cannot create session - no user found for token: {}", token);
            return null;
        }
        return sessionService.createSession(user, displayName);
    }

    public void renameSession(String id, String newName, String hostId)
    {
        sessionService.renameSession(id, newName, hostId);
    }

    public void updateSessionState(String id, SessionState state, String hostId)
    {
        sessionService.updateSessionState(id, state, hostId);
    }

    public void deleteSession(String id, String hostId)
    {
        sessionService.deleteSession(id, hostId);
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
            return "No active session found";
        }
        if (session.getState() == SessionState.PAUSED)
        {
            return "Session is currently paused";
        }

        Level level = levelService.getOrCreateLevel(uid, name, author, workshopID);

        if (!session.getPlayedLevels().contains(uid))
        {
            session.getPlayedLevels().add(uid);
        }
        session.setCurrentLevelUid(uid);
        sessionService.save(session);
        log.info("Current level set to '{}' in session '{}'", name, session.getDisplayName());
        return "Level set to: " + level.getName() + " by " + level.getAuthor();
    }

    // --- Voting ---

    public String vote(String token, String platformUserId, String platformUsername, Platform platform, VoteOption option)
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
            result = voteProcessingService.castVote(session.getId(), session.getCurrentLevelUid(), platformUserId, platformUsername, platform, actualOption);
        }

        sessionService.save(session);
        String summary = getVoteSummary(session);
        return result + " | " + summary;
    }

    // --- Result ---

    public VotingResultResponse getResult(String token)
    {
        VotingSession session = findActiveSession(token);
        if (session == null || session.getCurrentLevelUid() == null)
        {
            return null;
        }

        Level level = levelService.findById(session.getCurrentLevelUid()).orElse(null);
        List<UserVote> votes = voteProcessingService.getVotes(session.getId(), session.getCurrentLevelUid());

        VotesResponse votesResponse = voteProcessingService.calculateVotesResponse(votes);

        return VotingResultResponse.builder()
                .level(LevelResponse.builder()
                        .levelUid(session.getCurrentLevelUid())
                        .levelName(level != null ? level.getName() : "Unknown")
                        .levelAuthor(level != null ? level.getAuthor() : "Unknown")
                        .workshopId(level != null ? level.getWorkshopID() : null)
                        .build())
                .votes(votesResponse)
                .build();
    }

    private String getVoteSummary(VotingSession session)
    {
        List<UserVote> votes = voteProcessingService.getVotes(session.getId(), session.getCurrentLevelUid());
        VotesResponse votesResponse = voteProcessingService.calculateVotesResponse(votes);
        return "%d/%d/%d (y/n/a)".formatted(votesResponse.getYes(), votesResponse.getNo(), votesResponse.getAbstain());
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

        VotesResponse votesResponse = voteProcessingService.calculateVotesResponse(votes);

        session.setCurrentLevelUid(null);
        sessionService.save(session);

        String message = "RESULT<br>%s<br>----------------<br>%d/%d/%d (y/n/a)".formatted(levelInfo, votesResponse.getYes(), votesResponse.getNo(), votesResponse.getAbstain());
        log.info("Reset - {} | {}/{}/{} (y/n/a)", levelInfo, votesResponse.getYes(), votesResponse.getNo(), votesResponse.getAbstain());
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

        List<PlaylistResponse.LevelWithVotes> levels = session.getPlayedLevels().stream()
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

        // Current level (only if token is provided)
        if (token != null)
        {
            Level currentLevel = getCurrentLevel(token);
            if (currentLevel != null)
            {
                Map<String, Object> lvl = new LinkedHashMap<>();
                lvl.put("name", currentLevel.getName());
                lvl.put("author", currentLevel.getAuthor());
                lvl.put("uid", currentLevel.getUid());
                data.put("currentLevel", lvl);
            }
            else
            {
                data.put("currentLevel", null);
            }

            // Current votes for active level
            VotingSession activeSession = findActiveSession(token);
            if (activeSession != null)
            {
                data.put("activeSessionName", activeSession.getDisplayName());
                if (activeSession.getCurrentLevelUid() != null)
                {
                    data.put("currentVotes", getVotesMap(activeSession.getId(), activeSession.getCurrentLevelUid()));
                }
                else
                {
                    data.put("currentVotes", null);
                }
            }
            else
            {
                data.put("activeSessionName", null);
                data.put("currentVotes", null);
            }
        }
        else
        {
            data.put("currentLevel", null);
            data.put("activeSessionName", null);
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
            sm.put("createdAt", s.getCreatedAt() != null ? s.getCreatedAt().toEpochMilli() : null);

            var levelsList = new ArrayList<>(s.getPlayedLevels());
            Collections.reverse(levelsList);

            // If session is ACTIVE, only show the current level
            if (s.getState() == SessionState.ACTIVE && s.getCurrentLevelUid() != null)
            {
                levelsList = new ArrayList<>(List.of(s.getCurrentLevelUid()));
            }

            var levels = levelsList.stream().map(uid ->
            {
                Level levelInfo = levelService.findById(uid).orElse(null);
                var lvlVotes = voteProcessingService.getVotes(s.getId(), uid);

                Map<String, Object> lm = getVotesMap(lvlVotes);
                lm.put("uid", uid);
                lm.put("name", levelInfo != null ? levelInfo.getName() : uid);
                lm.put("author", levelInfo != null ? levelInfo.getAuthor() : "");
                lm.put("workshopID", levelInfo != null ? levelInfo.getWorkshopID() : 0);
                lm.put("isCurrent", uid.equals(s.getCurrentLevelUid()));

                var individualVotes = lvlVotes.stream()
                        .sorted(Comparator.comparing(UserVote::getModifiedAt).reversed())
                        .map(v ->
                        {
                            Map<String, Object> vm = new LinkedHashMap<>();
                            vm.put("user", v.getPlatformUserId());
                            vm.put("username", v.getPlatformUsername());
                            vm.put("platform", v.getPlatform().name());
                            vm.put("vote", v.getVote().name());
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

    private Map<String, Object> getVotesMap(String sessionId, String levelUid)
    {
        return getVotesMap(voteProcessingService.getVotes(sessionId, levelUid));
    }

    private Map<String, Object> getVotesMap(List<UserVote> votes)
    {
        Map<String, Object> map = voteProcessingService.calculateVotesMap(votes);
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

    public byte[] downloadPlaylist(String id, int roundLength, boolean shuffle, String name, String hostId) throws Exception
    {
        VotingSession votingSession = sessionService.findById(id).orElse(null);
        if (votingSession == null || !votingSession.getHostId().equals(hostId))
        {
            return null;
        }

        var levels = votingSession.getPlayedLevels().stream()
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
                    lm.put("played", false);
                    return lm;
                }).toList();

        String playlistName = (name != null && !name.isBlank()) ? name : votingSession.getDisplayName();

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

}
