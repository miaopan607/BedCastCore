package dev.bedcast.api;

import dev.bedcast.model.*;
import dev.bedcast.snapshot.*;
import dev.bedcast.util.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import dev.bedcast.provider.*;
import dev.bedcast.state.StateManager;
import dev.bedcast.event.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class RestServerTest {
    static GameSnapshot snapshot(String arena,String game) {
        GameState state=new GameState(arena,game,game,arena,GamePhase.WAITING,null,0,1,1,"solo",0,0,null,1,1,null,null,Collections.emptyMap());
        return new GameSnapshot(arena,game,1,100,0,100,state,Collections.emptyMap(),Collections.emptyMap(),Collections.emptyList(),true,null);
    }
    @Test void actualHttpEnforcesArenaAuthenticationAndOriginBoundaries()throws Exception {
        SnapshotStore store=new SnapshotStore();store.publish(snapshot("A","game-a"));store.publish(snapshot("B","game-b"));
        store.publish(snapshot("A+B/中文","game-encoded"));
        HttpClient client=HttpClient.newHttpClient();
        try(ApiServer server=new ApiServer(new InetSocketAddress("127.0.0.1",0),store,new TokenAuthenticator("secret",Collections.singletonList("http://localhost:3000")),new JsonCodec())) {
            server.start();String base="http://127.0.0.1:"+server.port()+"/api/v1/";
            assertEquals(401,get(client,base+"snapshot?arena=A","wrong",null).statusCode());
            assertEquals(400,get(client,base+"snapshot","secret",null).statusCode());
            HttpResponse<String> a=get(client,base+"snapshot?arena=A","secret","http://localhost:3000");
            assertEquals(200,a.statusCode());assertEquals("game-a",JsonParser.parseString(a.body()).getAsJsonObject().getAsJsonObject("data").get("gameId").getAsString());
            assertEquals("http://localhost:3000",a.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
            assertEquals(403,get(client,base+"snapshot?arena=A","secret","http://untrusted.example").statusCode());
            assertEquals("game-b",JsonParser.parseString(get(client,base+"snapshot?arena=B","secret",null).body()).getAsJsonObject().getAsJsonObject("data").get("gameId").getAsString());
            assertEquals("game-encoded",JsonParser.parseString(get(client,base+"snapshot?arena=A%2BB%2F%E4%B8%AD%E6%96%87","secret",null).body()).getAsJsonObject().getAsJsonObject("data").get("gameId").getAsString());
            HttpResponse<String> duplicate=get(client,base+"snapshot?arena=A&%61rena=B","secret",null);
            assertEquals(400,duplicate.statusCode());assertEquals("INVALID_QUERY",JsonParser.parseString(duplicate.body()).getAsJsonObject().get("error").getAsString());
            HttpResponse<String> options=client.send(HttpRequest.newBuilder(URI.create(base+"snapshot")).timeout(Duration.ofSeconds(3))
                    .header("Origin","http://localhost:3000").method("OPTIONS",HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(204,options.statusCode());assertEquals("GET, OPTIONS",options.headers().firstValue("Access-Control-Allow-Methods").orElse(null));
            HttpResponse<String> post=client.send(HttpRequest.newBuilder(URI.create(base+"snapshot?arena=A")).timeout(Duration.ofSeconds(3))
                    .header("Authorization","Bearer secret").POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(405,post.statusCode());assertEquals("GET, OPTIONS",post.headers().firstValue("Allow").orElse(null));
            assertEquals(404,get(client,base+"players/00000000-0000-0000-0000-000000000001?arena=A","secret",null).statusCode());
            assertEquals(400,get(client,base+"events?arena=A&after=-1","secret",null).statusCode());
            store.publish(store.get("A").unavailable());assertEquals(503,get(client,base+"game?arena=A","secret",null).statusCode());
        }
    }
    @Test @Timeout(20) void unfinishedHeadersDoNotBlockSnapshotsAndExpireWhileDataArrives()throws Exception {
        SnapshotStore store=new SnapshotStore();store.publish(snapshot("A","game-a"));
        HttpClient client=HttpClient.newHttpClient();
        ScheduledExecutorService drip=Executors.newSingleThreadScheduledExecutor();
        try(ApiServer server=new ApiServer(new InetSocketAddress("127.0.0.1",0),store,new TokenAuthenticator("secret",Collections.emptyList()),new JsonCodec())) {
            server.start();String url="http://127.0.0.1:"+server.port()+"/api/v1/snapshot?arena=A";
            try(Socket first=new Socket("127.0.0.1",server.port());Socket second=new Socket("127.0.0.1",server.port())) {
                sendPartialHeaders(first);sendPartialHeaders(second);
                // 持续补充请求头数据，验证解析期限不会被续期。
                drip.scheduleAtFixedRate(()->{
                    try{first.getOutputStream().write('x');}catch(IOException closed){/* 对端超时关闭。 */}
                },100,100,TimeUnit.MILLISECONDS);
                HttpResponse<String> response=get(client,url,"secret",null);
                assertEquals(200,response.statusCode());
                assertEquals("game-a",JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("data").get("gameId").getAsString());
                assertPeerClosed(first,15_000);assertPeerClosed(second,15_000);
                assertEquals(200,get(client,url,"secret",null).statusCode());
            }
        } finally{drip.shutdownNow();}
    }
    @Test void unfinishedRequestBodiesReceiveTheirResponseAndReleaseConnections()throws Exception {
        SnapshotStore store=new SnapshotStore();store.publish(snapshot("A","game-a"));
        try(ApiServer server=new ApiServer(new InetSocketAddress("127.0.0.1",0),store,new TokenAuthenticator("secret",Collections.emptyList()),new JsonCodec())) {
            server.start();
            try(Socket socket=new Socket("127.0.0.1",server.port())) {
                socket.setSoTimeout(3_000);
                socket.getOutputStream().write("GET /api/v1/snapshot?arena=A HTTP/1.1\r\nHost: localhost\r\nAuthorization: Bearer secret\r\nContent-Length: 1000\r\n\r\nx".getBytes(StandardCharsets.US_ASCII));
                assertEquals(200,get(HttpClient.newHttpClient(),"http://127.0.0.1:"+server.port()+"/api/v1/snapshot?arena=A","secret",null).statusCode());
                String response=new String(socket.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
                assertTrue(response.startsWith("HTTP/1.1 200 "));
                String body=response.substring(response.indexOf("\r\n\r\n")+4);
                assertEquals("game-a",JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("data").get("gameId").getAsString());
            }
        }
    }
    @Test void closingDisconnectsPendingRequestsAndAllowsRestartOnTheSamePort()throws Exception {
        SnapshotStore store=new SnapshotStore();store.publish(snapshot("A","game-a"));
        try(Socket pending=new Socket()) {
            int port;
            try(ApiServer server=new ApiServer(new InetSocketAddress("127.0.0.1",0),store,new TokenAuthenticator("secret",Collections.emptyList()),new JsonCodec())) {
                server.start();port=server.port();
                pending.connect(new InetSocketAddress("127.0.0.1",port));
                sendPartialHeaders(pending);
            }
            assertPeerClosed(pending,3_000);
            try(ApiServer replacement=new ApiServer(new InetSocketAddress("127.0.0.1",port),store,new TokenAuthenticator("secret",Collections.emptyList()),new JsonCodec())) {
                replacement.start();
                assertEquals(200,get(HttpClient.newHttpClient(),"http://127.0.0.1:"+port+"/api/v1/snapshot?arena=A","secret",null).statusCode());
            }
        }
    }
    @ParameterizedTest
    @CsvSource({
            "READY,?arena=A,200,OK", "READY,?arena=A&offset=2&mapId=map-a,200,EMPTY",
            "READY,'',400,MISSING_ARENA", "READY,?arena=unknown,404,ARENA_NOT_FOUND",
            "READY,?arena=A&offset=1,400,MISSING_MAP_ID", "READY,?arena=A&offset=-1,400,INVALID_QUERY",
            "READY,?arena=A&offset=x,400,INVALID_QUERY", "READY,?arena=A&offset=3&mapId=map-a,400,INVALID_QUERY",
            "READY,?arena=A&limit=0,400,INVALID_QUERY", "READY,?arena=A&limit=17,400,INVALID_QUERY",
            "READY,?arena=A&limit=1.5,400,INVALID_QUERY", "READY,?arena=A&offset=1&mapId=old,409,MAP_CHANGED",
            "PREPARING,?arena=A&mapId=old,409,MAP_CHANGED", "PREPARING,?arena=A,503,MAP_NOT_READY",
            "NULL,?arena=A,503,MAP_NOT_READY", "INVALID_CONFIG,?arena=A,503,MAP_CONFIG_INVALID",
            "INITIAL_STATE_MISSED,?arena=A,503,MAP_INITIAL_STATE_UNAVAILABLE", "CAPTURE_FAILED,?arena=A,503,MAP_CAPTURE_FAILED",
            "UNAVAILABLE,?arena=A,503,SNAPSHOT_UNAVAILABLE"
    })
    void mapPagesAndFailuresUseThePublishedGeneration(String status,String query,int expected,String code)throws Exception {
        int[] states=new int[4096];Arrays.fill(states,(35<<4)|14);
        ArenaMap.Bounds bounds=new ArenaMap.Bounds(-16,0,0,16,256,16),waiting=new ArenaMap.Bounds(-1,219,0,1,220,1);
        List<ArenaMap.Chunk> chunks=Arrays.asList(new ArenaMap.Chunk(-1,0,Collections.singletonList(ArenaMap.Section.encode(0,states))),new ArenaMap.Chunk(0,0,Collections.emptyList()));
        ArenaMap map=status.equals("NULL")?null:new ArenaMap(status.equals("UNAVAILABLE")?ArenaMap.Status.READY:ArenaMap.Status.valueOf(status),"map-a","world",123L,bounds,waiting,chunks);
        GameSnapshot base=snapshot("A","game-a");SnapshotStore store=new SnapshotStore();
        store.publish(new GameSnapshot("A","game-a",1,100,0,100,base.game,base.teams,base.players,base.events,!status.equals("UNAVAILABLE"),map));
        HttpClient client=HttpClient.newHttpClient();
        try(ApiServer server=new ApiServer(new InetSocketAddress("127.0.0.1",0),store,new TokenAuthenticator("secret",Collections.emptyList()),new JsonCodec())) {
            server.start();String root="http://127.0.0.1:"+server.port()+"/api/v1/";
            HttpResponse<String> response=get(client,root+"map"+query,"secret",null);assertEquals(expected,response.statusCode());
            JsonObject body=JsonParser.parseString(response.body()).getAsJsonObject();
            if(expected!=200) {
                assertEquals(code,body.get("error").getAsString());
                if(code.equals("MAP_CHANGED")) {
                    assertEquals("game-a",body.getAsJsonObject("data").get("gameId").getAsString());
                    assertEquals("map-a",body.getAsJsonObject("data").get("mapId").getAsString());
                }
                return;
            }
            JsonObject data=body.getAsJsonObject("data");assertFalse(data.has("players"));assertFalse(data.has("position"));
            assertEquals("legacy-rle-u16le-v1",data.get("encoding").getAsString());
            assertEquals(2,data.get("totalChunks").getAsInt());
            if(code.equals("EMPTY")) {
                assertEquals(0,data.getAsJsonArray("chunks").size());assertEquals(2,data.get("nextOffset").getAsInt());assertFalse(data.get("hasMore").getAsBoolean());
            } else {
                JsonObject first=JsonParser.parseString(get(client,root+"map?arena=A&limit=1","secret",null).body()).getAsJsonObject().getAsJsonObject("data");
                assertTrue(first.get("hasMore").getAsBoolean());assertEquals(1,first.get("nextOffset").getAsInt());
                JsonObject second=JsonParser.parseString(get(client,root+"map?arena=A&limit=1&offset=1&mapId="+first.get("mapId").getAsString(),"secret",null).body()).getAsJsonObject().getAsJsonObject("data");
                JsonArray rebuilt=new JsonArray();rebuilt.addAll(first.getAsJsonArray("chunks"));rebuilt.addAll(second.getAsJsonArray("chunks"));
                assertEquals(data.getAsJsonArray("chunks"),rebuilt);assertFalse(second.get("hasMore").getAsBoolean());
                assertEquals(0,second.getAsJsonArray("chunks").get(0).getAsJsonObject().getAsJsonArray("sections").size());
                JsonObject snapshot=JsonParser.parseString(get(client,root+"snapshot?arena=A","secret",null).body()).getAsJsonObject().getAsJsonObject("data");
                assertFalse(snapshot.has("map"));
            }
        }
    }
    private static final String POSITION_UUID="00000000-0000-0000-0000-000000000001";
    private static PlayerState marker(Position position,String mode) {
        return new PlayerState(POSITION_UUID,"Marker","red",!mode.equals("offline"),!mode.equals("dead"),mode.equals("spectator"),mode.equals("respawning"),
                20d,20d,0d,20,0,0,0,0,0,null,null,null,null,null,Collections.emptyMap(),null,Collections.emptyMap(),
                mode.equals("no-position")?null:position,12.5f,-3.25f,"UNKNOWN",
                Immutable.data("participant",!mode.equals("nonparticipant"),"participating",!mode.equals("left"),"qualified",true));
    }
    private static ArenaState observation(String arena,PlayerState player,GamePhase phase) {
        GameState game=new GameState(arena,null,null,arena,phase,null,0,1,1,"solo",0,0,null,1,1,null,null,Collections.emptyMap());
        return new ArenaState(game,Collections.singletonMap(player.uuid,player),Collections.emptyMap(),
                new ArenaMap(ArenaMap.Status.INVALID_CONFIG,"map-"+arena,arena,null,null,null,Collections.emptyList()));
    }
    @ParameterizedTest
    @ValueSource(strings={"active","left","offline","respawning","spectator","dead","nonparticipant","no-position","ENDING","ENDED"})
    void positionsFollowSamplesAndRemoveHistoricalMarkers(String mode)throws Exception {
        Map<String,ArenaState> observations=new LinkedHashMap<>();
        observations.put("A",observation("A",marker(new Position("world-A",-16.25,81,15.75),"active"),GamePhase.RUNNING));
        observations.put("B",observation("B",marker(new Position("world-B",100,70,100),"active"),GamePhase.RUNNING));
        BedWarsProvider provider=new BedWarsProvider() {
            public String getId(){return "memory";}
            public void start(ProviderEventSink sink){}
            public void close(){}
            public Collection<ArenaDescriptor> getArenas(){return Arrays.asList(new ArenaDescriptor("A","A",1),new ArenaDescriptor("B","B",1));}
            public Optional<ArenaState> capture(String arena){return Optional.ofNullable(observations.get(arena));}
        };
        SnapshotStore store=new SnapshotStore();
        SnapshotManager manager=new SnapshotManager(provider,store,new StateManager(20,id->StateManager.MatchSettings.single()),()->{},batch->{},(id,error)->fail(error));
        manager.collect();HttpClient client=HttpClient.newHttpClient();
        try(ApiServer server=new ApiServer(new InetSocketAddress("127.0.0.1",0),store,new TokenAuthenticator("secret",Collections.emptyList()),new JsonCodec())) {
            server.start();String root="http://127.0.0.1:"+server.port()+"/api/v1/positions?arena=";
            JsonObject first=JsonParser.parseString(get(client,root+"A","secret",null).body()).getAsJsonObject().getAsJsonObject("data");
            JsonObject b=JsonParser.parseString(get(client,root+"B","secret",null).body()).getAsJsonObject().getAsJsonObject("data");
            JsonObject position=first.getAsJsonObject("players").getAsJsonObject(POSITION_UUID).getAsJsonObject("position");
            assertEquals(-16.25,position.get("x").getAsDouble());assertEquals(81,position.get("y").getAsDouble());assertEquals(15.75,position.get("z").getAsDouble());
            assertEquals(store.get("A").timestamp,first.get("sampledAt").getAsLong());
            while(System.currentTimeMillis()<=first.get("sampledAt").getAsLong())Thread.sleep(1);
            observations.put("A",observation("A",marker(new Position("world-A",0.5,82,-0.25),"active"),GamePhase.RUNNING));
            manager.collect();
            JsonObject second=JsonParser.parseString(get(client,root+"A","secret",null).body()).getAsJsonObject().getAsJsonObject("data");
            JsonObject marker=second.getAsJsonObject("players").getAsJsonObject(POSITION_UUID);position=marker.getAsJsonObject("position");
            assertEquals(0.5,position.get("x").getAsDouble());assertEquals(82,position.get("y").getAsDouble());assertEquals(-0.25,position.get("z").getAsDouble());
            assertEquals("world-A",position.get("world").getAsString());assertEquals("red",marker.get("team").getAsString());
            assertEquals(12.5,marker.get("yaw").getAsDouble());assertEquals(-3.25,marker.get("pitch").getAsDouble());
            assertTrue(second.get("sampledAt").getAsLong()>first.get("sampledAt").getAsLong());
            assertEquals(store.get("A").timestamp,second.get("sampledAt").getAsLong());
            observations.put("A",observation("A",marker(new Position("world-A",0.5,82,-0.25),mode),
                    mode.equals("ENDING")||mode.equals("ENDED")?GamePhase.valueOf(mode):GamePhase.RUNNING));
            if(mode.equals("ENDED"))manager.accept(ProviderEvent.ended("A",System.currentTimeMillis()));
            manager.collect();
            HttpResponse<String> response=get(client,root+"A","secret",null);assertEquals(200,response.statusCode());
            JsonObject current=JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("data");
            assertEquals(mode.equals("active")?1:0,current.getAsJsonObject("players").size());
            JsonObject unchanged=JsonParser.parseString(get(client,root+"B","secret",null).body()).getAsJsonObject().getAsJsonObject("data");
            assertEquals(b.get("gameId"),unchanged.get("gameId"));assertEquals(b.getAsJsonObject("players"),unchanged.getAsJsonObject("players"));
        }
    }
    private static void sendPartialHeaders(Socket socket)throws IOException {
        socket.getOutputStream().write("GET /api/v1/arenas HTTP/1.1\r\nHost: localhost\r\nX-Probe: partial".getBytes(StandardCharsets.US_ASCII));
    }
    private static void assertPeerClosed(Socket socket,int timeoutMillis)throws IOException {
        socket.setSoTimeout(timeoutMillis);
        try{assertEquals(-1,socket.getInputStream().read(),"未完成请求的连接应被服务端关闭");}
        catch(SocketException closed){/* 对端关闭可能表现为 FIN 或 RST。 */}
    }
    private static HttpResponse<String> get(HttpClient client,String uri,String token,String origin)throws Exception {
        HttpRequest.Builder request=HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(3)).header("Authorization","Bearer "+token);
        if(origin!=null)request.header("Origin",origin);
        return client.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
}
