# Zeepkist Playlist Voting

This project allows Zeepkist players to create voting sessions for their playlists. Spectators (e.g., via Twitch or Steam) can vote on the levels currently being played.

## Features

- **Session Management**: Create, pause, and rename voting sessions.
- **Real-time Voting**: Support for various platforms (Steam, Twitch, etc.).
- **Playlist Generation**: Export voting results as a Zeepkist playlist file.
- **Dashboard**: Clear web interface for hosts and voters.

## Workflows

### Use Case Diagram

The following diagram shows the interactions of different actors with the system.

```mermaid
useCaseDiagram
    actor Host
    actor Voter
    actor "Steam API" as Steam

    package "Playlist Voting System" {
        usecase "Create Session" as UC1
        usecase "Set Level" as UC2
        usecase "Cast Vote" as UC3
        usecase "Export Playlist" as UC4
        usecase "Login via Steam" as UC5
    }

    Host --> UC1
    Host --> UC2
    Host --> UC4
    Host --> UC5
    Voter --> UC3
    UC5 ..> Steam : verifies
```

### Activity Diagram: Voting Process

This workflow describes how a level is set and votes are collected.

```mermaid
activityDiagram
    start
    :Host sets current level;
    :System opens voting for this level;
    repeat
        :Voter sends vote;
        if (Vote valid?) then (yes)
            :Save/Update vote;
            :Update dashboard via WebSocket;
        else (no)
            :Return error message;
        endif
    backward:Next vote;
    repeat while (Level finished?) is (no)
    :Close voting for level;
    :Save results in session history;
    stop
```

## Setup & Installation

### Prerequisites

- Java 21 or higher
- MongoDB (local or Atlas)
- Steam API Key (for authentication)

### Starting the Application

1. Clone the repository.
2. Configure `application.properties` (MongoDB URI, Steam API Key).
3. Start the application via Gradle:
   ```bash
   ./gradlew bootRun
   ```

## API Documentation

Interactive API documentation (Swagger UI) is available after startup at:
`http://localhost:8080/swagger-ui/index.html`

A static description of the endpoints can also be found in [API_DOCUMENTATION.md](API_DOCUMENTATION.md).

## WebSocket Endpoints

In addition to REST endpoints, the system supports WebSockets for real-time updates.

- **Topic**: `/topic/timer` - Receives timer updates from the host.
- **Mapping**: `/app/timer` - Host sends timer status to the server.

## License

[Insert license here, if applicable]
