package h34r7l3s.freakyworld;

import java.util.UUID;

public class EventInfo {
    private final UUID playerUUID;
    private int startTimestamp;
    private int duration;

    public EventInfo(UUID playerUUID) {
        this.playerUUID = playerUUID;
        this.startTimestamp = 0;
        this.duration = 0;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public int getStartTimestamp() {
        return startTimestamp;
    }

    public void setStartTimestamp(int startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }
}
