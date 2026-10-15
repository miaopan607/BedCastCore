package dev.bedcast.provider;
import dev.bedcast.event.EventType;
import dev.bedcast.model.Immutable;
import java.util.*;
/** 核心事件已经确认的 JSON 数据。生命周期终止使用 ended 标记。 */
public final class ProviderEvent {
    public final String arenaId;
    public final EventType type;
    public final long timestamp;
    public final Map<String,Object> data;
    public final boolean ended;
    public ProviderEvent(String arenaId, EventType type, long timestamp, Map<String,Object> data) {
        this(arenaId, type, timestamp, data, false);
    }
    private ProviderEvent(String arenaId, EventType type, long timestamp, Map<String,Object> data, boolean ended) {
        this.arenaId = arenaId; this.type = type; this.timestamp = timestamp;
        this.data = Immutable.json(data); this.ended = ended;
    }
    public static ProviderEvent ended(String arenaId, long timestamp) {
        return new ProviderEvent(arenaId, null, timestamp, Collections.emptyMap(), true);
    }
}
