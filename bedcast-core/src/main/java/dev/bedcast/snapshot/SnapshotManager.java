package dev.bedcast.snapshot;
import dev.bedcast.provider.*;
import dev.bedcast.state.*;
import dev.bedcast.model.*;
import java.util.*;
import java.util.function.*;
/** 由宿主同步调度；网络发布回调必须为非阻塞提交。 */
public final class SnapshotManager implements ProviderEventSink {
    private final BedWarsProvider provider;
    private final SnapshotStore store;
    private final StateManager states;
    private final Runnable assertMainThread;
    private final Consumer<PublicationBatch> publish;
    private final BiConsumer<String,Exception> errors;
    public SnapshotManager(BedWarsProvider provider,SnapshotStore store,StateManager states,Runnable assertMainThread,
                           Consumer<PublicationBatch> publish,BiConsumer<String,Exception> errors) {
        this.provider=provider; this.store=store; this.states=states; this.assertMainThread=assertMainThread; this.publish=publish; this.errors=errors;
    }
    @Override public void accept(ProviderEvent event) { assertMainThread.run(); states.accept(event); }
    private void commit(ArenaSession session,GameSnapshot snapshot) {
        if(snapshot==null) return;
        GameSnapshot before=store.get(snapshot.arenaId); store.publish(snapshot); publish.accept(new PublicationBatch(before,snapshot,session.drain()));
    }
    public void collect() {
        assertMainThread.run(); long now=System.currentTimeMillis();
        Collection<ArenaDescriptor> descriptors;
        try { descriptors=provider.getArenas(); } catch(Exception error) {
            for(GameSnapshot snapshot:store.all().values()) fail(snapshot.arenaId,error,now); return;
        }
        Set<String> seen=new HashSet<>();
        for(ArenaDescriptor descriptor:descriptors) {
            String id=descriptor.arenaId; seen.add(id);
            try {
                Optional<ArenaState> observation=provider.capture(id);
                if(!observation.isPresent()) { finish(id,now); continue; }
                ArenaSession session=states.get(id);
                List<ProviderEvent> pending=states.take(id);
                if(session!=null && session.generation!=descriptor.generation) {
                    int boundary=-1; for(int i=0;i<pending.size();i++) if(pending.get(i).ended) { boundary=i; break; }
                    commit(session,session.finish(boundary<0?Collections.emptyList():pending.subList(0,boundary+1),now));
                    pending=boundary<0?pending:pending.subList(boundary+1,pending.size()); session=null;
                }
                if(session==null) session=states.create(descriptor,now);
                for(int i=0;i<pending.size();i++) if(pending.get(i).ended) {
                    for(int j=i+1;j<pending.size();j++) states.accept(pending.get(j));
                    pending=pending.subList(0,i+1);break;
                }
                commit(session,session.update(observation.get(),pending,now));
            } catch(Exception error) { fail(id,error,now); }
        }
        for(String id:states.ids()) if(!seen.contains(id)) finish(id,now);
    }
    private void finish(String id,long now) {
        ArenaSession session=states.get(id);
        if(session==null) return;
        if(!session.isEnded()) commit(session,session.finish(states.take(id),now));
        GameSnapshot snapshot=session.current();
        if(snapshot!=null && now-snapshot.timestamp>=600_000) { states.remove(id); store.remove(id); }
    }
    private void fail(String id,Exception error,long now) {
        GameSnapshot before=store.get(id);
        if(before!=null&&!before.available)return;
        GameSnapshot failed=before==null?new GameSnapshot(id,null,0,now,0,now,null,Collections.emptyMap(),Collections.emptyMap(),Collections.emptyList(),false,null):before.unavailable();
        store.publish(failed); publish.accept(new PublicationBatch(before,failed,Collections.emptyList())); errors.accept(id,error);
    }
}
