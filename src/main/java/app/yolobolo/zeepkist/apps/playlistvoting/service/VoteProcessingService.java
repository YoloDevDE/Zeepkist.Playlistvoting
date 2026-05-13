package app.yolobolo.zeepkist.apps.playlistvoting.service;

import app.yolobolo.zeepkist.apps.playlistvoting.model.UserVote;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.response.VotesResponse;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.Platform;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.VoteOption;
import app.yolobolo.zeepkist.apps.playlistvoting.repository.UserVoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoteProcessingService
{

    private final UserVoteRepository voteRepository;

    public List<UserVote> getVotes(String sessionId, String levelUid)
    {
        return voteRepository.findBySessionIdAndLevelUid(sessionId, levelUid);
    }

    public Optional<UserVote> getVote(String sessionId, String levelUid, String platformUserId, Platform platform)
    {
        return voteRepository.findBySessionIdAndLevelUidAndPlatformAndPlatformUserId(sessionId, levelUid, platform, platformUserId);
    }

    public String castVote(String sessionId, String levelUid, String platformUserId, String platformUsername, Platform platform, VoteOption option)
    {
        Optional<UserVote> existing = voteRepository.findBySessionIdAndLevelUidAndPlatformAndPlatformUserId(
                sessionId, levelUid, platform, platformUserId);

        UserVote userVote = existing.orElse(new UserVote());
        userVote.setSessionId(sessionId);
        userVote.setLevelUid(levelUid);
        userVote.setPlatform(platform);
        userVote.setPlatformUserId(platformUserId);
        userVote.setPlatformUsername(platformUsername);
        userVote.setVote(option);
        userVote.setModifiedAt(Instant.now());

        voteRepository.save(userVote);
        log.info("Vote cast: {} by {} on {} (Level: {})", option, platformUsername, platform, levelUid);
        return "Vote registered: " + option;
    }

    public String removeVote(String sessionId, String levelUid, String platformUserId, Platform platform)
    {
        Optional<UserVote> existing = voteRepository.findBySessionIdAndLevelUidAndPlatformAndPlatformUserId(
                sessionId, levelUid, platform, platformUserId);

        if (existing.isEmpty())
        {
            return "No vote found to remove";
        }

        voteRepository.delete(existing.get());
        log.info("Vote removed for {} on {} (Level: {})", platformUserId, platform, levelUid);
        return "Vote removed";
    }

    public void resetVotes(String sessionId)
    {
        voteRepository.deleteBySessionId(sessionId);
        log.info("All votes reset for session: {}", sessionId);
    }

    public void deleteVoteById(String voteId)
    {
        voteRepository.deleteById(voteId);
        log.info("Vote deleted by ID: {}", voteId);
    }

    public Map<String, Object> calculateVotesMap(List<UserVote> votes, boolean allowAbstain)
    {
        VotesResponse response = calculateVotesResponse(votes, allowAbstain);

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("yes", response.getYes());
        map.put("no", response.getNo());
        if (allowAbstain)
        {
            map.put("abstain", response.getAbstain());
        }
        map.put("total", response.getTotal());
        map.put("allowAbstain", response.isAllowAbstain());
        return map;
    }

    public VotesResponse calculateVotesResponse(List<UserVote> votes)
    {
        return calculateVotesResponse(votes, true);
    }

    public VotesResponse calculateVotesResponse(List<UserVote> votes, boolean allowAbstain)
    {
        if (votes == null)
        {
            return VotesResponse.builder()
                    .yes(0)
                    .no(0)
                    .abstain(0)
                    .total(0)
                    .allowAbstain(allowAbstain)
                    .platforms(Map.of())
                    .build();
        }
        long yes = votes.stream().filter(v -> v.getVote() == VoteOption.YES).count();
        long no = votes.stream().filter(v -> v.getVote() == VoteOption.NO).count();
        long abstain = votes.stream().filter(v -> v.getVote() == VoteOption.ABSTAIN).count();

        Map<String, Long> platforms = votes.stream()
                .filter(v -> allowAbstain || (v.getVote() != VoteOption.ABSTAIN && v.getVote() != VoteOption.IDK))
                .collect(Collectors.groupingBy(v -> v.getPlatform().name(), Collectors.counting()));

        long total = allowAbstain ? (yes + no + abstain) : (yes + no);

        return VotesResponse.builder()
                .yes(yes)
                .no(no)
                .abstain(allowAbstain ? abstain : 0)
                .total(total)
                .allowAbstain(allowAbstain)
                .platforms(platforms)
                .build();
    }
}
