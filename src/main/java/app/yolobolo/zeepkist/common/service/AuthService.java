package app.yolobolo.zeepkist.common.service;

import app.yolobolo.zeepkist.common.model.User;
import app.yolobolo.zeepkist.common.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class AuthService
{

    private static final String STEAM_OPENID_ENDPOINT = "https://steamcommunity.com/openid/login";
    private static final String STEAM_IDENTIFIER_SELECT = "http://specs.openid.net/auth/2.0/identifier_select";
    private static final String OPENID_NS = "http://specs.openid.net/auth/2.0";
    private static final String OPENID_MODE_CHECKID_SETUP = "checkid_setup";
    private static final String OPENID_MODE_ID_RES = "id_res";
    private static final String OPENID_MODE_CHECK_AUTH = "check_authentication";

    private final RestClient restClient = RestClient.create();

    @Autowired
    private UserRepository userRepo;
    @Autowired
    private UserService userService;
    @Value("${steam.api.key}")
    private String steamApiKey;
    @Value("${zeepkist.app.id}")
    private String zeepkistAppId;

    public String verifySteamTicket(String ticketHex)
    {
        String url = "https://api.steampowered.com/ISteamUserAuth/AuthenticateUserTicket/v1/?key=%s&appid=%s&ticket=%s"
                .formatted(enc(steamApiKey), enc(zeepkistAppId), enc(ticketHex));

        try
        {
            Map<String, Object> response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.containsKey("response"))
            {
                Map<String, Object> respBody = (Map<String, Object>) response.get("response");
                if (respBody.containsKey("params"))
                {
                    Map<String, Object> params = (Map<String, Object>) respBody.get("params");
                    if ("OK".equals(params.get("result")))
                    {
                        return (String) params.get("steamid");
                    }
                }
                if (respBody.containsKey("error"))
                {
                    log.error("Steam ticket verification failed: {}", respBody.get("error"));
                }
            }
        }
        catch (Exception e)
        {
            log.error("Error during Steam ticket verification", e);
        }
        return null;
    }

    public String getSteamLoginUrl(String returnTo, String realm)
    {
        return STEAM_OPENID_ENDPOINT + "?"
                + "openid.ns=" + enc(OPENID_NS)
                + "&openid.mode=" + enc(OPENID_MODE_CHECKID_SETUP)
                + "&openid.return_to=" + enc(returnTo)
                + "&openid.realm=" + enc(realm)
                + "&openid.identity=" + enc(STEAM_IDENTIFIER_SELECT)
                + "&openid.claimed_id=" + enc(STEAM_IDENTIFIER_SELECT);
    }

    public String verifySteamLogin(Map<String, String> params)
    {
        if (!OPENID_MODE_ID_RES.equals(params.get("openid.mode")))
        {
            log.warn("Steam login mode is not id_res: {}", params.get("openid.mode"));
            return null;
        }

        MultiValueMap<String, String> verification = new LinkedMultiValueMap<>();
        params.forEach((k, v) ->
        {
            if (k.startsWith("openid."))
            {
                verification.add(k, v);
            }
        });
        verification.set("openid.mode", OPENID_MODE_CHECK_AUTH);

        String response = restClient.post()
                .uri(STEAM_OPENID_ENDPOINT)
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .body(verification)
                .retrieve()
                .body(String.class);

        if (response == null || !response.contains("is_valid:true"))
        {
            log.warn("Steam OpenID validation failed: {}", response);
            return null;
        }

        return params.get("openid.claimed_id");
    }

    private String enc(String value)
    {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    public User handleSteamLogin(String steamId)
    {
        String steamName = fetchSteamName(steamId);
        // Steam OpenID 2.0 provides no email by default. 
        // We'll keep it null for now or fetch it if we had a way.
        return userService.createOrUpdateUserFromIdentity("STEAM", steamId, steamName);
    }

    public User findOrCreateUser(String steamId)
    {
        Optional<User> userOpt = userRepo.findByIdentity("STEAM", steamId);
        if (userOpt.isPresent())
        {
            User user = userOpt.get();
            user.setLastLoginAt(Instant.now());
            userRepo.save(user);
            return user;
        }

        String steamName = fetchSteamName(steamId);
        return userService.createOrUpdateUserFromIdentity("STEAM", steamId, steamName);
    }

    @SuppressWarnings("unchecked")
    private String fetchSteamName(String steamId)
    {
        try
        {
            String url = "https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v2/?key=%s&steamids=%s"
                    .formatted(steamApiKey, steamId);
            Map<String, Object> response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(Map.class);

            Map<String, Object> responseObj = (Map<String, Object>) response.get("response");
            java.util.List<Map<String, Object>> players = (java.util.List<Map<String, Object>>) responseObj.get("players");
            if (players != null && !players.isEmpty())
            {
                return (String) players.getFirst().get("personaname");
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to fetch Steam username for steamId: {}", steamId, e);
        }
        return steamId;
    }
}
