package app.yolobolo.zeepkist.common.repository;

import app.yolobolo.zeepkist.common.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepo extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);

    Optional<User> findByToken(String token);

    @Query("{'identities.provider': ?0, 'identities.providerId': ?1}")
    Optional<User> findByIdentity(String provider, String providerId);
}
