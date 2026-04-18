package app.yolobolo.zeepkist.playlistvoting.repository;

import app.yolobolo.zeepkist.playlistvoting.model.ZkHost;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ZkHostRepo extends MongoRepository<ZkHost, String> {
    ZkHost findByToken(String token);

    ZkHost findBySteamId(String steamId);
}
