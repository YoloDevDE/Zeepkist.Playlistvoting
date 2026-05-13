package app.yolobolo.zeepkist.apps.playlistvoting.model.enums;

public enum LobbyGameState
{
    GAME(0),
    ROUND_ENDING(1),
    PODIUM(2);

    private final int value;

    LobbyGameState(int value)
    {
        this.value = value;
    }

    public static LobbyGameState fromInt(int value)
    {
        for (LobbyGameState state : LobbyGameState.values())
        {
            if (state.value == value)
            {
                return state;
            }
        }
        return GAME; // Default
    }

    @com.fasterxml.jackson.annotation.JsonValue
    public int getValue()
    {
        return value;
    }
}
