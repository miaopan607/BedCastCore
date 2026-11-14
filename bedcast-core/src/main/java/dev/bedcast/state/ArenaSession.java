package dev.bedcast.state;

import dev.bedcast.model.*;
import dev.bedcast.event.*;
import dev.bedcast.provider.*;
import dev.bedcast.snapshot.*;
import java.util.*;
import static dev.bedcast.model.Immutable.data;

/** 单写者比赛会话，保留离线选手及结算数据。 */
public final class ArenaSession {
    public final String arenaId, gameId;
    public final long generation, coverageStartTime;
    private final String matchId;
    private final int round, rounds;
    private final EventManager events;
    private final Map<String,Combat> combat=new HashMap<>();
    private final Set<String> eliminatedPlayers=new HashSet<>(), eliminatedTeams=new HashSet<>();
    private final List<BroadcastEvent> additions=new ArrayList<>();
    private GameSnapshot current;
    private boolean gameStarted, gameEnded, ended;
    private boolean completeCoverage;
    private Long finishTime;
    private String winner;
    private long revision;
    private static final class Combat {
        Integer streak; String killer, victim; Long deathTime, killTime;
        Combat(boolean complete) { streak=complete?0:null; }
    }
    public ArenaSession(ArenaDescriptor descriptor, long now, int capacity, StateManager.MatchSettings match) {
        arenaId=descriptor.arenaId; generation=descriptor.generation; gameId=UUID.randomUUID().toString();
        coverageStartTime=now; events=new EventManager(arenaId,gameId,capacity);
        matchId=match.matchId==null?gameId:match.matchId; round=match.currentRound; rounds=match.totalRounds;
    }
    private long time(long timestamp, GameState game) {
        if(game==null || game.startTime==null) return 0;
        return Math.max(0, (Math.min(timestamp,finishTime==null?timestamp:finishTime)-game.startTime)/1000);
    }
    private BroadcastEvent emit(EventType type,long timestamp,GameState game,Map<String,Object> payload) {
        BroadcastEvent event=events.append(type,timestamp,time(timestamp,game),payload); additions.add(event); return event;
    }
    private Combat combat(String uuid) { return combat.computeIfAbsent(uuid,key->new Combat(completeCoverage)); }
    private void apply(ProviderEvent source, GameState game) {
        if(source.ended) { ended=true; return; }
        EventType type=source.type;
        Map<String,Object> payload=source.data;
        if(type==EventType.GAME_START) {
            if(gameStarted) return; gameStarted=true; completeCoverage=true;
            for(Combat c:combat.values()) if(c.streak==null) c.streak=0;
        }
        if(type==EventType.GAME_END) {
            if(gameEnded) return; gameEnded=true; finishTime=source.timestamp; winner=(String)payload.get("winnerTeamId");
        }
        if(type==EventType.TEAM_ELIMINATED) eliminatedTeams.add((String)payload.get("teamId"));
        if(type==EventType.PLAYER_DEATH) {
            String victim=(String)payload.get("victimUuid"), killer=(String)payload.get("killerUuid");
            boolean finalKill=Boolean.TRUE.equals(payload.get("final"));
            Combat dead=combat(victim); dead.streak=0; dead.killer=killer; dead.deathTime=source.timestamp;
            if(finalKill) eliminatedPlayers.add(victim);
            BroadcastEvent death=emit(type,source.timestamp,game,payload);
            if(killer!=null && !killer.equals(victim)) {
                Combat attacking=combat(killer); if(attacking.streak!=null) attacking.streak++;
                attacking.victim=victim; attacking.killTime=source.timestamp;
                Map<String,Object> kill=new LinkedHashMap<>(payload); kill.put("deathId",death.eventId);
                emit(EventType.PLAYER_KILL,source.timestamp,game,kill);
                if(finalKill) emit(EventType.PLAYER_FINAL_KILL,source.timestamp,game,kill);
            }
            return;
        }
        emit(type,source.timestamp,game,payload);
    }
    public GameSnapshot update(ArenaState observed, List<ProviderEvent> pending, long now) {
        GameState raw=observed.game;
        if(current==null) { completeCoverage=raw.gameState==GamePhase.WAITING || raw.gameState==GamePhase.COUNTDOWN; }
        for(ProviderEvent event:pending) apply(event,raw);
        if(ended && !gameEnded) apply(new ProviderEvent(arenaId,EventType.GAME_END,now,data("winnerTeamId",null,"reason","ABORTED")),raw);
        Map<String,PlayerState> players=new LinkedHashMap<>();
        if(current!=null) for(PlayerState p:current.players.values()) if(Boolean.TRUE.equals(p.extensions.get("participant"))) players.put(p.uuid,p);
        for(PlayerState p:observed.players.values()) {
            PlayerState value=p;
            Combat c=combat(p.uuid);
            value=value.withCombat(c.streak,c.killer,c.victim,c.deathTime,c.killTime);
            PlayerState old=current==null?null:current.players.get(p.uuid);
            if(value.equals(old)) value=old;
            players.put(p.uuid,value);
        }
        for(ProviderEvent event:pending) {
            if(event.type==EventType.PLAYER_LEAVE || event.type==EventType.PLAYER_DISCONNECT) {
                String id=(String)event.data.get("uuid"); PlayerState p=players.get(id);
                if(p!=null) { Map<String,Object> ext=new LinkedHashMap<>(p.extensions); ext.put("participating",false); ext.put("lastObservedAt",event.timestamp);
                    // 若同一批次已经重连，最后一个 presence 事件决定状态。
                    boolean rejoined=false;
                    for(int i=pending.indexOf(event)+1;i<pending.size();i++) { ProviderEvent later=pending.get(i);
                        if((later.type==EventType.PLAYER_RECONNECT || later.type==EventType.PLAYER_JOIN) && id.equals(later.data.get("uuid"))) rejoined=true; }
                    if(!rejoined) players.put(id,p.withPresence(event.type==EventType.PLAYER_DISCONNECT?false:p.online,false,p.spectator,false,ext));
                }
            }
        }
        Map<String,TeamState> teams=new LinkedHashMap<>();
        for(TeamState t:observed.teams.values()) {
            int count=0,alive=0; Integer kills=0,deaths=0,finalKills=0,beds=0;
            for(PlayerState p:players.values()) if(t.teamId.equals(p.team) && Boolean.TRUE.equals(p.extensions.get("participant"))) {
                count++; if(!eliminatedPlayers.contains(p.uuid) && Boolean.TRUE.equals(p.extensions.get("qualified"))) alive++;
                kills=add(kills,p.kills); deaths=add(deaths,p.deaths); finalKills=add(finalKills,p.finalKills); beds=add(beds,p.bedsDestroyed);
            }
            TeamState value=t.withTotals(t.eliminated||eliminatedTeams.contains(t.teamId),count,alive,kills,deaths,finalKills,beds);
            TeamState old=current==null?null:current.teams.get(t.teamId);
            if(old!=null && old.bedAlive && !value.bedAlive && pending.stream().noneMatch(e->e.type==EventType.BED_DESTROYED && t.teamId.equals(e.data.get("victimTeamId"))))
                emit(EventType.BED_DESTROYED,now,raw,data("playerUuid",null,"teamId",null,"victimTeamId",t.teamId));
            teams.put(t.teamId,value.equals(old)?old:value);
        }
        int aliveTeams=0; for(TeamState t:teams.values()) if(!t.eliminated && t.alivePlayers>0) aliveTeams++;
        GamePhase phase=ended?GamePhase.ENDED:(gameEnded?GamePhase.ENDING:raw.gameState);
        GameState sessionGame=new GameState(arenaId,gameId,matchId,raw.mapName,phase,raw.startTime,time(now,raw),round,rounds,raw.gameMode,
                teams.size(),aliveTeams,winner,raw.diamondLevel,raw.emeraldLevel,raw.nextDiamondUpgrade,raw.nextEmeraldUpgrade,raw.extensions);
        current=new GameSnapshot(arenaId,gameId,++revision,now,events.cursor(),coverageStartTime,sessionGame,teams,players,events.history(),true,observed.map);
        return current;
    }
    private static Integer add(Integer left,Integer right) { return left==null||right==null?null:left+right; }
    public GameSnapshot finish(List<ProviderEvent> pending,long now) {
        if(current==null) return null;
        List<ProviderEvent> copy=new ArrayList<>(pending); copy.add(ProviderEvent.ended(arenaId,now));
        return update(new ArenaState(current.game,current.players,current.teams,current.map),copy,now);
    }
    public List<BroadcastEvent> drain() { List<BroadcastEvent> result=new ArrayList<>(additions); additions.clear(); return result; }
    public GameSnapshot current() { return current; }
    public boolean isEnded() { return ended; }
}
