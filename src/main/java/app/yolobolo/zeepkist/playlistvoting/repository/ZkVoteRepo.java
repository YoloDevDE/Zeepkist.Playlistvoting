package app.yolobolo.zeepkist.playlistvoting.repository;

import app.yolobolo.zeepkist.playlistvoting.model.Platform;
import app.yolobolo.zeepkist.playlistvoting.model.ZkVote;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ZkVoteRepo extends MongoRepository<ZkVote, String> {
    List<ZkVote> findBySessionIdAndLevelUid(String sessionId, String levelUid);

    Optional<ZkVote> findBySessionIdAndLevelUidAndPlatformAndPlatformUserId(String sessionId, String levelUid, Platform platform, String platformUserId);
}
