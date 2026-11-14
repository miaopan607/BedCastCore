package dev.bedcast.provider.hmo;

import com.andrei1058.bedwars.api.BedWars;
import com.andrei1058.bedwars.api.arena.IArena;
import dev.bedcast.model.*;
import dev.bedcast.provider.*;
import dev.bedcast.event.EventType;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.function.BooleanSupplier;

public final class HmoBedWarsProvider implements BedWarsProvider {
    static final class Context {
        final IArena arena;final long generation;
        final HmoMapCapture map;
        final Map<String,PlayerState> last=new LinkedHashMap<>();
        final Set<String> finalDeaths=new HashSet<>(),eliminated=new HashSet<>(),disconnected=new HashSet<>();
        final Map<String,Integer> generators=new HashMap<>();
        GamePhase phase;boolean terminal;
        Context(IArena arena,long generation,java.util.logging.Logger logger){this.arena=arena;this.generation=generation;phase=HmoStateMapper.phase(arena);map=new HmoMapCapture(arena,logger);}
    }
    private final JavaPlugin plugin;
    final BedWars api;
    final HmoStateMapper mapper;
    private final Map<String,Context> contexts=new LinkedHashMap<>();
    private final Map<String,ArenaState> terminalStates=new LinkedHashMap<>();
    private final Map<String,ArenaDescriptor> terminalDescriptors=new LinkedHashMap<>();
    private final List<Runnable> deferred=new ArrayList<>();
    private final Map<UUID,IArena> quitting=new HashMap<>();
    private final HmoEventListener listener;
    private ProviderEventSink sink;
    private long generation;
    private final Deque<Context> mapQueue=new ArrayDeque<>();
    private BukkitTask mapTask;
    private HmoBedWarsProvider(JavaPlugin plugin,BedWars api){this.plugin=plugin;this.api=api;mapper=new HmoStateMapper(api);listener=new HmoEventListener(this);}
    public static BedWarsProvider connect(JavaPlugin plugin) {
        org.bukkit.plugin.Plugin core=Bukkit.getPluginManager().getPlugin("HMO-Bedwars");
        if(core==null||!core.isEnabled())throw new IllegalStateException("必须先启用 HMO-Bedwars");
        BedWars api=Bukkit.getServicesManager().load(BedWars.class);
        if(api==null)throw new IllegalStateException("HMO-Bedwars API 服务尚未注册");
        return new HmoBedWarsProvider(plugin,api);
    }
    private static void mainThread(){if(!Bukkit.isPrimaryThread())throw new IllegalStateException("HMO 采集必须在主线程");}
    @Override public String getId(){return "hmo";}
    @Override public void start(ProviderEventSink sink) {
        mainThread();this.sink=sink;Bukkit.getPluginManager().registerEvents(listener,plugin);
        mapTask=Bukkit.getScheduler().runTaskTimer(plugin,this::captureMaps,1L,1L);
    }
    private void captureMaps() {
        if(mapQueue.isEmpty())return;
        long start=System.nanoTime();
        for(int advanced=0;advanced<4&&!mapQueue.isEmpty();advanced++) {
            Context c=mapQueue.removeFirst();
            if(!c.terminal&&contexts.get(c.arena.getArenaName())==c&&c.map.current().status==ArenaMap.Status.PREPARING) {
                c.map.advanceChunk();
                if(c.map.current().status==ArenaMap.Status.PREPARING)mapQueue.addLast(c);
            }
            // 区块加载不可抢占，预算仅在完整区块之间检查。
            if(System.nanoTime()-start>=2_000_000L)break;
        }
    }
    Context context(IArena arena) {
        String id=arena.getArenaName();Context current=contexts.get(id);
        if(current==null||current.arena!=arena){current=new Context(arena,++generation,plugin.getLogger());contexts.put(id,current);mapQueue.addLast(current);}return current;
    }
    void emit(IArena arena,EventType type,long timestamp,Map<String,Object> data){mainThread();context(arena);sink.accept(new ProviderEvent(arena.getArenaName(),type,timestamp,data));}
    void defer(BooleanSupplier confirmed,Runnable action){deferred.add(()->{if(confirmed.getAsBoolean())action.run();});}
    void remember(Player player,IArena arena) {
        Context c=context(arena);PlayerState previous=c.last.get(player.getUniqueId().toString());
        com.andrei1058.bedwars.api.arena.stats.PlayerGameStats stats=arena.getStatsHolder()==null?null:arena.getStatsHolder().get(player.getUniqueId()).orElse(null);
        PlayerState value=mapper.player(arena,player.getUniqueId(),stats,previous,c.finalDeaths);
        Map<String,Object> ext=new LinkedHashMap<>(value.extensions);ext.put("lastObservedAt",System.currentTimeMillis());
        c.last.put(value.uuid,value.withPresence(value.online,value.alive,value.spectator,value.respawning,ext));
    }
    void quitBegin(Player player){IArena arena=api.getArenaUtil().getArenaByPlayer(player);if(arena!=null)quitting.put(player.getUniqueId(),arena);}
    void quitEnd(Player player) {
        IArena arena=quitting.remove(player.getUniqueId());if(arena==null)return;
        if(context(arena).disconnected.add(player.getUniqueId().toString()))emit(arena,EventType.PLAYER_DISCONNECT,System.currentTimeMillis(),
                Immutable.data("uuid",player.getUniqueId().toString(),"teamId",HmoStateMapper.id(mapper.team(arena,null,player.getUniqueId()))));
    }
    void terminate(String arenaId) {
        Context c=contexts.get(arenaId);
        if(c!=null&&!c.terminal) {
            c.map.stopBeforeRunning();
            terminalStates.put(arenaId,mapper.capture(c));
            terminalDescriptors.put(arenaId,new ArenaDescriptor(arenaId,c.arena.getDisplayName(),c.generation));
            c.terminal=true;sink.accept(ProviderEvent.ended(arenaId,System.currentTimeMillis()));
        }
    }
    @Override public Collection<ArenaDescriptor> getArenas() {
        mainThread();if(!Bukkit.getPluginManager().isPluginEnabled("HMO-Bedwars"))throw new IllegalStateException("HMO-Bedwars 已停用");
        if(!deferred.isEmpty()){List<Runnable> tasks=new ArrayList<>(deferred);deferred.clear();for(Runnable task:tasks)task.run();}
        List<ArenaDescriptor> result=new ArrayList<>(terminalDescriptors.values());Set<String> live=new HashSet<>();
        for(IArena arena:api.getArenaUtil().getArenas()) {
            Context c=context(arena);live.add(arena.getArenaName());
            if(!c.terminal)result.add(new ArenaDescriptor(arena.getArenaName(),arena.getDisplayName(),c.generation));
        }
        contexts.keySet().retainAll(live);return result;
    }
    @Override public Optional<ArenaState> capture(String arenaId) {
        mainThread();
        ArenaState terminal=terminalStates.remove(arenaId);
        if(terminal!=null){terminalDescriptors.remove(arenaId);return Optional.of(terminal);}
        Context c=contexts.get(arenaId);if(c==null||c.terminal)return Optional.empty();
        ArenaState state=mapper.capture(c);
        for(TeamState team:state.teams.values())if(team.eliminated && c.eliminated.add(team.teamId))emit(c.arena,EventType.TEAM_ELIMINATED,System.currentTimeMillis(),Immutable.data("teamId",team.teamId));
        return Optional.of(state);
    }
    @Override public void close(){mainThread();if(mapTask!=null){mapTask.cancel();mapTask=null;}HandlerList.unregisterAll(listener);mapQueue.clear();deferred.clear();quitting.clear();contexts.clear();terminalStates.clear();terminalDescriptors.clear();sink=null;}
}
