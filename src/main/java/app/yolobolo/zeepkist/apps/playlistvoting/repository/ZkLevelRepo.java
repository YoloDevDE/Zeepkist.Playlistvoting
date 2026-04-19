package app.yolobolo.zeepkist.apps.playlistvoting.repository;

import app.yolobolo.zeepkist.apps.playlistvoting.model.ZkLevel;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ZkLevelRepo extends MongoRepository<ZkLevel, String> {
}
