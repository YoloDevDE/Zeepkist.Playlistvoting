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
graph TD
    Host((Host))
    Voter((Voter))
    Steam[Steam API]

    subgraph "Playlist Voting System"
        UC1(Create Session)
        UC2(Set Level)
        UC3(Cast Vote)
        UC4(Export Playlist)
        UC5(Login via Steam)
    end

    Host --- UC1
    Host --- UC2
    Host --- UC4
    Host --- UC5
    Voter --- UC3
    UC5 -. verifies .-> Steam
```

### Activity Diagram: Voting Process

This workflow describes how a level is set and votes are collected.

```mermaid
flowchart TD
    Start([Start]) --> SetLevel[Host sets current level]
    SetLevel --> OpenVoting[System opens voting for this level]

    OpenVoting --> ReceiveVote[Voter sends vote]
    ReceiveVote --> Valid{Vote valid?}

    Valid -- Yes --> SaveVote[Save/Update vote]
    SaveVote --> UpdateDash[Update dashboard via WebSocket]
    UpdateDash --> Finished{Level finished?}

    Valid -- No --> Error[Return error message]
    Error --> Finished

    Finished -- No --> ReceiveVote
    Finished -- Yes --> CloseVoting[Close voting for level]

    CloseVoting --> SaveResults[Save results in session history]
    SaveResults --> Stop([Stop])
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

## WebSocket Endpoints

In addition to REST endpoints, the system supports WebSockets for real-time updates.

- **Topic**: `/topic/timer` - Receives timer updates from the host.
- **Mapping**: `/app/timer` - Host sends timer status to the server.

## License

[Insert license here, if applicable]
