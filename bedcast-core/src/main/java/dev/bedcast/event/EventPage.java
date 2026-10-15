package dev.bedcast.event;
import dev.bedcast.model.Immutable;
import java.util.*;
public final class EventPage {
    public final List<BroadcastEvent> events;
    public final String gameId;
    public final long oldestSequence;
    public final long nextCursor;
    public final boolean hasMore;
    public EventPage(List<BroadcastEvent> events, String gameId, long oldestSequence, long nextCursor, boolean hasMore) {
        this.events=Immutable.list(events); this.gameId=gameId; this.oldestSequence=oldestSequence; this.nextCursor=nextCursor; this.hasMore=hasMore;
    }
}
