package app.yolobolo.zeepkist.apps.playlistvoting.service;

import app.yolobolo.zeepkist.apps.playlistvoting.model.VotingSession;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.SessionState;
import app.yolobolo.zeepkist.apps.playlistvoting.repository.VotingSessionRepository;
import app.yolobolo.zeepkist.common.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService
{

    private final VotingSessionRepository sessionRepository;

    public Optional<VotingSession> findById(String id)
    {
        return sessionRepository.findById(id);
    }

    public List<VotingSession> findByHostId(String hostId)
    {
        return sessionRepository.findByHostId(hostId);
    }

    public List<VotingSession> findAllActive()
    {
        return sessionRepository.findByState(SessionState.ACTIVE);
    }

    public VotingSession findActiveOrPausedSession(String hostId)
    {
        List<VotingSession> sessions = sessionRepository.findByHostId(hostId);
        return sessions.stream()
                .filter(s -> s.getState() == SessionState.ACTIVE)
                .findFirst()
                .orElseGet(() -> sessions.stream()
                        .filter(s -> s.getState() == SessionState.PAUSED)
                        .findFirst()
                        .orElse(null));
    }

    public VotingSession createSession(User user, String displayName)
    {
        // Deactivate all other sessions for this user
        List<VotingSession> otherSessions = sessionRepository.findByHostId(user.getId());
        for (VotingSession s : otherSessions)
        {
            if (s.getState() != SessionState.FINISHED)
            {
                s.setState(SessionState.PAUSED);
                s.setCurrentLevelUid(null);
                sessionRepository.save(s);
            }
        }

        VotingSession session = new VotingSession();
        session.setHostId(user.getId());
        session.setDisplayName(displayName);
        session.setState(SessionState.ACTIVE);
        sessionRepository.save(session);
        log.info("Created new session '{}' and deactivated others for user: {}", displayName, user.getId());
        return session;
    }

    public void renameSession(String id, String newName, String hostId)
    {
        sessionRepository.findById(id).ifPresent(session ->
        {
            if (session.getHostId().equals(hostId))
            {
                session.setDisplayName(newName);
                sessionRepository.save(session);
                log.info("Session {} renamed to '{}'", id, newName);
            }
        });
    }

    public void updateSessionState(String id, SessionState state, String hostId)
    {
        sessionRepository.findById(id).ifPresent(session ->
        {
            if (session.getHostId().equals(hostId))
            {
                if (state == SessionState.ACTIVE)
                {
                    // Pause all other active/paused sessions
                    List<VotingSession> otherSessions = sessionRepository.findByHostId(hostId);
                    for (VotingSession s : otherSessions)
                    {
                        if (!s.getId().equals(id) && s.getState() != SessionState.FINISHED)
                        {
                            s.setState(SessionState.PAUSED);
                            s.setCurrentLevelUid(null);
                            sessionRepository.save(s);
                        }
                    }
                }
                if (state == SessionState.FINISHED && session.getCurrentLevelUid() != null)
                {
                    session.getLevelStatuses().put(session.getCurrentLevelUid(), "VOTING_FINISHED");
                }
                session.setState(state);
                sessionRepository.save(session);
                log.info("Session {} state changed to {}", id, state);
            }
        });
    }

    public void updateSessionSettings(String id, app.yolobolo.zeepkist.apps.playlistvoting.model.dto.request.SessionSettingsRequest settings, String hostId)
    {
        sessionRepository.findById(id).ifPresent(session ->
        {
            if (session.getHostId().equals(hostId))
            {
                if (settings.getDisplayName() != null)
                {
                    session.setDisplayName(settings.getDisplayName());
                }
                if (settings.getPlaylist() != null)
                {
                    session.setPlaylist(settings.getPlaylist());
                }
                session.setPlaylistMode(settings.isPlaylistMode());
                session.setAllowAbstain(settings.isAllowAbstain());

                if (settings.getState() != null && settings.getState() != session.getState())
                {
                    updateSessionState(id, settings.getState(), hostId);
                }
                else
                {
                    sessionRepository.save(session);
                }
                log.info("Session {} settings updated", id);
            }
        });
    }

    public void deleteSession(String id, String hostId)
    {
        sessionRepository.findById(id).ifPresent(session ->
        {
            if (session.getHostId().equals(hostId))
            {
                sessionRepository.delete(session);
                log.info("Session {} deleted", id);
            }
        });
    }

    public List<VotingSession> findAll()
    {
        return sessionRepository.findAll();
    }

    public void save(VotingSession session)
    {
        session.setLastUsed(Instant.now());
        sessionRepository.save(session);
    }
}
