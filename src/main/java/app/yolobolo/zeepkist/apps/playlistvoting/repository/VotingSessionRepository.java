package app.yolobolo.zeepkist.apps.playlistvoting.repository;

import app.yolobolo.zeepkist.apps.playlistvoting.model.VotingSession;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.SessionState;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VotingSessionRepository extends MongoRepository<VotingSession, String>
{
    List<VotingSession> findByHostId(String hostId);

    List<VotingSession> findByHostIdAndState(String hostId, SessionState state);

    List<VotingSession> findByState(SessionState state);
}
