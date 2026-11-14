package dev.bedcast.websocket;

import com.google.gson.*;
import dev.bedcast.model.*;
import dev.bedcast.event.*;
import dev.bedcast.snapshot.*;
import dev.bedcast.util.*;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class WebSocketNetworkTest {
    private static GameSnapshot snapshot(String arena,long revision,List<BroadcastEvent> events) {
        GameState game=new GameState(arena,"game-"+arena,"match",arena,GamePhase.RUNNING,0L,revision,1,1,"solo",0,0,null,1,1,10,10,Collections.emptyMap());
        return new GameSnapshot(arena,game.gameId,revision,System.currentTimeMillis(),events.size(),0,game,Collections.emptyMap(),Collections.emptyMap(),events,true,null);
    }
    static final class Listener implements WebSocket.Listener {
        final BlockingQueue<JsonObject> messages=new LinkedBlockingQueue<>();final StringBuilder frame=new StringBuilder();
        @Override public void onOpen(WebSocket socket){socket.request(1);}
        @Override public CompletionStage<?> onText(WebSocket socket,CharSequence text,boolean last){frame.append(text);if(last){messages.add(JsonParser.parseString(frame.toString()).getAsJsonObject());frame.setLength(0);}socket.request(1);return null;}
        JsonObject next()throws Exception{JsonObject value=messages.poll(5,TimeUnit.SECONDS);assertNotNull(value,"缺少 WebSocket 消息");return value;}
    }
    @Test void realClientsAuthenticateReceiveSnapshotFirstAndOnlySubscribedArenaEvents()throws Exception {
        SnapshotStore store=new SnapshotStore();store.publish(snapshot("A",1,Collections.emptyList()));store.publish(snapshot("B",1,Collections.emptyList()));
        try(PublicationDispatcher dispatcher=new PublicationDispatcher(store,new JsonCodec());
            WebSocketServer server=new WebSocketServer(new InetSocketAddress("127.0.0.1",0),store,new TokenAuthenticator("secret",Collections.emptyList()),dispatcher,e->{})) {
            server.startAndAwait();String url="ws://127.0.0.1:"+server.getPort()+"/api/v1/ws?arena=";
            HttpClient client=HttpClient.newHttpClient();Listener a=new Listener(),b=new Listener();
            String token=Base64.getUrlEncoder().withoutPadding().encodeToString("secret".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            WebSocket socketA=client.newWebSocketBuilder().subprotocols("bedcast.v1","bedcast.token."+token).buildAsync(URI.create(url+"A"),a).get(5,TimeUnit.SECONDS);
            WebSocket socketB=client.newWebSocketBuilder().subprotocols("bedcast.v1").header("Authorization","Bearer secret").buildAsync(URI.create(url+"B"),b).get(5,TimeUnit.SECONDS);
            assertEquals("bedcast.v1",socketA.getSubprotocol());JsonObject initial=a.next();assertEquals("SNAPSHOT",initial.get("type").getAsString());b.next();
            EventManager events=new EventManager("A","game-A",20);
            BroadcastEvent death=events.append(EventType.PLAYER_DEATH,10,1,Immutable.data("victimUuid","B","killerUuid","A","final",true));
            BroadcastEvent kill=events.append(EventType.PLAYER_KILL,10,1,Immutable.data("deathId",death.eventId));
            BroadcastEvent finalKill=events.append(EventType.PLAYER_FINAL_KILL,10,1,Immutable.data("deathId",death.eventId));
            GameSnapshot before=store.get("A"), after=snapshot("A",2,events.history());store.publish(after);dispatcher.accept(new PublicationBatch(before,after,events.history()));
            long last=initial.get("sequence").getAsLong();
            for(String type:Arrays.asList("PLAYER_DEATH","PLAYER_KILL","PLAYER_FINAL_KILL","GAME_STATE_UPDATE")) {
                JsonObject message=a.next();assertEquals(type,message.get("type").getAsString());assertTrue(message.get("sequence").getAsLong()>last);last=message.get("sequence").getAsLong();
            }
            assertNull(b.messages.poll(200,TimeUnit.MILLISECONDS));
            assertThrows(ExecutionException.class,()->client.newWebSocketBuilder().subprotocols("bedcast.v1").header("Authorization","Bearer wrong").buildAsync(URI.create(url+"A"),new Listener()).get(5,TimeUnit.SECONDS));
            socketA.sendClose(WebSocket.NORMAL_CLOSURE,"done").get(5,TimeUnit.SECONDS);socketB.sendClose(WebSocket.NORMAL_CLOSURE,"done").get(5,TimeUnit.SECONDS);
        }
    }
    @Test void initialSnapshotUsesDispatcherVersionAndCannotSkipQueuedEvents()throws Exception {
        SnapshotStore store=new SnapshotStore();GameSnapshot first=snapshot("A",1,Collections.emptyList());store.publish(first);
        try(PublicationDispatcher dispatcher=new PublicationDispatcher(store,new JsonCodec());
            WebSocketServer server=new WebSocketServer(new InetSocketAddress("127.0.0.1",0),store,new TokenAuthenticator("",Collections.emptyList()),dispatcher,e->{})) {
            EventManager events=new EventManager("A","game-A",20);
            events.append(EventType.ITEM_PURCHASE,10,1,Immutable.data("itemId","wool"));
            GameSnapshot second=snapshot("A",2,events.history());
            events.append(EventType.TEAM_UPGRADE,11,1,Immutable.data("upgradeId","sharpness"));
            GameSnapshot third=snapshot("A",3,events.history());store.publish(third);
            server.startAndAwait();Listener listener=new Listener();
            WebSocket socket=HttpClient.newHttpClient().newWebSocketBuilder().subprotocols("bedcast.v1")
                    .buildAsync(URI.create("ws://127.0.0.1:"+server.getPort()+"/api/v1/ws?arena=A"),listener).get(5,TimeUnit.SECONDS);
            JsonObject initial=listener.next();
            assertEquals("SNAPSHOT",initial.get("type").getAsString());
            assertEquals(1,initial.getAsJsonObject("data").get("revision").getAsLong());
            dispatcher.accept(new PublicationBatch(first,second,second.events));
            dispatcher.accept(new PublicationBatch(second,third,Collections.singletonList(third.events.get(1))));
            List<String> received=new ArrayList<>();long sequence=initial.get("sequence").getAsLong();
            for(int i=0;i<4;i++) {
                JsonObject message=listener.next();assertTrue(message.get("sequence").getAsLong()>sequence);sequence=message.get("sequence").getAsLong();
                received.add(message.get("type").getAsString());
            }
            assertEquals(Arrays.asList("ITEM_PURCHASE","GAME_STATE_UPDATE","TEAM_UPGRADE","GAME_STATE_UPDATE"),received);
            socket.sendClose(WebSocket.NORMAL_CLOSURE,"done").get(5,TimeUnit.SECONDS);
        }
    }
}
