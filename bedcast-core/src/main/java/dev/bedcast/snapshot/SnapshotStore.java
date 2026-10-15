package dev.bedcast.snapshot;
import dev.bedcast.model.Immutable;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
/** 目录只在增删竞技场时替换；每场快照独立原子发布。 */
public final class SnapshotStore {
    private final AtomicReference<Map<String,AtomicReference<GameSnapshot>>> arenas=new AtomicReference<>(Collections.emptyMap());
    public GameSnapshot get(String arenaId) {
        AtomicReference<GameSnapshot> ref=arenas.get().get(arenaId); return ref==null?null:ref.get();
    }
    public Map<String,GameSnapshot> all() {
        Map<String,GameSnapshot> result=new LinkedHashMap<>();
        arenas.get().forEach((id,ref)->result.put(id,ref.get())); return Collections.unmodifiableMap(result);
    }
    public void publish(GameSnapshot snapshot) {
        AtomicReference<GameSnapshot> ref=arenas.get().get(snapshot.arenaId);
        if (ref!=null) { ref.set(snapshot); return; }
        Map<String,AtomicReference<GameSnapshot>> copy=new LinkedHashMap<>(arenas.get());
        copy.put(snapshot.arenaId,new AtomicReference<>(snapshot)); arenas.set(Immutable.map(copy));
    }
    public void remove(String arenaId) {
        Map<String,AtomicReference<GameSnapshot>> copy=new LinkedHashMap<>(arenas.get()); copy.remove(arenaId); arenas.set(Immutable.map(copy));
    }
}
