package dev.bedcast.api;

import dev.bedcast.model.*;
import dev.bedcast.snapshot.*;
import dev.bedcast.util.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.net.http.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RestServerTest {
    static GameSnapshot snapshot(String arena,String game) {
        GameState state=new GameState(arena,game,game,arena,GamePhase.WAITING,null,0,1,1,"solo",0,0,null,1,1,null,null,Collections.emptyMap());
        return new GameSnapshot(arena,game,1,100,0,100,state,Collections.emptyMap(),Collections.emptyMap(),Collections.emptyList(),true);
    }
    @Test void actualHttpEnforcesArenaAuthenticationAndOriginBoundaries()throws Exception {
        SnapshotStore store=new SnapshotStore();store.publish(snapshot("A","game-a"));store.publish(snapshot("B","game-b"));
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
            assertEquals(404,get(client,base+"players/00000000-0000-0000-0000-000000000001?arena=A","secret",null).statusCode());
            assertEquals(400,get(client,base+"events?arena=A&after=-1","secret",null).statusCode());
            store.publish(store.get("A").unavailable());assertEquals(503,get(client,base+"game?arena=A","secret",null).statusCode());
        }
    }
    private static HttpResponse<String> get(HttpClient client,String uri,String token,String origin)throws Exception {
        HttpRequest.Builder request=HttpRequest.newBuilder(URI.create(uri)).header("Authorization","Bearer "+token);
        if(origin!=null)request.header("Origin",origin);
        return client.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
}
