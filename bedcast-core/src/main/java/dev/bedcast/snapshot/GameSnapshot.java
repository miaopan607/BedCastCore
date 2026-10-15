package dev.bedcast.snapshot;
import dev.bedcast.model.*;
import dev.bedcast.event.BroadcastEvent;
import java.util.*;
public final class GameSnapshot {
    public final String arenaId, gameId;
    public final long revision, timestamp, eventCursor, coverageStartTime;
    public final GameState game;
    public final Map<String,TeamState> teams;
    public final Map<String,PlayerState> players;
    public final transient List<BroadcastEvent> events;
    public final transient boolean available;
    public GameSnapshot(String arenaId, String gameId, long revision, long timestamp, long eventCursor, long coverageStartTime,
                        GameState game, Map<String,TeamState> teams, Map<String,PlayerState> players, List<BroadcastEvent> events, boolean available) {
        this.arenaId=arenaId; this.gameId=gameId; this.revision=revision; this.timestamp=timestamp;
        this.eventCursor=eventCursor; this.coverageStartTime=coverageStartTime; this.game=game;
        this.teams=Immutable.map(teams); this.players=Immutable.map(players); this.events=Immutable.list(events); this.available=available;
    }
    public GameSnapshot unavailable() {
        return new GameSnapshot(arenaId,gameId,revision,timestamp,eventCursor,coverageStartTime,game,teams,players,events,false);
    }
}
