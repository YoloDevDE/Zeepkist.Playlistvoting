package app.yolobolo.zeepkist.apps.playlistvoting.repository;

import app.yolobolo.zeepkist.apps.playlistvoting.model.UserVote;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.Platform;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.VoteOption;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserVoteRepository extends MongoRepository<UserVote, String>
{
    List<UserVote> findBySessionIdAndLevelUid(String sessionId, String levelUid);

    void deleteBySessionId(String sessionId);

    void deleteBySessionIdAndLevelUid(String sessionId, String levelUid);

    long countBySessionId(String sessionId);

    long countBySessionIdAndVote(String sessionId, VoteOption vote);

    List<UserVote> findBySessionIdInAndVote(List<String> sessionIds, VoteOption vote);

    List<UserVote> findBySessionIdInAndLevelUidIn(List<String> sessionIds, List<String> levelUids);

    void deleteByPlatformAndPlatformUserId(Platform platform, String platformUserId);

    Optional<UserVote> findBySessionIdAndLevelUidAndPlatformAndPlatformUserId(String sessionId, String levelUid, Platform platform, String platformUserId);
}
