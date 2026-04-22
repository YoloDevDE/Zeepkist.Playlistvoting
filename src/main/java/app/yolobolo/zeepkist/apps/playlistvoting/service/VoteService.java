package app.yolobolo.zeepkist.apps.playlistvoting.service;

import app.yolobolo.zeepkist.apps.playlistvoting.model.*;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.ZkPlaylistResponse;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.Platform;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.ResultOption;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.SessionState;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.VoteOption;
import app.yolobolo.zeepkist.apps.playlistvoting.repository.ZkLevelRepo;
import app.yolobolo.zeepkist.apps.playlistvoting.repository.ZkSessionRepo;
import app.yolobolo.zeepkist.apps.playlistvoting.repository.ZkVoteRepo;
import app.yolobolo.zeepkist.common.model.User;
import app.yolobolo.zeepkist.common.repository.UserRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class VoteService {


    @Autowired
    private UserRepo userRepo;
    @Autowired
    private ZkSessionRepo zkSessionRepo;
    @Autowired
    private ZkLevelRepo zkLevelRepo;
    @Autowired
    private ZkVoteRepo zkVoteRepo;

    // --- Token / User ---

    public User findUserByToken(String token) {
        return userRepo.findByToken(token).orElse(null);
    }

    public User findUserById(String id) {
        return userRepo.findById(id).orElse(null);
    }

    public User findUserBySteamId(String steamId) {
        return userRepo.findByIdentity("STEAM", steamId).orElse(null);
    }

    public void saveUser(User user) {
        userRepo.save(user);
        log.info("User saved: {}", user.getId());
    }

    // --- Session ---

    public ZkSession findActiveSession(String token) {
        User user = findUserByToken(token);
        if (user == null) {
            log.warn("No user found for token: {}", token);
            return null;
        }
        List<ZkSession> sessions = zkSessionRepo.findByHostId(user.getId());
        return sessions.stream()
                .filter(s -> s.getState() == SessionState.ACTIVE)
                .findFirst()
                .orElseGet(() -> sessions.stream()
                        .filter(s -> s.getState() == SessionState.PAUSED)
                        .findFirst()
                        .orElse(null));
    }

    public ZkSession createSession(String token, String displayName) {
        User user = findUserByToken(token);
        if (user == null) {
            log.warn("Cannot create session - no user found for token: {}", token);
            return null;
        }

        // Deactivate all other sessions for this user
        List<ZkSession> otherSessions = zkSessionRepo.findByHostId(user.getId());
        for (ZkSession s : otherSessions) {
            if (s.getState() != SessionState.FINISHED) {
                s.setState(SessionState.PAUSED);
                s.setCurrentLevelUid(null);
                zkSessionRepo.save(s);
            }
        }

        ZkSession session = new ZkSession();
        session.setHostId(user.getId());
        session.setDisplayName(displayName);
        session.setState(SessionState.ACTIVE);
        zkSessionRepo.save(session);
        log.info("Created new session '{}' and deactivated others for user: {}", displayName, user.getId());
        return session;
    }

    public void renameSession(String id, String newName, String hostId) {
        ZkSession session = zkSessionRepo.findById(id).orElse(null);
        if (session != null && session.getHostId().equals(hostId)) {
            session.setDisplayName(newName);
            zkSessionRepo.save(session);
            log.info("Session {} renamed to '{}'", id, newName);
        }
    }

    public void updateSessionState(String id, SessionState state, String hostId) {
        ZkSession session = zkSessionRepo.findById(id).orElse(null);
        if (session != null && session.getHostId().equals(hostId)) {
            if (state == SessionState.ACTIVE) {
                // Pause all other active/paused sessions
                List<ZkSession> otherSessions = zkSessionRepo.findByHostId(hostId);
                for (ZkSession s : otherSessions) {
                    if (!s.getId().equals(id) && s.getState() != SessionState.FINISHED) {
                        s.setState(SessionState.PAUSED);
                        s.setCurrentLevelUid(null); // Clear current level from other sessions
                        zkSessionRepo.save(s);
                    }
                }
            }
            session.setState(state);
            zkSessionRepo.save(session);
            log.info("Session {} state changed to {}", id, state);
        }
    }

    public void deleteSession(String id, String hostId) {
        ZkSession session = zkSessionRepo.findById(id).orElse(null);
        if (session != null && session.getHostId().equals(hostId)) {
            zkSessionRepo.delete(session);
            log.info("Session {} deleted by host {}", id, hostId);
        }
    }

    public ZkSession renameActiveSession(String token, String newName) {
        ZkSession session = findActiveSession(token);
        if (session == null) return null;
        session.setDisplayName(newName);
        zkSessionRepo.save(session);
        log.info("Active session renamed to '{}'", newName);
        return session;
    }

    public ZkSession pauseSession(String token) {
        ZkSession session = findActiveSession(token);
        if (session == null) return null;
        session.setState(SessionState.PAUSED);
        zkSessionRepo.save(session);
        log.info("Session '{}' paused", session.getDisplayName());
        return session;
    }

    public ZkSession resumeSession(String token) {
        ZkSession session = findActiveSession(token);
        if (session == null || session.getState() != SessionState.PAUSED) return null;
        session.setState(SessionState.ACTIVE);
        zkSessionRepo.save(session);
        log.info("Session '{}' resumed", session.getDisplayName());
        return session;
    }

    // --- Level ---

    public ZkLevel getCurrentLevel(String token) {
        ZkSession session = findActiveSession(token);
        if (session == null || session.getCurrentLevelUid() == null) return null;
        return zkLevelRepo.findById(session.getCurrentLevelUid()).orElse(null);
    }

    public String setCurrentLevel(String token, String uid, String name, String author, Long workshopID) {
        ZkSession session = findActiveSession(token);
        if (session == null) return "No active session found";
        if (session.getState() == SessionState.PAUSED) return "Session is currently paused";

        ZkLevel level = zkLevelRepo.findById(uid).orElseGet(() -> {
            ZkLevel newLevel = ZkLevel.builder()
                    .uid(uid)
                    .name(name)
                    .author(author)
                    .workshopID(workshopID)
                    .build();
            zkLevelRepo.save(newLevel);
            log.info("Created new level: {} by {}", name, author);
            return newLevel;
        });

        if (!session.getPlayedLevels().contains(uid)) {
            session.getPlayedLevels().add(uid);
        }
        session.setCurrentLevelUid(uid);
        session.setLastUsed(Instant.now());
        zkSessionRepo.save(session);
        log.info("Current level set to '{}' in session '{}'", name, session.getDisplayName());
        return "Level set to: " + level.getName() + " by " + level.getAuthor();
    }

    // --- Voting ---

    public String vote(String token, String platformUserId, String platformUsername, Platform platform, VoteOption option) {
        return switch (option) {
            case YES -> castVote(token, platformUserId,platformUsername, platform, true);
            case NO -> castVote(token, platformUserId,platformUsername, platform, false);
            case IDK -> castVote(token, platformUserId,platformUsername, platform, new Random().nextBoolean());
            case REMOVE -> removeVote(token, platformUserId, platform);
            case ABSTAIN -> abstainVote(token, platformUserId, platform);
        };
    }

    private String castVote(String token, String platformUserId,String platformUsername, Platform platform, boolean vote) {
        ZkSession session = findActiveSession(token);
        if (session == null) return "No active session found";
        if (session.getState() == SessionState.PAUSED) return "Session is currently paused";
        if (session.getCurrentLevelUid() == null) return "No level currently set";

        Optional<ZkVote> existing = zkVoteRepo.findBySessionIdAndLevelUidAndPlatformAndPlatformUserId(
                session.getId(), session.getCurrentLevelUid(), platform, platformUserId);

        ZkVote zkVote = existing.orElseGet(ZkVote::new);
        String voteLabel = vote ? "YES" : "NO";

        String action;
        if (existing.isEmpty()) {
            action = "voted";
        } else if (existing.get().getVote() == (vote ? VoteOption.YES : VoteOption.NO)) {
            action = "already voted";
        } else {
            action = "changed their mind and voted";
        }

        zkVote.setSessionId(session.getId());
        zkVote.setLevelUid(session.getCurrentLevelUid());
        zkVote.setPlatform(platform);
        zkVote.setPlatformUserId(platformUserId);
        zkVote.setPlatformUsername(platformUsername);
        zkVote.setVote(vote ? VoteOption.YES : VoteOption.NO);
        zkVote.setModifiedAt(Instant.now());
        zkVoteRepo.save(zkVote);

        session.setLastUsed(Instant.now());
        zkSessionRepo.save(session);

        String result = getVoteSummary(session);
        log.info("{} ({}) {} '{}' | {}", platformUserId, platform, action, voteLabel, result);
        return "@%s - %s '%s' | %s".formatted(platformUserId, action, voteLabel, result);
    }

    private String removeVote(String token, String platformUserId, Platform platform) {
        ZkSession session = findActiveSession(token);
        if (session == null) return "No active session found";
        if (session.getState() == SessionState.PAUSED) return "Session is currently paused";
        if (session.getCurrentLevelUid() == null) return "No level currently set";

        Optional<ZkVote> existing = zkVoteRepo.findBySessionIdAndLevelUidAndPlatformAndPlatformUserId(
                session.getId(), session.getCurrentLevelUid(), platform, platformUserId);

        if (existing.isEmpty()) {
            return "@%s - You haven't voted yet.".formatted(platformUserId);
        }
        zkVoteRepo.delete(existing.get());
        session.setLastUsed(Instant.now());
        zkSessionRepo.save(session);

        String result = getVoteSummary(session);
        log.info("{} ({}) revoked their vote | {}", platformUserId, platform, result);
        return "@%s - revoked their vote | %s".formatted(platformUserId, result);
    }

    private String abstainVote(String token, String platformUserId, Platform platform) {
        ZkSession session = findActiveSession(token);
        if (session == null) return "No active session found";
        if (session.getState() == SessionState.PAUSED) return "Session is currently paused";
        if (session.getCurrentLevelUid() == null) return "No level currently set";

        Optional<ZkVote> existing = zkVoteRepo.findBySessionIdAndLevelUidAndPlatformAndPlatformUserId(
                session.getId(), session.getCurrentLevelUid(), platform, platformUserId);

        ZkVote zkVote = existing.orElseGet(ZkVote::new);
        zkVote.setSessionId(session.getId());
        zkVote.setLevelUid(session.getCurrentLevelUid());
        zkVote.setPlatform(platform);
        zkVote.setPlatformUserId(platformUserId);
        zkVote.setVote(VoteOption.ABSTAIN);
        zkVote.setModifiedAt(Instant.now());
        zkVoteRepo.save(zkVote);

        session.setLastUsed(Instant.now());
        zkSessionRepo.save(session);

        String result = getVoteSummary(session);
        log.info("{} ({}) abstained | {}", platformUserId, platform, result);
        return "@%s - abstained from voting | %s".formatted(platformUserId, result);
    }

    // --- Result ---

    public String getResult(String token, ResultOption option) {
        ZkSession session = findActiveSession(token);
        if (session == null) return "No active session found";
        if (session.getCurrentLevelUid() == null) return "No level currently set";

        List<ZkVote> votes = zkVoteRepo.findBySessionIdAndLevelUid(session.getId(), session.getCurrentLevelUid());
        log.debug("Result requested - option: {}", option);
        return switch (option) {
            case YES -> String.valueOf(votes.stream().filter(v -> v.getVote() == VoteOption.YES).count());
            case NO -> String.valueOf(votes.stream().filter(v -> v.getVote() == VoteOption.NO).count());
            case ABSTAIN -> String.valueOf(votes.stream().filter(v -> v.getVote() == VoteOption.ABSTAIN).count());
            case TOTAL -> getVoteSummary(session);
        };
    }

    private String getVoteSummary(ZkSession session) {
        List<ZkVote> votes = zkVoteRepo.findBySessionIdAndLevelUid(session.getId(), session.getCurrentLevelUid());
        long yes = votes.stream().filter(v -> v.getVote() == VoteOption.YES).count();
        long no = votes.stream().filter(v -> v.getVote() == VoteOption.NO).count();
        long abstain = votes.stream().filter(v -> v.getVote() == VoteOption.ABSTAIN).count();
        return "%d/%d/%d (y/n/a)".formatted(yes, no, abstain);
    }

    // --- Reset ---

    public String reset(String token) {
        ZkSession session = findActiveSession(token);
        if (session == null) return "No active session found";
        if (session.getState() == SessionState.PAUSED) return "Session is currently paused";

        ZkLevel level = getCurrentLevel(token);
        String levelInfo = level != null ? level.getName() + " by " + level.getAuthor() : "Previous Level";

        List<ZkVote> votes = level != null
                ? zkVoteRepo.findBySessionIdAndLevelUid(session.getId(), level.getUid())
                : List.of();

        long yes = votes.stream().filter(v -> v.getVote() == VoteOption.YES).count();
        long no = votes.stream().filter(v -> v.getVote() == VoteOption.NO).count();
        long abstain = votes.stream().filter(v -> v.getVote() == VoteOption.ABSTAIN).count();

        session.setCurrentLevelUid(null);
        session.setLastUsed(Instant.now());
        zkSessionRepo.save(session);

        String message = "RESULT<br>%s<br>----------------<br>%d/%d/%d (y/n/a)".formatted(levelInfo, yes, no, abstain);
        log.info("Reset - {} | {}/{}/{} (y/n/a)", levelInfo, yes, no, abstain);
        return message;
    }

    public ZkPlaylistResponse getPlaylist(String token) {
        ZkSession session = findActiveSession(token);
        if (session == null) return null;

        ZkPlaylistResponse response = new ZkPlaylistResponse();
        response.setSessionName(session.getDisplayName());

        List<ZkPlaylistResponse.LevelWithVotes> levels = session.getPlayedLevels().stream()
                .map(uid -> {
                    ZkLevel level = zkLevelRepo.findById(uid).orElse(null);
                    if (level == null) return null;

                    ZkPlaylistResponse.LevelWithVotes lwv = new ZkPlaylistResponse.LevelWithVotes();
                    lwv.setUid(uid);
                    lwv.setName(level.getName());
                    lwv.setAuthor(level.getAuthor());
                    lwv.setVotes(zkVoteRepo.findBySessionIdAndLevelUid(session.getId(), uid));
                    return lwv;
                })
                .filter(Objects::nonNull)
                .toList();

        response.setLevels(levels);
        log.debug("Playlist requested for session '{}', {} levels", session.getDisplayName(), levels.size());
        return response;
    }

    public Map<String, Object> getDashboardData(String token, String hostId) {
        Map<String, Object> data = new LinkedHashMap<>();

        // Current level
        ZkLevel currentLevel = getCurrentLevel(token);
        if (currentLevel != null) {
            Map<String, Object> lvl = new LinkedHashMap<>();
            lvl.put("name", currentLevel.getName());
            lvl.put("author", currentLevel.getAuthor());
            lvl.put("uid", currentLevel.getUid());
            data.put("currentLevel", lvl);
        } else {
            data.put("currentLevel", null);
        }

        // Current votes for active level
        ZkSession activeSession = findActiveSession(token);
        if (activeSession != null) {
            data.put("activeSessionName", activeSession.getDisplayName());
            if (activeSession.getCurrentLevelUid() != null) {
                data.put("currentVotes", getVotesMap(activeSession.getId(), activeSession.getCurrentLevelUid()));
            } else {
                data.put("currentVotes", null);
            }
        } else {
            data.put("activeSessionName", null);
            data.put("currentVotes", null);
        }

        // All sessions with level details + per-level votes
        var sessions = zkSessionRepo.findByHostId(hostId).stream().map(s -> {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("id", s.getId());
            sm.put("name", s.getDisplayName());
            sm.put("state", s.getState().name());
            sm.put("createdAt", s.getCreatedAt() != null ? s.getCreatedAt().toEpochMilli() : null);

            var levelsList = new ArrayList<>(s.getPlayedLevels());
            Collections.reverse(levelsList);

            // If session is ACTIVE, only show the current level
            if (s.getState() == SessionState.ACTIVE && s.getCurrentLevelUid() != null) {
                levelsList = new ArrayList<>(List.of(s.getCurrentLevelUid()));
            }

            var levels = levelsList.stream().map(uid -> {
                ZkLevel levelInfo = zkLevelRepo.findById(uid).orElse(null);
                var lvlVotes = zkVoteRepo.findBySessionIdAndLevelUid(s.getId(), uid);

                Map<String, Object> lm = getVotesMap(lvlVotes);
                lm.put("uid", uid);
                lm.put("name", levelInfo != null ? levelInfo.getName() : uid);
                lm.put("author", levelInfo != null ? levelInfo.getAuthor() : "");
                lm.put("workshopID", levelInfo != null ? levelInfo.getWorkshopID() : 0);
                lm.put("isCurrent", uid.equals(s.getCurrentLevelUid()));

                var individualVotes = lvlVotes.stream()
                        .sorted(Comparator.comparing(ZkVote::getModifiedAt).reversed())
                        .map(v -> {
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

    private Map<String, Object> getVotesMap(String sessionId, String levelUid) {
        return getVotesMap(zkVoteRepo.findBySessionIdAndLevelUid(sessionId, levelUid));
    }

    private Map<String, Object> getVotesMap(List<ZkVote> votes) {
        long yes = votes.stream().filter(v -> v.getVote() == VoteOption.YES).count();
        long no = votes.stream().filter(v -> v.getVote() == VoteOption.NO).count();
        long abstain = votes.stream().filter(v -> v.getVote() == VoteOption.ABSTAIN).count();
        long total = yes + no + abstain;
        double yesPct = (yes + no) > 0 ? (yes * 100.0 / (yes + no)) : 0;

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("yes", yes);
        map.put("no", no);
        map.put("abstain", abstain);
        map.put("total", total);
        map.put("yesPct", Math.round(yesPct));
        return map;
    }

    public List<ZkSession> findAllSessions(String hostId) {
        return zkSessionRepo.findByHostId(hostId);
    }

    public byte[] downloadPlaylist(String id, int roundLength, boolean shuffle, String name, String hostId) throws Exception {
        ZkSession zkSession = zkSessionRepo.findById(id).orElse(null);
        if (zkSession == null || !zkSession.getHostId().equals(hostId)) return null;

        var levels = zkSession.getPlayedLevels().stream()
                .map(uid -> zkLevelRepo.findById(uid).orElse(null))
                .filter(Objects::nonNull)
                .map(l -> {
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

        String playlistName = (name != null && !name.isBlank()) ? name : zkSession.getDisplayName();

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

    public String getPlaylistFilename(String sessionId, String name) {
        ZkSession zkSession = zkSessionRepo.findById(sessionId).orElse(null);
        String playlistName;
        if (name != null && !name.isBlank()) {
            playlistName = name;
        } else if (zkSession != null) {
            playlistName = zkSession.getDisplayName();
        } else {
            playlistName = "playlist";
        }
        return playlistName.replaceAll("[^a-zA-Z0-9_-]", "_") + ".zeeplist";
    }

}
