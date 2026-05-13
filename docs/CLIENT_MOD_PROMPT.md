# KI-Prompts für die Zeepkist-Mod-Entwicklung

Diese Datei enthält optimierte Prompts, die du direkt in eine KI (wie ChatGPT, Claude oder GitHub Copilot) kopieren kannst, um die Zeepkist-Mod für dieses Backend anzupassen.

---

## Prompt: Live-Timer & GameState Synchronisierung (C# / BepInEx)

**Ziel:** Erstelle oder aktualisiere eine C#-Mod für Zeepkist, die den aktuellen Lobby-Status (Timer und GameState) an das Backend synchronisiert.

### 1. Endpunkt & Authentifizierung
*   **URL:** `POST http://<DEIN_SERVER>/api/sessions/active/timer`
*   **Header:** 
    *   `Content-Type: application/json`
    *   `Authorization: Bearer <DEIN_TOKEN>` (Token muss konfigurierbar sein)

### 2. Datenstruktur (JSON Payload)
Sende bei jedem Update folgendes Objekt (Mapping der Zeepkist-API):
```json
{
  "roundTime": double,          // ZeepkistNetwork.CurrentLobby.RoundTime
  "levelLoadedAtTime": double,  // ZeepkistNetwork.CurrentLobby.LevelLoadedAtTime
  "currentTime": double,        // ZeepkistNetwork.Time
  "gameState": int              // ZeepkistNetwork.CurrentLobby.GameState
}
```

**GameState Mapping:**
- `0` = **GAME** (Runde läuft)
- `1` = **ROUND_ENDING** (Kurze Pause nach Zieleinlauf)
- `2` = **PODIUM** (Ergebnisanzeige)

### 3. Sende-Logik (Trigger)
Implementiere folgende Trigger, um den Server effizient zu aktualisieren:

1.  **Sofort bei Status-Änderung (Event-basiert):**
    *   Abonniere `ZeepkistNetwork.LobbyGameStateChanged`. Sende bei jeder Änderung ein Update.
    *   Abonniere `ZeepkistNetwork.LevelDataReceived`. Sende ein Update, sobald ein neues Level geladen wurde.

2.  **Periodisches Update (Heartbeat):**
    *   Sende alle **10 Sekunden** ein Update als Fallback (in der `Update()`-Methode).

3.  **Sicherheits-Prüfungen:**
    *   Sende nur, wenn `ZeepkistNetwork.IsConnectedToGame` wahr ist und `ZeepkistNetwork.CurrentLobby` nicht null.
    *   **Wichtig:** Sende nur, wenn du der Host bist (`ZeepkistNetwork.IsMasterClient`), um redundante Daten zu vermeiden.

### 4. Code-Beispiel (C#)
```csharp
private int lastGameState = -1;
private float nextHeartbeat = 0f;

void Update() {
    if (!ZeepkistNetwork.IsConnectedToGame || ZeepkistNetwork.CurrentLobby == null) return;
    if (!ZeepkistNetwork.IsMasterClient) return; 

    bool stateChanged = ZeepkistNetwork.CurrentLobby.GameState != lastGameState;
    bool heartbeat = Time.time >= nextHeartbeat;

    if (stateChanged || heartbeat) {
        SendTimerUpdateToServer(); 
        lastGameState = ZeepkistNetwork.CurrentLobby.GameState;
        nextHeartbeat = Time.time + 10f; 
    }
}
```

---
*Zuletzt aktualisiert: 2026-05-12*
