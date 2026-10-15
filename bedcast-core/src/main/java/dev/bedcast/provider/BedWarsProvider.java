package dev.bedcast.provider;
import java.util.*;
/** 所有方法都由服务器主线程调用；实现不得向外泄露核心对象。 */
public interface BedWarsProvider extends AutoCloseable {
    String getId();
    Collection<ArenaDescriptor> getArenas();
    Optional<ArenaState> capture(String arenaId);
    void start(ProviderEventSink sink);
    @Override void close();
}
