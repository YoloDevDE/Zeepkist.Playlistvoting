package app.yolobolo.zeepkist.apps.playlistvoting.repository;

import app.yolobolo.zeepkist.apps.playlistvoting.model.UserVote;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.Platform;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserVoteRepository extends MongoRepository<UserVote, String>
{
    List<UserVote> findBySessionIdAndLevelUid(String sessionId, String levelUid);

    Optional<UserVote> findBySessionIdAndLevelUidAndPlatformAndPlatformUserId(String sessionId, String levelUid, Platform platform, String platformUserId);
}
