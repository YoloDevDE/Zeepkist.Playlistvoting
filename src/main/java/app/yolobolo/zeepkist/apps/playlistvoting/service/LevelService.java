package app.yolobolo.zeepkist.apps.playlistvoting.service;

import app.yolobolo.zeepkist.apps.playlistvoting.model.Level;
import app.yolobolo.zeepkist.apps.playlistvoting.repository.LevelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LevelService
{

    private final LevelRepository levelRepository;
    private final RestClient restClient = RestClient.create();

    @Value("${steam.api.key}")
    private String steamApiKey;

    public Optional<Level> findById(String uid)
    {
        Optional<Level> levelOpt = levelRepository.findById(uid);
        if (levelOpt.isPresent())
        {
            Level level = levelOpt.get();
            if (level.getThumbnailUrl() == null && level.getWorkshopID() != null && level.getWorkshopID() != 0)
            {
                updateThumbnail(level);
            }
        }
        return levelOpt;
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

            if (workshopID != null && workshopID != 0)
            {
                fetchThumbnailUrl(workshopID).ifPresent(newLevel::setThumbnailUrl);
            }

            levelRepository.save(newLevel);
            log.info("Created new level: {} by {}", name, author);
            return newLevel;
        });
    }

    private void updateThumbnail(Level level)
    {
        fetchThumbnailUrl(level.getWorkshopID()).ifPresent(url ->
        {
            level.setThumbnailUrl(url);
            levelRepository.save(level);
            log.info("Updated thumbnail for level: {}", level.getName());
        });
    }

    @SuppressWarnings("unchecked")
    private Optional<String> fetchThumbnailUrl(Long workshopID)
    {
        try
        {
            String url = "https://api.steampowered.com/ISteamRemoteStorage/GetPublishedFileDetails/v1/";
            String body = "itemcount=1&publishedfileids[0]=" + workshopID;
            if (steamApiKey != null && !steamApiKey.equals("dummy"))
            {
                body += "&key=" + steamApiKey;
            }

            Map<String, Object> response = restClient.post()
                    .uri(url)
                    .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.containsKey("response"))
            {
                Map<String, Object> respBody = (Map<String, Object>) response.get("response");
                List<Map<String, Object>> details = (List<Map<String, Object>>) respBody.get("publishedfiledetails");
                if (details != null && !details.isEmpty())
                {
                    Map<String, Object> item = details.get(0);
                    return Optional.ofNullable((String) item.get("preview_url"));
                }
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to fetch thumbnail for workshopID: {}", workshopID, e);
        }
        return Optional.empty();
    }

    public void save(Level level)
    {
        levelRepository.save(level);
    }
}
