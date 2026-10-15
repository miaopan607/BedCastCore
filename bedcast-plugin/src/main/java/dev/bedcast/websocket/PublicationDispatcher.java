package dev.bedcast.websocket;
import dev.bedcast.snapshot.*;
import dev.bedcast.event.BroadcastEvent;
import dev.bedcast.model.Immutable;
import dev.bedcast.util.JsonCodec;
import org.java_websocket.WebSocket;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
/** 同一个线程排序初始快照与后续事件，Minecraft 主线程只做 offer。 */
public final class PublicationDispatcher implements Consumer<PublicationBatch>,AutoCloseable {
    private final SnapshotStore store;
    private final JsonCodec json;
    private final ArrayBlockingQueue<Runnable> work=new ArrayBlockingQueue<>(256);
    private final Map<String,GameSnapshot> latest=new HashMap<>();
    private final Map<String,Long> sequence=new HashMap<>();
    private final ConcurrentMap<WebSocket,ClientSession> sessions=new ConcurrentHashMap<>();
    private final AtomicBoolean overflow=new AtomicBoolean();
    private final AtomicLong epoch=new AtomicLong();
    private volatile boolean running=true;
    private final Thread thread;
    public PublicationDispatcher(SnapshotStore store,JsonCodec json) {
        this.store=store;this.json=json;latest.putAll(store.all());
        thread=new Thread(this::run,"BedCast-publications");thread.setDaemon(true);thread.start();
    }
    private void offer(Runnable action) { if(!running)return; if(!work.offer(action)) {epoch.incrementAndGet();overflow.set(true);} }
    @Override public void accept(PublicationBatch batch) {long version=epoch.get();offer(()->{if(version==epoch.get())dispatch(batch);});}
    public void register(WebSocket socket,String arenaId) {
        ClientSession session=new ClientSession(socket,arenaId);sessions.put(socket,session);
        offer(()->{if(sessions.get(socket)!=session)return;
            GameSnapshot snapshot=latest.get(arenaId);
            if(snapshot==null || !snapshot.available){socket.close(1011,"赛事采集不可用");return;}
            session.send(message("SNAPSHOT",snapshot,snapshot));session.ready=socket.isOpen()&&!socket.isClosing();
        });
    }
    public void unregister(WebSocket socket){sessions.remove(socket);}
    private String message(String type,GameSnapshot snapshot,Object payload) {
        long next=sequence.merge(snapshot.arenaId,1L,Long::sum);
        return json.encode(Immutable.data("type",type,"arenaId",snapshot.arenaId,"gameId",snapshot.gameId,"sequence",next,
                "timestamp",snapshot.timestamp,"gameTime",snapshot.game==null?0:snapshot.game.elapsedTime,"data",payload));
    }
    private void broadcast(String arenaId,String message) {
        for(ClientSession session:sessions.values())if(session.ready&&session.arenaId.equals(arenaId))session.send(message);
    }
    private void dispatch(PublicationBatch batch) {
        GameSnapshot s=batch.snapshot;
        GameSnapshot before=latest.get(s.arenaId);
        if(before!=null && Objects.equals(before.gameId,s.gameId) && s.revision<before.revision)return;
        latest.put(s.arenaId,s);
        if(!s.available) {for(ClientSession session:sessions.values())if(session.arenaId.equals(s.arenaId)){session.ready=false;session.socket.close(1011,"赛事采集不可用");}return;}
        if(batch.newGame) {broadcast(s.arenaId,message("SNAPSHOT",s,s));return;}
        for(BroadcastEvent event:batch.events) {
            Map<String,Object> payload=new LinkedHashMap<>(event.data);payload.put("eventId",event.eventId);payload.put("eventSequence",event.sequence);
            // 语义事件保留实际发生时刻，消息序号与 REST 事件游标独立。
            long next=sequence.merge(s.arenaId,1L,Long::sum);
            broadcast(s.arenaId,json.encode(Immutable.data("type",event.eventType.name(),"arenaId",s.arenaId,"gameId",s.gameId,"sequence",next,
                    "timestamp",event.timestamp,"gameTime",event.gameTime,"data",payload)));
        }
        if(batch.diff.gameChanged)broadcast(s.arenaId,message("GAME_STATE_UPDATE",s,s.game));
        batch.diff.players.forEach((id,value)->broadcast(s.arenaId,message("PLAYER_STATE_UPDATE",s,value)));
        batch.diff.teams.forEach((id,value)->broadcast(s.arenaId,message("TEAM_STATE_UPDATE",s,value)));
        for(String id:batch.diff.removedPlayers)broadcast(s.arenaId,message("PLAYER_STATE_UPDATE",s,Immutable.data("id",id,"removed",true)));
        for(String id:batch.diff.removedTeams)broadcast(s.arenaId,message("TEAM_STATE_UPDATE",s,Immutable.data("id",id,"removed",true)));
    }
    private void run() {
        try {
            while(running) {
                if(overflow.getAndSet(false)) {
                    for(ClientSession session:sessions.values())session.socket.close(1013,"推送拥塞，请重新同步");
                    sessions.clear();work.clear();latest.clear();latest.putAll(store.all());
                }
                Runnable action=work.poll(100,TimeUnit.MILLISECONDS);if(action!=null)action.run();
            }
        } catch(InterruptedException e){Thread.currentThread().interrupt();}
        finally{for(ClientSession session:sessions.values())session.socket.close(1001,"服务关闭");sessions.clear();}
    }
    @Override public void close(){running=false;thread.interrupt();}
}
