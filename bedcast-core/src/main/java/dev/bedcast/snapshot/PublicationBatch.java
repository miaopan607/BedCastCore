package dev.bedcast.snapshot;
import dev.bedcast.model.Immutable;
import dev.bedcast.event.BroadcastEvent;
import java.util.*;
public final class PublicationBatch {
    public final GameSnapshot snapshot;
    public final SnapshotDiff diff;
    public final List<BroadcastEvent> events;
    public final boolean newGame;
    public PublicationBatch(GameSnapshot before, GameSnapshot snapshot, List<BroadcastEvent> events) {
        this.snapshot=snapshot; this.events=Immutable.list(events);
        this.newGame=before==null || !Objects.equals(before.gameId,snapshot.gameId) || !before.available;
        this.diff=new SnapshotDiff(newGame?null:before,snapshot);
    }
}
