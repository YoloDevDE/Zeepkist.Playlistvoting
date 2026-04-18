package app.yolobolo.zeepkist.playlistvoting.repository;

import app.yolobolo.zeepkist.playlistvoting.model.ZkLevel;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ZkLevelRepo extends MongoRepository<ZkLevel, String> {
}
