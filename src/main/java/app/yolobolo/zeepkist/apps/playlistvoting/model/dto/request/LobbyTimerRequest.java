package app.yolobolo.zeepkist.apps.playlistvoting.model.dto.request;

import lombok.Data;

@Data
public class LobbyTimerRequest
{
    private double roundTime;
    private double levelLoadedAtTime;
    private double currentTime;
    private int gameState;
}
