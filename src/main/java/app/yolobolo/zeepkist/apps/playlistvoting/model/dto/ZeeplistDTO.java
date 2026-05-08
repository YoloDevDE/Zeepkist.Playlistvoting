package app.yolobolo.zeepkist.apps.playlistvoting.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class ZeeplistDTO
{
    private String name;
    private int amountOfLevels;
    private double roundLength;
    private boolean shufflePlaylist;
    @JsonProperty("UID")
    private List<String> uid;
    private List<ZeeplistLevelDTO> levels;

    @Data
    public static class ZeeplistLevelDTO
    {
        @JsonProperty("UID")
        private String uid;
        @JsonProperty("WorkshopID")
        private Long workshopId;
        @JsonProperty("Name")
        private String name;
        @JsonProperty("Collaborators")
        private String collaborators;
        @JsonProperty("OverrideAuthorName")
        private String overrideAuthorName;
        @JsonProperty("Author")
        private String author;
        private boolean played;
    }
}
