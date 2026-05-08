# Zeepkist Playlist Voting API Documentation

This documentation provides details for the REST API endpoints of the Zeepkist Playlist Voting application. These endpoints can be used to
manage voting sessions, cast votes, and retrieve session data.

## Base URL

All relative paths are based on the server root (e.g., `http://localhost:8080`).

## Authentication

Most endpoints require authentication. You can authenticate in two ways:

1. **Header:** `Authorization: Bearer <token>`
2. **Query Parameter:** `?token=<token>` (useful for chatbots and simple scripts)

To get your token, use the `/api/playlistvoting/token` endpoint after logging in via the web interface, or check the `id` and `token`
returned by the Steam login endpoint.

---

## 1. Authentication Endpoints

### Login with Steam Ticket

- **Method:** `POST`
- **Path:** `/api/auth/steam/ticket`
- **Input:**
    - **Body (Text):** Steam Auth Ticket in Hex format.
- **Output:** JSON object
    ```json
    {
      "id": "user-uuid",
      "token": "auth-token",
      "displayName": "User Name",
      "steamId": "76561198..."
    }
    ```
- **Description:** Verifies a Steam ticket and returns user information including the auth token.

---

## 2. Session Management Endpoints (Host Only)

### Get My Token

- **Method:** `GET`
- **Path:** `/api/playlistvoting/token`
- **Authentication:** Required
- **Output:** `String` (The auth token)
- **Description:** Returns the auth token for the currently authenticated user.

### Create Session

- **Method:** `POST`
- **Path:** `/api/playlistvoting/sessions`
- **Authentication:** Required
- **Input:**
    - **Body (JSON):**
        ```json
        { "name": "Session Name" }
        ```
- **Output:** `201 Created` with confirmation message.
- **Description:** Creates a new voting session.

### Update Session State

- **Method:** `PATCH`
- **Path:** `/api/playlistvoting/sessions/{id}/state`
- **Authentication:** Required
- **Input:**
    - **Path Variable:** `id` (Session ID)
    - **Body (JSON):**
        ```json
        { "state": "ACTIVE" } // Values: ACTIVE, PAUSED, FINISHED
        ```
- **Output:** `204 No Content`
- **Description:** Changes the state of a specific session.

### Rename Active Session

- **Method:** `PATCH`
- **Path:** `/api/playlistvoting/sessions/active`
- **Authentication:** Required
- **Input:**
    - **Body (JSON):**
        ```json
        { "name": "New Name" }
        ```
- **Output:** `200 OK` with confirmation message.
- **Description:** Renames the currently active session.

### Pause Active Session

- **Method:** `POST`
- **Path:** `/api/playlistvoting/sessions/active/pause`
- **Authentication:** Required
- **Description:** Pauses the currently active session.

### Resume Active Session

- **Method:** `POST`
- **Path:** `/api/playlistvoting/sessions/active/resume`
- **Authentication:** Required
- **Description:** Resumes the currently paused session.

### Set Current Level

- **Method:** `POST`
- **Path:** `/api/playlistvoting/sessions/active/level`
- **Authentication:** Required
- **Input:**
    - **Body (JSON or Form):**
        ```json
        {
          "uid": "level-uid",
          "name": "Level Name",
          "author": "Author Name",
          "workshopID": 123456
        }
        ```
- **Output:** `200 OK`
- **Description:** Sets the level currently being played/voted on in the active session.

### Update Lobby Timer

- **Method:** `POST`
- **Path:** `/api/playlistvoting/sessions/active/timer`
- **Authentication:** Required
- **Input:**
    - **Query Parameter:** `timer` (e.g., "05:00")
- **Output:** `204 No Content`
- **Description:** Updates the displayed lobby timer.

### Reset Session Votes

- **Method:** `DELETE`
- **Path:** `/api/playlistvoting/sessions/{id}/votes`
- **Authentication:** Required
- **Description:** Resets all votes for the specified session.

---

## 3. Voting Endpoints

### Cast a Vote

- **Method:** `GET`
- **Path:** `/api/playlistvoting/votes`
- **Authentication:** Required (can be via `token` query param)
- **Input:**
    - **Query Parameters:**
        - `platformUserId`: The ID of the user on their platform (e.g., Steam ID, Twitch ID).
        - `username`: Display name of the voter.
        - `platform`: Voter's platform. Values: `TWITCH`, `STEAM`, `YOUTUBE`, `DISCORD`, `KIK`, `TIKTOK`, `OTHER`.
        - `vote`: The vote option. Values: `YES`, `NO`, `ABSTAIN`, `REMOVE`, `IDK`.
- **Output:** `200 OK` with a text result (e.g., "Vote recorded").
- **Description:** Records a vote for the current level in the active session.

### Cast Dashboard Vote

- **Method:** `POST`
- **Path:** `/api/playlistvoting/dashboard/votes`
- **Authentication:** Required
- **Input:**
    - **Query Parameters:**
        - `hostId`: The internal ID of the session host.
        - `vote`: The vote option (`YES`, `NO`, etc.).
- **Description:** Allows voting via the dashboard interface for a specific host.

---

## 4. Data Retrieval Endpoints

### Get Active Session Result

- **Method:** `GET`
- **Path:** `/api/playlistvoting/sessions/active/result`
- **Authentication:** Required
- **Output:** JSON object containing current level info and vote counts.
- **Example Response:**
    ```json
    {
      "sessionName": "My Session",
      "sessionState": "ACTIVE",
      "lobbyTimer": "02:30",
      "level": {
        "levelUid": "abc-123",
        "levelName": "Speed Run",
        "levelAuthor": "ProGamer",
        "workshopId": 987654,
        "status": "VOTING"
      },
      "votes": {
        "yes": 10,
        "no": 2,
        "abstain": 1,
        "total": 13
      }
    }
    ```

### Get Active Session Playlist

- **Method:** `GET`
- **Path:** `/api/playlistvoting/sessions/active/playlist`
- **Authentication:** Required
- **Output:** JSON object with all levels and their associated votes in the session.

### Download Playlist File

- **Method:** `GET`
- **Path:** `/api/playlistvoting/sessions/{id}/playlist`
- **Authentication:** Required
- **Input:**
    - **Query Parameters:**
        - `roundLength`: (Optional, default 360) Seconds per round.
        - `shuffle`: (Optional, default true) Whether to shuffle the playlist.
        - `name`: (Optional) Custom filename for the playlist.
- **Output:** `byte[]` (A `.zeeplevel` or JSON playlist file)
- **Description:** Generates and downloads a playlist file for the session results.

### Get Dashboard Data

- **Method:** `GET`
- **Path:** `/api/playlistvoting/dashboard`
- **Authentication:** Optional
- **Input:**
    - **Query Parameter:** `hostId` (Optional)
- **Description:** Returns a map of data used for the dashboard UI.

---

## Enums

### Platform

- `TWITCH`
- `STEAM`
- `YOUTUBE`
- `DISCORD`
- `KIK`
- `TIKTOK`
- `OTHER`

### VoteOption

- `YES`
- `NO`
- `ABSTAIN`
- `REMOVE`
- `IDK`

### SessionState

- `ACTIVE`
- `PAUSED`
- `FINISHED`
