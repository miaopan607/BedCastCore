package dev.bedcast.provider.hmo;

import com.andrei1058.bedwars.api.BedWars;
import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.arena.team.ITeam;
import com.andrei1058.bedwars.api.arena.stats.*;
import com.andrei1058.bedwars.api.upgrades.EnemyBaseEnterTrap;
import dev.bedcast.model.*;
import dev.bedcast.provider.ArenaState;
import org.bukkit.*;
import org.bukkit.entity.Player;
import java.util.*;

final class HmoStateMapper {
    private final BedWars api;
    private final HmoInventoryReader inventory;
    HmoStateMapper(BedWars api){this.api=api;inventory=new HmoInventoryReader(api);}
    static GamePhase phase(IArena arena) {
        switch(arena.getStatus()) {case starting:return GamePhase.COUNTDOWN;case playing:return GamePhase.RUNNING;case restarting:return GamePhase.ENDING;default:return GamePhase.WAITING;}
    }
    static String id(ITeam team){return team==null?null:team.getIdentity().toString();}
    ITeam team(IArena arena,Player player,UUID uuid) {
        ITeam result=player==null?null:arena.getTeam(player);if(result==null)result=arena.getExTeam(uuid);
        if(result==null)for(ITeam candidate:arena.getTeams())if(candidate.wasMember(uuid)){result=candidate;break;}
        return result;
    }
    PlayerState player(IArena arena,UUID uuid,PlayerGameStats stats,PlayerState old,Set<String> finalDeaths) {
        Player live=Bukkit.getPlayer(uuid);boolean inArena=live!=null && api.getArenaUtil().getArenaByPlayer(live)==arena;
        boolean spectator=inArena&&arena.isSpectator(uuid),respawning=inArena&&arena.isReSpawning(uuid);
        ITeam team=team(arena,inArena?live:null,uuid);String teamId=id(team);
        boolean participant=team!=null || (inArena && arena.isPlayer(live)) || stats!=null || (old!=null && Boolean.TRUE.equals(old.extensions.get("participant")));
        boolean qualified=participant&&!finalDeaths.contains(uuid.toString()) && ((inArena&&(!spectator||respawning)) ||
                (!inArena&&team!=null&&!team.isBedDestroyed()&&phase(arena)==GamePhase.RUNNING&&(live==null||api.getArenaUtil().getArenaByPlayer(live)==null)));
        boolean knownZero=phase(arena)==GamePhase.WAITING||phase(arena)==GamePhase.COUNTDOWN||!participant;
        Integer kills=stat(stats,DefaultStatistics.KILLS,knownZero),deaths=stat(stats,DefaultStatistics.DEATHS,knownZero),
                finals=stat(stats,DefaultStatistics.KILLS_FINAL,knownZero),beds=stat(stats,DefaultStatistics.BEDS_DESTROYED,knownZero);
        if(inArena) {
            String area="UNKNOWN";Location position=live.getLocation();
            for(ITeam base:arena.getTeams())if(base.getBed()!=null&&base.getBed().getWorld().equals(position.getWorld())&&base.getBed().distanceSquared(position)<=arena.getIslandRadius()*arena.getIslandRadius()){area="BASE";break;}
            return inventory.read(live,teamId,participant,true,spectator,respawning,qualified,kills,deaths,finals,beds,area);
        }
        if(old!=null) {
            Map<String,Object> ext=new LinkedHashMap<>(old.extensions);ext.put("participating",false);ext.put("qualified",qualified);
            return old.withStatistics(kills==null?old.kills:kills,deaths==null?old.deaths:deaths,finals==null?old.finalKills:finals,beds==null?old.bedsDestroyed:beds)
                    .withPresence(live!=null&&live.isOnline(),false,old.spectator,false,ext);
        }
        String name=stats==null?(live==null?uuid.toString():live.getName()):stats.getUsername();
        return new PlayerState(uuid.toString(),name,teamId,live!=null&&live.isOnline(),false,false,false,null,null,null,null,kills,deaths,finals,beds,null,null,null,null,null,
                null,Collections.emptyMap(),null,Collections.emptyMap(),null,null,null,"UNKNOWN",Immutable.data("participant",participant,"participating",false,"qualified",qualified));
    }
    private static Integer stat(PlayerGameStats stats,DefaultStatistics id,boolean knownZero) {
        if(stats==null)return knownZero?0:null;
        return stats.getStatistic(id).map(GameStatistic::getValue).filter(Number.class::isInstance).map(value->((Number)value).intValue()).orElse(knownZero?0:null);
    }
    ArenaState capture(HmoBedWarsProvider.Context context) {
        IArena arena=context.arena;Map<UUID,PlayerGameStats> statistics=new LinkedHashMap<>();
        if(arena.getStatsHolder()!=null)for(Optional<PlayerGameStats> entry:arena.getStatsHolder().getTrackedPlayers())entry.ifPresent(stats->statistics.put(stats.getPlayer(),stats));
        Set<UUID> uuids=new LinkedHashSet<>(statistics.keySet());
        for(Player p:arena.getPlayers())uuids.add(p.getUniqueId());for(Player p:arena.getSpectators())uuids.add(p.getUniqueId());
        for(Player p:arena.getRespawnSessions().keySet())uuids.add(p.getUniqueId());for(String id:context.last.keySet())uuids.add(UUID.fromString(id));
        Map<String,PlayerState> players=new LinkedHashMap<>();
        for(UUID uuid:uuids) {
            PlayerState value=player(arena,uuid,statistics.get(uuid),context.last.get(uuid.toString()),context.finalDeaths);
            if(Boolean.TRUE.equals(value.extensions.get("participant")) || Boolean.TRUE.equals(value.extensions.get("participating")))players.put(value.uuid,value);
        }
        context.last.clear();context.last.putAll(players);
        Map<String,TeamState> teams=new LinkedHashMap<>();int aliveTeams=0;
        for(ITeam t:arena.getTeams()) {
            List<TrapState> traps=new ArrayList<>();for(EnemyBaseEnterTrap trap:t.getActiveTraps())traps.add(new TrapState(trap.getNameMsgPath(),api.getDefaultLang().m(trap.getNameMsgPath())));
            boolean used=players.values().stream().anyMatch(p->id(t).equals(p.team));
            boolean eliminated=used && t.isBedDestroyed()&&t.getMembers().isEmpty();if(!eliminated&&used)aliveTeams++;
            Map<String,Integer> upgrades=t.getTeamUpgradeTiers();
            TeamState state=new TeamState(id(t),t.getName(),t.getDisplayName(api.getDefaultLang()),t.getColor().name(),!t.isBedDestroyed(),eliminated,0,0,0,0,0,0,
                    level(upgrades,"upgrade-swords")>0,level(upgrades,"upgrade-armor"),level(upgrades,"upgrade-miner"),level(upgrades,"upgrade-forge"),traps,traps.isEmpty()?null:traps.get(0),Collections.singletonMap("upgrades",upgrades));
            teams.put(state.teamId,state);
        }
        GamePhase phase=phase(arena);Long start=arena.getStartTime()==null?null:arena.getStartTime().toEpochMilli();
        GameState game=new GameState(arena.getArenaName(),null,null,arena.getDisplayName(),phase,start,start==null?0:Math.max(0,(System.currentTimeMillis()-start)/1000),1,1,arena.getGroup(),teams.size(),aliveTeams,
                id(arena.getWinner()),arena.getDiamondTier(),arena.getEmeraldTier(),phase==GamePhase.RUNNING&&arena.getDiamondTier()<3?Math.max(0,arena.getUpgradeDiamondsCount()):null,
                phase==GamePhase.RUNNING&&arena.getEmeraldTier()<3?Math.max(0,arena.getUpgradeEmeraldsCount()):null,Collections.emptyMap());
        return new ArenaState(game,players,teams);
    }
    private static int level(Map<String,Integer> values,String key){Integer value=values.get(key);return value==null?0:value+1;}
}
