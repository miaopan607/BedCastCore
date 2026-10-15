package dev.bedcast.snapshot;

import dev.bedcast.event.*;
import dev.bedcast.model.*;
import dev.bedcast.provider.*;
import dev.bedcast.state.StateManager;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LifecycleTest {
    private static final String P="00000000-0000-0000-0000-000000000001";
    @Test void ordinaryDeathRespawnAndRepeatedEndPreserveRosterAndFreezeClock() {
        CollectionTest.Provider provider=new CollectionTest.Provider();
        provider.arenas.put("A",CollectionTest.arena("A",CollectionTest.player(P,2,true)));
        SnapshotStore store=new SnapshotStore();
        SnapshotManager manager=new SnapshotManager(provider,store,new StateManager(10,id->StateManager.MatchSettings.single()),()->{},batch->{},(id,e)->fail(e));
        manager.collect();
        manager.accept(new ProviderEvent("A",EventType.PLAYER_DEATH,System.currentTimeMillis(),Immutable.data("victimUuid",P,"killerUuid",null,"final",false)));
        PlayerState waiting=CollectionTest.player(P,2,true).withPresence(true,false,false,true,Immutable.data("participant",true,"qualified",true));
        provider.arenas.put("A",CollectionTest.arena("A",waiting)); manager.collect();
        assertTrue(store.get("A").players.get(P).respawning);assertEquals(1,store.get("A").teams.get("red").alivePlayers);
        manager.accept(new ProviderEvent("A",EventType.PLAYER_RESPAWN,System.currentTimeMillis(),Immutable.data("uuid",P)));
        provider.arenas.put("A",CollectionTest.arena("A",CollectionTest.player(P,2,true)));manager.collect();assertTrue(store.get("A").players.get(P).alive);
        long finish=System.currentTimeMillis();
        ProviderEvent end=new ProviderEvent("A",EventType.GAME_END,finish,Immutable.data("winnerTeamId","red","reason","VICTORY"));
        manager.accept(end);manager.accept(end);manager.collect();
        GameSnapshot ending=store.get("A");assertEquals(GamePhase.ENDING,ending.game.gameState);
        assertEquals(1,ending.events.stream().filter(e->e.eventType==EventType.GAME_END).count());
        provider.arenas.clear();manager.collect();GameSnapshot ended=store.get("A");
        assertEquals(GamePhase.ENDED,ended.game.gameState);assertEquals(2,ended.teams.get("red").kills);
        assertEquals(ending.game.elapsedTime,ended.game.elapsedTime);assertEquals("red",ended.game.winnerTeamId);
    }
    @Test void terminalCaptureAndImmediateNextArenaKeepEventsInTheirOwnGame() {
        boolean[] restarting={false};
        int[] captures={0};
        BedWarsProvider provider=new BedWarsProvider() {
            public String getId(){return "fixture";}
            public void start(ProviderEventSink sink){}
            public void close(){}
            public Collection<ArenaDescriptor> getArenas(){
                return restarting[0]?Arrays.asList(new ArenaDescriptor("A","A",1),new ArenaDescriptor("A","A",2))
                        :Collections.singletonList(new ArenaDescriptor("A","A",1));
            }
            public Optional<ArenaState> capture(String id){
                return Optional.of(CollectionTest.arena("A",CollectionTest.player(P,restarting[0]?(captures[0]++==0?3:0):2,true)));
            }
        };
        SnapshotStore store=new SnapshotStore();List<PublicationBatch> published=new ArrayList<>();
        SnapshotManager manager=new SnapshotManager(provider,store,new StateManager(10,id->StateManager.MatchSettings.single()),()->{},published::add,(id,e)->fail(e));
        manager.collect();String oldGame=store.get("A").gameId;
        manager.accept(ProviderEvent.ended("A",System.currentTimeMillis()));
        manager.accept(new ProviderEvent("A",EventType.PLAYER_JOIN,System.currentTimeMillis(),Immutable.data("uuid",P)));
        restarting[0]=true;manager.collect();
        GameSnapshot terminal=published.get(1).snapshot;
        assertEquals(GamePhase.ENDED,terminal.game.gameState);assertEquals(3,terminal.players.get(P).kills);
        assertTrue(terminal.events.stream().noneMatch(e->e.eventType==EventType.PLAYER_JOIN));
        assertNotEquals(oldGame,store.get("A").gameId);
        assertEquals(EventType.PLAYER_JOIN,store.get("A").events.get(0).eventType);
        assertEquals(0,store.get("A").players.get(P).kills);
    }
}
