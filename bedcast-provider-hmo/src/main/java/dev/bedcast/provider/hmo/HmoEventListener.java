package dev.bedcast.provider.hmo;

import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.arena.team.ITeam;
import com.andrei1058.bedwars.api.events.gameplay.*;
import com.andrei1058.bedwars.api.events.player.*;
import com.andrei1058.bedwars.api.events.server.*;
import com.andrei1058.bedwars.api.events.shop.ShopBuyEvent;
import com.andrei1058.bedwars.api.events.upgrades.UpgradeBuyEvent;
import com.andrei1058.bedwars.api.events.team.TeamEliminatedEvent;
import dev.bedcast.event.EventType;
import dev.bedcast.model.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.*;
import static dev.bedcast.model.Immutable.data;

final class HmoEventListener implements Listener {
    private final HmoBedWarsProvider provider;
    HmoEventListener(HmoBedWarsProvider provider){this.provider=provider;}
    private static String uuid(Player p){return p==null?null:p.getUniqueId().toString();}
    private static String team(ITeam team){return HmoStateMapper.id(team);}
    private Map<String,Object> player(IArena a,Player p){return data("uuid",uuid(p),"teamId",team(provider.mapper.team(a,p,p.getUniqueId())));}
    @EventHandler(priority=EventPriority.MONITOR)
    public void state(GameStateChangeEvent e) {
        HmoBedWarsProvider.Context c=provider.context(e.getArena());GamePhase phase=HmoStateMapper.phase(e.getArena());
        if(c.phase!=GamePhase.RUNNING && phase==GamePhase.RUNNING)provider.emit(e.getArena(),EventType.GAME_START,System.currentTimeMillis(),Collections.emptyMap());c.phase=phase;
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void end(GameEndEvent e){provider.emit(e.getArena(),EventType.GAME_END,System.currentTimeMillis(),data("winnerTeamId",team(e.getTeamWinner()),"reason","VICTORY"));}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void join(PlayerJoinArenaEvent e) {
        long now=System.currentTimeMillis();Map<String,Object> payload=player(e.getArena(),e.getPlayer());
        provider.defer(()->!e.isCancelled()&&(e.getArena().isPlayer(e.getPlayer())||e.getArena().isSpectator(e.getPlayer())),
                ()->provider.emit(e.getArena(),EventType.PLAYER_JOIN,now,payload));
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void leave(PlayerLeaveArenaEvent e){provider.remember(e.getPlayer(),e.getArena());provider.emit(e.getArena(),EventType.PLAYER_LEAVE,System.currentTimeMillis(),player(e.getArena(),e.getPlayer()));}
    @EventHandler(priority=EventPriority.LOWEST)
    public void quitStart(PlayerQuitEvent e){provider.quitBegin(e.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR)
    public void quitEnd(PlayerQuitEvent e){provider.quitEnd(e.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void reconnect(PlayerReJoinEvent e) {
        long now=System.currentTimeMillis();Map<String,Object> payload=player(e.getArena(),e.getPlayer());
        provider.defer(()->!e.isCancelled()&&e.getArena().isPlayer(e.getPlayer()),()->{
            provider.context(e.getArena()).disconnected.remove(uuid(e.getPlayer()));provider.emit(e.getArena(),EventType.PLAYER_RECONNECT,now,payload);
        });
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void respawn(PlayerReSpawnEvent e){provider.emit(e.getArena(),EventType.PLAYER_RESPAWN,System.currentTimeMillis(),player(e.getArena(),e.getPlayer()));}
    @EventHandler(priority=EventPriority.MONITOR)
    public void kill(PlayerKillEvent e) {
        if(e.getCause().isFinalKill())provider.context(e.getArena()).finalDeaths.add(uuid(e.getVictim()));
        provider.emit(e.getArena(),EventType.PLAYER_DEATH,System.currentTimeMillis(),data("victimUuid",uuid(e.getVictim()),"killerUuid",uuid(e.getKiller()),
                "victimTeamId",team(e.getVictimTeam()),"killerTeamId",team(e.getKillerTeam()),"cause",e.getCause().name(),"final",e.getCause().isFinalKill()));
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void bed(PlayerBedBreakEvent e){provider.emit(e.getArena(),EventType.BED_DESTROYED,System.currentTimeMillis(),data("playerUuid",uuid(e.getPlayer()),"teamId",team(e.getPlayerTeam()),"victimTeamId",team(e.getVictimTeam())));}
    @EventHandler(priority=EventPriority.MONITOR)
    public void eliminated(TeamEliminatedEvent e){if(provider.context(e.getArena()).eliminated.add(team(e.getTeam())))provider.emit(e.getArena(),EventType.TEAM_ELIMINATED,System.currentTimeMillis(),data("teamId",team(e.getTeam())));}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void purchase(ShopBuyEvent e) {
        if(e.getArena()==null)return;long now=System.currentTimeMillis();Map<String,Object> payload=data("playerUuid",uuid(e.getBuyer()),"itemId",e.getCategoryContent().getIdentifier());
        provider.defer(()->!e.isCancelled(),()->provider.emit(e.getArena(),EventType.ITEM_PURCHASE,now,payload));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void upgrade(UpgradeBuyEvent e) {
        long now=System.currentTimeMillis();Map<String,Object> payload=data("playerUuid",uuid(e.getPlayer()),"teamId",team(e.getTeam()),"upgradeId",e.getTeamUpgrade().getName());
        provider.defer(()->!e.isCancelled(),()->provider.emit(e.getArena(),EventType.TEAM_UPGRADE,now,payload));
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void generator(GeneratorUpgradeEvent e) {
        IArena a=e.getGenerator().getArena();String type=e.getGenerator().getType().name();
        if(e.getGenerator().getBwt()!=null||!(type.equals("DIAMOND")||type.equals("EMERALD")))return;
        int level=type.equals("DIAMOND")?a.getDiamondTier():a.getEmeraldTier();
        Integer old=provider.context(a).generators.put(type,level);
        if(!Objects.equals(old,level))provider.emit(a,EventType.GENERATOR_UPGRADE,System.currentTimeMillis(),data("generatorType",type,"level",level));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void damage(EntityDamageEvent e) {
        if(!(e.getEntity() instanceof Player)||e.getFinalDamage()<=0)return;
        Player victim=(Player)e.getEntity();IArena arena=provider.api.getArenaUtil().getArenaByPlayer(victim);
        if(arena==null||!arena.isPlayer(victim)||HmoStateMapper.phase(arena)!=GamePhase.RUNNING)return;
        Entity source=e instanceof EntityDamageByEntityEvent?((EntityDamageByEntityEvent)e).getDamager():null;
        Object attacker=source instanceof Projectile?((Projectile)source).getShooter():source;
        provider.emit(arena,EventType.PLAYER_DAMAGE,System.currentTimeMillis(),data("victimUuid",uuid(victim),"attackerUuid",attacker instanceof Player?uuid((Player)attacker):null,
                "cause",e.getCause().name(),"damage",e.getFinalDamage()));
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void restart(ArenaRestartEvent e){provider.terminate(e.getArenaName());}
    @EventHandler(priority=EventPriority.MONITOR)
    public void disable(ArenaDisableEvent e){provider.terminate(e.getArenaName());}
}
