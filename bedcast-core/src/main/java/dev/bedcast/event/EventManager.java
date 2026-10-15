package dev.bedcast.event;
import dev.bedcast.model.Immutable;
import dev.bedcast.snapshot.GameSnapshot;
import java.util.*;
/** 单写者有界事件窗口；读者拿到的历史视图永不变化。 */
public final class EventManager {
    private final int capacity;
    private final String arenaId, gameId;
    private final Deque<BroadcastEvent> ring = new ArrayDeque<>();
    private List<BroadcastEvent> published = Collections.emptyList();
    private long sequence;
    private boolean dirty;
    public EventManager(String arenaId, String gameId, int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("events.capacity-per-arena 必须大于 0");
        this.arenaId=arenaId; this.gameId=gameId; this.capacity=capacity;
    }
    public BroadcastEvent append(EventType type, long timestamp, long gameTime, Map<String,Object> data) {
        BroadcastEvent event=new BroadcastEvent(arenaId,gameId,++sequence,type,timestamp,gameTime,data);
        if (ring.size()==capacity) ring.removeFirst();
        ring.addLast(event); dirty=true; return event;
    }
    public long cursor() { return sequence; }
    public List<BroadcastEvent> history() {
        if (dirty) { published=Immutable.list(ring); dirty=false; }
        return published;
    }
    public static EventPage page(GameSnapshot snapshot, String gameId, long after, int limit) {
        if (limit<1 || limit>500 || after<0) throw new EventQueryException("INVALID_QUERY");
        if (gameId!=null && !gameId.equals(snapshot.gameId)) throw new EventQueryException("GAME_CHANGED");
        long oldest=snapshot.events.isEmpty()?snapshot.eventCursor+1:snapshot.events.get(0).sequence;
        if (after < oldest-1) throw new EventQueryException("EVENT_CURSOR_EXPIRED");
        if (after > snapshot.eventCursor) throw new EventQueryException("INVALID_CURSOR");
        List<BroadcastEvent> result=new ArrayList<>();
        for (BroadcastEvent event:snapshot.events) if (event.sequence>after && result.size()<limit) result.add(event);
        long next=result.isEmpty()?after:result.get(result.size()-1).sequence;
        return new EventPage(result,snapshot.gameId,oldest,next,next<snapshot.eventCursor);
    }
    public static EventPage recent(GameSnapshot snapshot, int limit) {
        if (limit<1 || limit>500) throw new EventQueryException("INVALID_QUERY");
        List<BroadcastEvent> events=snapshot.events;
        return new EventPage(events.subList(Math.max(0,events.size()-limit),events.size()),snapshot.gameId,
                events.isEmpty()?1:events.get(0).sequence,snapshot.eventCursor,false);
    }
}
