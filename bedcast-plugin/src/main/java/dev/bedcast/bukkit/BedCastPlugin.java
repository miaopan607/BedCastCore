package dev.bedcast.bukkit;

import dev.bedcast.api.ApiServer;
import dev.bedcast.config.BroadcastConfig;
import dev.bedcast.provider.BedWarsProvider;
import dev.bedcast.provider.hmo.HmoBedWarsProvider;
import dev.bedcast.snapshot.*;
import dev.bedcast.state.StateManager;
import dev.bedcast.util.*;
import dev.bedcast.websocket.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.logging.*;

public final class BedCastPlugin extends JavaPlugin {
    private BedWarsProvider provider;
    private ApiServer api;
    private WebSocketServer websocket;
    private PublicationDispatcher dispatcher;
    private BukkitTask sampling;
    @Override public void onEnable() {
        Logger logger=getLogger();
        try {
            saveDefaultConfig();BroadcastConfig config=new BroadcastConfig(getConfig());
            if(config.unauthenticatedRemote())logger.warning("非回环地址未配置 Token，赛事数据可被网络中的其他主机读取");
            SnapshotStore store=new SnapshotStore();provider=HmoBedWarsProvider.connect(this);
            SnapshotManager manager=new SnapshotManager(provider,store,new StateManager(config.eventCapacity,config::match),
                    ()->{if(!Bukkit.isPrimaryThread())throw new IllegalStateException("快照采集必须在 Bukkit 主线程");},
                    batch->{if(dispatcher!=null)dispatcher.accept(batch);},
                    (arena,error)->{if(config.debug)logger.log(Level.WARNING,"竞技场采集失败: "+arena,error);else logger.warning("竞技场采集失败: "+arena+" / "+error.getMessage());});
            provider.start(manager);manager.collect();
            JsonCodec json=new JsonCodec();TokenAuthenticator auth=new TokenAuthenticator(config.token,config.allowedOrigins);
            if(config.apiEnabled){api=new ApiServer(config.apiAddress,store,auth,json);api.start();logger.info("REST 已监听 "+config.apiAddress);}
            if(config.websocketEnabled){dispatcher=new PublicationDispatcher(store,json);websocket=new WebSocketServer(config.websocketAddress,store,auth,dispatcher,
                    error->logger.log(Level.WARNING,"WebSocket 网络错误",error));websocket.startAndAwait();logger.info("WebSocket 已监听 "+config.websocketAddress);}
            sampling=Bukkit.getScheduler().runTaskTimer(this,manager::collect,1,config.intervalTicks);
        } catch(Exception|LinkageError error) {
            logger.log(Level.SEVERE,"BedCast 启动失败",error);Bukkit.getPluginManager().disablePlugin(this);
        }
    }
    @Override public void onDisable() {
        if(api!=null){api.close();api=null;}
        if(websocket!=null){websocket.close();websocket=null;}
        if(sampling!=null){sampling.cancel();sampling=null;}
        if(provider!=null){provider.close();provider=null;}
        if(dispatcher!=null){dispatcher.close();dispatcher=null;}
    }
}
