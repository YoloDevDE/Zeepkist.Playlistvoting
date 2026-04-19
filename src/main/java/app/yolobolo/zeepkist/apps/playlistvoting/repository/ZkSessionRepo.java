package app.yolobolo.zeepkist.apps.playlistvoting.repository;

import app.yolobolo.zeepkist.apps.playlistvoting.model.SessionState;
import app.yolobolo.zeepkist.apps.playlistvoting.model.ZkSession;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ZkSessionRepo extends MongoRepository<ZkSession, String> {
    List<ZkSession> findByHostId(String hostId);

    List<ZkSession> findByHostIdAndState(String hostId, SessionState state);
}
