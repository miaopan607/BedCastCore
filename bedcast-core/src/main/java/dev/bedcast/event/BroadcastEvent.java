package dev.bedcast.event;
import dev.bedcast.model.Immutable;
import java.util.*;
public final class BroadcastEvent {
    public final String eventId;
    public final EventType eventType;
    public final String arenaId;
    public final String gameId;
    public final long sequence;
    public final long timestamp;
    public final long gameTime;
    public final Map<String,Object> data;
    public BroadcastEvent(String arenaId, String gameId, long sequence, EventType type, long timestamp, long gameTime, Map<String,Object> data) {
        this.eventId = gameId + ":" + sequence; this.eventType = type; this.arenaId = arenaId; this.gameId = gameId;
        this.sequence = sequence; this.timestamp = timestamp; this.gameTime = gameTime; this.data = Immutable.json(data);
    }
}
