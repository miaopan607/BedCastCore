package dev.bedcast.config;

import dev.bedcast.state.StateManager.MatchSettings;
import org.bukkit.configuration.ConfigurationSection;
import java.net.*;
import java.util.*;

public final class BroadcastConfig {
    public final boolean apiEnabled,websocketEnabled,debug;
    public final InetSocketAddress apiAddress,websocketAddress;
    public final String token;
    public final List<String> allowedOrigins;
    public final int intervalTicks,eventCapacity;
    private final Map<String,MatchSettings> matches=new HashMap<>();
    public BroadcastConfig(ConfigurationSection config)throws UnknownHostException {
        apiEnabled=bool(config,"api.enabled");websocketEnabled=bool(config,"websocket.enabled");debug=bool(config,"debug");
        apiAddress=address(config,"api");websocketAddress=address(config,"websocket");token=string(config,"api.token");
        if(apiEnabled&&websocketEnabled&&apiAddress.getPort()==websocketAddress.getPort() &&
                (apiAddress.getAddress().equals(websocketAddress.getAddress())||apiAddress.getAddress().isAnyLocalAddress()||websocketAddress.getAddress().isAnyLocalAddress()))
            throw new IllegalArgumentException("api.port 与 websocket.port 绑定冲突");
        intervalTicks=integer(config,"snapshot.interval-ticks");if(intervalTicks<2||intervalTicks>5)throw new IllegalArgumentException("snapshot.interval-ticks 必须在 2–5");
        eventCapacity=integer(config,"events.capacity-per-arena");if(eventCapacity<1)throw new IllegalArgumentException("events.capacity-per-arena 必须大于 0");
        if(!"hmo".equals(string(config,"provider.type")))throw new IllegalArgumentException("provider.type 必须为 hmo");
        Object origins=config.get("api.allowed-origins");if(!(origins instanceof List))throw new IllegalArgumentException("api.allowed-origins 必须为列表");
        List<String> copy=new ArrayList<>();for(Object origin:(List<?>)origins){if(!(origin instanceof String))throw new IllegalArgumentException("api.allowed-origins 必须是字符串列表");copy.add((String)origin);}allowedOrigins=Collections.unmodifiableList(copy);
        ConfigurationSection section=config.getConfigurationSection("matches");
        if(section!=null)for(Map.Entry<String,Object> entry:section.getValues(false).entrySet()) {
            if(!(entry.getValue() instanceof ConfigurationSection))throw new IllegalArgumentException("matches."+entry.getKey()+" 必须为对象");
            ConfigurationSection match=(ConfigurationSection)entry.getValue();
            try {
                matches.put(entry.getKey(),new MatchSettings(match.contains("match-id")?string(match,"match-id"):null,
                        match.contains("current-round")?integer(match,"current-round"):1,
                        match.contains("total-rounds")?integer(match,"total-rounds"):1));
            } catch(IllegalArgumentException error) {
                throw new IllegalArgumentException("matches."+entry.getKey()+": "+error.getMessage(),error);
            }
        }
    }
    public MatchSettings match(String arena){return matches.getOrDefault(arena,MatchSettings.single());}
    public boolean unauthenticatedRemote(){return token.isEmpty() && ((apiEnabled&&!apiAddress.getAddress().isLoopbackAddress())||(websocketEnabled&&!websocketAddress.getAddress().isLoopbackAddress()));}
    private static InetSocketAddress address(ConfigurationSection c,String prefix)throws UnknownHostException {
        int port=integer(c,prefix+".port");if(port<1||port>65535)throw new IllegalArgumentException(prefix+".port 必须在 1–65535");
        return new InetSocketAddress(InetAddress.getByName(string(c,prefix+".host")),port);
    }
    private static int integer(ConfigurationSection c,String key){Object value=c.get(key);if(!(value instanceof Integer))throw new IllegalArgumentException(key+" 必须为整数");return (Integer)value;}
    private static boolean bool(ConfigurationSection c,String key){Object value=c.get(key);if(!(value instanceof Boolean))throw new IllegalArgumentException(key+" 必须为布尔值");return (Boolean)value;}
    private static String string(ConfigurationSection c,String key){Object value=c.get(key);if(!(value instanceof String))throw new IllegalArgumentException(key+" 必须为字符串");return (String)value;}
}
