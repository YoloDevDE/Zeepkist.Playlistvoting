# Backend Mod API Plan - Zeepkist Playlist Voting

## 1. Existing Endpoints Found
- `GET /api/playlistvoting/token`: Get authentication token for the current user.
- `POST /api/playlistvoting/sessions`: Create a new session.
- `PUT /api/playlistvoting/sessions/{id}/settings`: Update session settings including playlist and mode.
- `POST /api/playlistvoting/sessions/active/playlist`: Upload/replace the active session's playlist.
- `GET /api/playlistvoting/sessions/active/result`: Get results for the active session.
- `POST /api/playlistvoting/sessions/active/set-level`: Set the current level for the active session.
- `GET /api/playlistvoting/sessions/{id}/playlist`: Download the session's playlist.
- `POST /api/playlistvoting/vote`: Cast a vote (token-based).

## 2. Existing DTOs/Entities
- `VotingSession`: Main entity for voting sessions.
- `Level`: Entity for level metadata.
- `UserVote`: Entity for individual votes.
- `ZeeplistDTO`: DTO for playlist structure.
- `UpdatePlaylistRequest`: Request for updating playlist.
- `VotingResultResponse`: Response for voting results.

## 3. Required Features Already Exist
- Token-based authentication for most mod-facing endpoints.
- Basic session management (create, rename, pause, resume).
- Basic playlist upload and download.
- Setting current level.
- Casting votes.
- Getting active session results.

## 4. Missing Features
- Detailed active session metadata (remaining levels, playlist status, etc.).
- Latest active/resumable session lookup.
- Explicit playlist mode toggle endpoint.
- Filtered playlist downloads (toBeVoted, final YES).
- Level-specific finalization by UID.
- Level-specific vote reset.
- Level-specific result retrieval.
- Deduplication of playlist levels by UID.
- Safe playlist replacement that preserves votes (mostly exists, but needs verification).

## 5. Proposed Endpoint List

### 5.1. Session Metadata
- `GET /api/playlistvoting/sessions/active`: Returns `PlaylistVotingSessionInfoDto`.
- `GET /api/playlistvoting/sessions/latest-active`: Returns `PlaylistVotingSessionInfoDto` for the latest active/resumable session.

**PlaylistVotingSessionInfoDto**:
```json
{
  "id": "string",
  "displayName": "string",
  "state": "ACTIVE|PAUSED|FINISHED",
  "playlistModeEnabled": boolean,
  "hasPlaylist": boolean,
  "currentLevelUid": "string",
  "totalLevelCount": number,
  "finalizedLevelCount": number,
  "remainingLevelCount": number,
  "votingResult": { ... } // Optional/latest result
}
```

### 5.2. Playlist Management
- `PATCH /api/playlistvoting/sessions/active/playlist-mode`: Body `{"enabled": true}`.
- `GET /api/playlistvoting/sessions/{id}/playlist?filter=toBeVoted`: Returns playlist with non-finalized levels.
- `GET /api/playlistvoting/sessions/{id}/playlist?filter=final`: Returns playlist with YES results.

### 5.3. Level Management (Mod focused)
- `POST /api/playlistvoting/sessions/active/levels/{levelUid}/finalize`: Finalizes a specific level.
- `DELETE /api/playlistvoting/sessions/active/levels/{levelUid}/votes`: Resets votes for a specific level.
- `GET /api/playlistvoting/sessions/active/levels/{levelUid}/result`: Returns result for a specific level.

## 6. Migration / Backward Compatibility
- Existing endpoints will remain as they are or be extended.
- `UpdatePlaylistRequest` already supports `playlistMode`.
- `VotingSession` already has most fields needed.
- `LevelStatuses` in `VotingSession` will be used to track finalized levels (e.g., status "FINALIZED" or similar).

## 7. Risks / TODOs
- Ensure `levelStatuses` is consistently used for finalization.
- Check if `UserVote` needs cleanup when a level is removed from playlist (probably not, as per requirement).
- Verify token-based access for all new endpoints.
- Deduplication logic when uploading playlist.
