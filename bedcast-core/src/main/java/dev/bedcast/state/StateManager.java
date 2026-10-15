package dev.bedcast.state;
import dev.bedcast.provider.*;
import java.util.*;
import java.util.function.Function;
public final class StateManager {
    private final Map<String,ArenaSession> sessions=new LinkedHashMap<>();
    private final Map<String,List<ProviderEvent>> pending=new HashMap<>();
    private final int capacity;
    private final Function<String,MatchSettings> matches;
    public static final class MatchSettings {
        public final String matchId;
        public final int currentRound,totalRounds;
        public MatchSettings(String matchId,int currentRound,int totalRounds) {
            if(currentRound<1 || totalRounds<currentRound) throw new IllegalArgumentException("比赛轮次无效");
            this.matchId=matchId; this.currentRound=currentRound; this.totalRounds=totalRounds;
        }
        public static MatchSettings single() { return new MatchSettings(null,1,1); }
    }
    public StateManager(int capacity,Function<String,MatchSettings> matches) { this.capacity=capacity; this.matches=matches; }
    public void accept(ProviderEvent event) { pending.computeIfAbsent(event.arenaId,key->new ArrayList<>()).add(event); }
    public ArenaSession get(String id) { return sessions.get(id); }
    public ArenaSession create(ArenaDescriptor descriptor,long now) {
        ArenaSession session=new ArenaSession(descriptor,now,capacity,matches.apply(descriptor.arenaId)); sessions.put(descriptor.arenaId,session); return session;
    }
    public List<ProviderEvent> take(String id) { List<ProviderEvent> result=pending.remove(id); return result==null?Collections.emptyList():result; }
    public Set<String> ids() { return new LinkedHashSet<>(sessions.keySet()); }
    public void remove(String id) { sessions.remove(id); pending.remove(id); }
}
