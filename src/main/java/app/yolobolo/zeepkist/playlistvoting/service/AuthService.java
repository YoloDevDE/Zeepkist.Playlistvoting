package app.yolobolo.zeepkist.playlistvoting.service;

import app.yolobolo.zeepkist.playlistvoting.model.ZkHost;
import app.yolobolo.zeepkist.playlistvoting.repository.ZkHostRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
public class AuthService {

    private final RestClient restClient = RestClient.create();
    @Autowired
    private ZkHostRepo zkHostRepo;
    @Value("${steam.api.key}")
    private String steamApiKey;

    public ZkHost findOrCreateHost(String steamId) {
        ZkHost host = zkHostRepo.findBySteamId(steamId);
        if (host != null) {
            host.setLastUsed(Instant.now());
            zkHostRepo.save(host);
            log.info("Existing host logged in - steamId: {}", steamId);
            return host;
        }

        String steamName = fetchSteamName(steamId);
        host = new ZkHost();
        host.setSteamId(steamId);
        host.setSteamName(steamName);
        zkHostRepo.save(host);
        log.info("New host created - steamId: {}, name: {}, token: {}", steamId, steamName, host.getToken());
        return host;
    }

    @SuppressWarnings("unchecked")
    private String fetchSteamName(String steamId) {
        try {
            String url = "https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v2/?key=%s&steamids=%s"
                    .formatted(steamApiKey, steamId);
            Map<String, Object> response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(Map.class);

            Map<String, Object> responseObj = (Map<String, Object>) response.get("response");
            java.util.List<Map<String, Object>> players = (java.util.List<Map<String, Object>>) responseObj.get("players");
            if (players != null && !players.isEmpty()) {
                return (String) players.getFirst().get("personaname");
            }
        } catch (Exception e) {
            log.warn("Failed to fetch Steam username for steamId: {}", steamId, e);
        }
        return steamId;
    }
}
