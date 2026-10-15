package dev.bedcast.snapshot;

import dev.bedcast.model.*;
import dev.bedcast.provider.*;
import dev.bedcast.state.StateManager;
import dev.bedcast.event.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CollectionTest {
    private static final String UUID_A="00000000-0000-0000-0000-000000000001";
    private static final String UUID_B="00000000-0000-0000-0000-000000000002";
    static PlayerState player(String id,int kills,boolean online) {
        return new PlayerState(id,id,"red",online,online,false,false,20d,20d,0d,20,kills,0,0,0,0,null,null,null,null,
                new EquipmentState(new ItemState("AIR",0,0,Collections.emptyMap()),"NONE","LEATHER","NONE","NONE",false,Collections.emptyMap()),
                Collections.emptyMap(),new ResourceState(0,0,0,0),Collections.emptyMap(),new Position("world",0,64,0),0f,0f,"BASE",
                Immutable.data("participant",true,"participating",online,"qualified",true));
    }
    static ArenaState arena(String id,PlayerState... players) {
        Map<String,PlayerState> p=new LinkedHashMap<>(); for(PlayerState player:players)p.put(player.uuid,player);
        TeamState t=new TeamState("red","red","Red","RED",true,false,players.length,players.length,0,0,0,0,false,0,0,0,Collections.emptyList(),null,Collections.emptyMap());
        GameState g=new GameState(id,null,null,id,GamePhase.RUNNING,System.currentTimeMillis()-10_000,10,1,1,"solo",1,1,null,1,1,60,60,Collections.emptyMap());
        return new ArenaState(g,p,Collections.singletonMap("red",t));
    }
    static final class Provider implements BedWarsProvider {
        final Map<String,ArenaState> arenas=new LinkedHashMap<>(); long generation=1; boolean fail;
        public String getId(){return "fixture";} public void start(ProviderEventSink sink){} public void close(){}
        public Collection<ArenaDescriptor> getArenas(){List<ArenaDescriptor>d=new ArrayList<>();arenas.forEach((id,a)->d.add(new ArenaDescriptor(id,id,generation)));return d;}
        public Optional<ArenaState> capture(String id){if(fail)throw new IllegalStateException("capture failed");return Optional.ofNullable(arenas.get(id));}
    }
    private static SnapshotManager manager(Provider p,SnapshotStore store,int capacity) {
        return new SnapshotManager(p,store,new StateManager(capacity,id->StateManager.MatchSettings.single()),()->{},batch->{},(id,error)->{});
    }
    @Test void arenasWithSameUuidAreIsolatedAndDisconnectRetainsStatistics() {
        Provider p=new Provider(); p.arenas.put("A",arena("A",player(UUID_A,3,true))); p.arenas.put("B",arena("B",player(UUID_A,7,true)));
        SnapshotStore store=new SnapshotStore(); SnapshotManager m=manager(p,store,20); m.collect();
        GameSnapshot before=store.get("A");
        m.accept(new ProviderEvent("A",EventType.PLAYER_DISCONNECT,System.currentTimeMillis(),Immutable.data("uuid",UUID_A,"teamId","red")));
        p.arenas.put("A",arena("A",player(UUID_A,3,false)));m.collect();
        assertFalse(store.get("A").players.get(UUID_A).online); assertEquals(3,store.get("A").players.get(UUID_A).kills);
        assertEquals(7,store.get("B").players.get(UUID_A).kills);assertEquals(0,store.get("B").eventCursor);
        assertTrue(before.players.get(UUID_A).online);assertEquals(0,before.eventCursor);
    }
    @Test void finalKillProducesLinkedEventsWithoutDoubleCountingAndNewGameInvalidatesCursor() {
        Provider p=new Provider();p.arenas.put("A",arena("A",player(UUID_A,0,true),player(UUID_B,0,true)));
        SnapshotStore store=new SnapshotStore();SnapshotManager m=manager(p,store,20);m.collect();String old=store.get("A").gameId;
        m.accept(new ProviderEvent("A",EventType.PLAYER_DEATH,System.currentTimeMillis(),Immutable.data("victimUuid",UUID_B,"killerUuid",UUID_A,"final",true)));
        p.arenas.put("A",arena("A",player(UUID_A,1,true),player(UUID_B,0,false)));m.collect();
        GameSnapshot s=store.get("A");assertEquals(Arrays.asList(EventType.PLAYER_DEATH,EventType.PLAYER_KILL,EventType.PLAYER_FINAL_KILL),
                Arrays.asList(s.events.get(0).eventType,s.events.get(1).eventType,s.events.get(2).eventType));
        assertEquals(s.events.get(0).eventId,s.events.get(2).data.get("deathId")); assertEquals(1,s.players.get(UUID_A).kills);
        assertEquals(1,s.teams.get("red").alivePlayers);
        p.generation++;m.collect();assertNotEquals(old,store.get("A").gameId);
        assertEquals("GAME_CHANGED",assertThrows(EventQueryException.class,()->EventManager.page(store.get("A"),old,0,100)).code);
    }
    @Test void failedCaptureDoesNotConsumeEventsOrExposeStaleSnapshotAsAvailable() {
        Provider p=new Provider();p.arenas.put("A",arena("A",player(UUID_A,0,true)));SnapshotStore store=new SnapshotStore();SnapshotManager m=manager(p,store,2);m.collect();
        m.accept(new ProviderEvent("A",EventType.ITEM_PURCHASE,1,Immutable.data("playerUuid",UUID_A,"itemId","wool")));
        p.fail=true;m.collect();assertFalse(store.get("A").available);
        p.fail=false;m.collect();assertTrue(store.get("A").available);assertEquals(1,store.get("A").events.size());
        m.accept(new ProviderEvent("A",EventType.ITEM_PURCHASE,2,Immutable.data("itemId","tnt")));
        m.accept(new ProviderEvent("A",EventType.ITEM_PURCHASE,3,Immutable.data("itemId","fireball")));m.collect();
        assertEquals(2,store.get("A").events.size());
        assertEquals("EVENT_CURSOR_EXPIRED",assertThrows(EventQueryException.class,()->EventManager.page(store.get("A"),null,0,20)).code);
        assertEquals(3,EventManager.page(store.get("A"),null,1,20).nextCursor);
    }
}
