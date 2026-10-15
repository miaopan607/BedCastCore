package dev.bedcast.api;
import dev.bedcast.snapshot.*;
import dev.bedcast.event.*;
import dev.bedcast.model.Immutable;
import dev.bedcast.util.Query;
import java.net.URI;
import java.util.*;
public final class ApiRouter {
    private final SnapshotStore store;
    public ApiRouter(SnapshotStore store){this.store=store;}
    public static final class Result {
        public final int status; public final ApiResponse body;
        public Result(int status,ApiResponse body){this.status=status;this.body=body;}
    }
    private static Result ok(Object data){return new Result(200,ApiResponse.ok(data));}
    private static Result error(int status,String code,String message){return new Result(status,ApiResponse.error(code,message,null));}
    public Result route(URI uri) {
        String path=uri.getPath();
        if(!path.startsWith("/api/v1/"))return error(404,"NOT_FOUND","接口不存在");
        Map<String,String> q;
        try{q=Query.parse(uri.getRawQuery());}catch(IllegalArgumentException e){return error(400,"INVALID_QUERY","查询参数无效");}
        String route=path.substring(8);
        if(route.equals("arenas")) {
            List<Object> summaries=new ArrayList<>();
            for(GameSnapshot s:store.all().values()) summaries.add(Immutable.data("arenaId",s.arenaId,"gameId",s.gameId,
                    "mapName",s.game==null?null:s.game.mapName,"gameState",s.game==null?null:s.game.gameState.name(),"available",s.available));
            return ok(summaries);
        }
        if(!(route.equals("game")||route.equals("snapshot")||route.equals("teams")||route.startsWith("teams/")||route.equals("players")||route.startsWith("players/")||route.equals("events")||route.equals("events/recent")))
            return error(404,"NOT_FOUND","接口不存在");
        String arena=q.get("arena"); if(arena==null||arena.isEmpty())return error(400,"MISSING_ARENA","必须指定 arena");
        GameSnapshot s=store.get(arena);
        if(s==null)return error(404,"ARENA_NOT_FOUND","竞技场不存在");
        if(!s.available)return error(503,"SNAPSHOT_UNAVAILABLE","竞技场采集暂不可用");
        if(route.equals("game"))return ok(s.game);
        if(route.equals("snapshot"))return ok(s);
        if(route.equals("teams"))return ok(s.teams);
        if(route.equals("players"))return ok(s.players);
        if(route.startsWith("teams/")) { Object team=s.teams.get(route.substring(6));return team==null?error(404,"TEAM_NOT_FOUND","队伍不存在"):ok(team); }
        if(route.startsWith("players/")) {
            String id=route.substring(8);
            try{if(!java.util.UUID.fromString(id).toString().equals(id))throw new IllegalArgumentException();}catch(IllegalArgumentException e){return error(400,"INVALID_UUID","玩家 UUID 无效");}
            Object player=s.players.get(id);return player==null?error(404,"PLAYER_NOT_FOUND","玩家不存在"):ok(player);
        }
        try {
            int limit=Integer.parseInt(q.getOrDefault("limit",route.equals("events/recent")?"20":"100"));
            return ok(route.equals("events/recent")?EventManager.recent(s,limit):EventManager.page(s,q.get("gameId"),Long.parseLong(q.getOrDefault("after","0")),limit));
        } catch(EventQueryException e) {
            boolean conflict=e.code.equals("GAME_CHANGED")||e.code.equals("EVENT_CURSOR_EXPIRED");
            Object data=conflict?Immutable.data("gameId",s.gameId,"oldestSequence",s.events.isEmpty()?1:s.events.get(0).sequence):null;
            return new Result(conflict?409:400,ApiResponse.error(e.code,"事件游标或查询范围无效",data));
        } catch(NumberFormatException e){return error(400,"INVALID_QUERY","查询数字无效");}
    }
}
