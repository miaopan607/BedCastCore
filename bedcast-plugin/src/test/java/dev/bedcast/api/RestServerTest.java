package dev.bedcast.api;

import dev.bedcast.model.*;
import dev.bedcast.snapshot.*;
import dev.bedcast.util.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
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
        return new GameSnapshot(arena,game,1,100,0,100,state,Collections.emptyMap(),Collections.emptyMap(),Collections.emptyList(),true);
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
