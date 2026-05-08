package app.yolobolo.zeepkist.apps.playlistvoting.service;

import app.yolobolo.zeepkist.apps.playlistvoting.model.Level;
import app.yolobolo.zeepkist.apps.playlistvoting.repository.LevelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LevelService
{

    private final LevelRepository levelRepository;

    public Optional<Level> findById(String uid)
    {
        return levelRepository.findById(uid);
    }

    public Level getOrCreateLevel(String uid, String name, String author, Long workshopID)
    {
        return levelRepository.findById(uid).orElseGet(() ->
        {
            Level newLevel = Level.builder()
                    .uid(uid)
                    .name(name)
                    .author(author)
                    .workshopID(workshopID)
                    .build();
            levelRepository.save(newLevel);
            log.info("Created new level: {} by {}", name, author);
            return newLevel;
        });
    }

    public void save(Level level)
    {
        levelRepository.save(level);
    }
}
