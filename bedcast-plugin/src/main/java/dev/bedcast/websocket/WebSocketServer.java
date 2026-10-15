package dev.bedcast.websocket;
import dev.bedcast.snapshot.*;
import dev.bedcast.util.*;
import org.java_websocket.*;
import org.java_websocket.drafts.*;
import org.java_websocket.protocols.Protocol;
import org.java_websocket.handshake.*;
import org.java_websocket.exceptions.InvalidDataException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
public final class WebSocketServer extends org.java_websocket.server.WebSocketServer implements AutoCloseable {
    private final SnapshotStore store;
    private final TokenAuthenticator auth;
    private final PublicationDispatcher dispatcher;
    private final Consumer<Exception> errors;
    private final CountDownLatch startup=new CountDownLatch(1);
    private volatile Exception failure;
    private final Semaphore slots=new Semaphore(64);
    private final Set<WebSocket> admitted=ConcurrentHashMap.newKeySet();
    public WebSocketServer(InetSocketAddress address,SnapshotStore store,TokenAuthenticator auth,PublicationDispatcher dispatcher,Consumer<Exception> errors) {
        super(address,2,Collections.singletonList(new Draft_6455(Collections.emptyList(),Collections.singletonList(new Protocol("bedcast.v1")),4096)));
        this.store=store;this.auth=auth;this.dispatcher=dispatcher;this.errors=errors;
        setDaemon(true);setConnectionLostTimeout(30);setMaxPendingConnections(64);setReuseAddr(true);
    }
    public void startAndAwait()throws Exception {
        start();if(!startup.await(5,TimeUnit.SECONDS))throw new IllegalStateException("WebSocket 启动超时");if(failure!=null)throw failure;
    }
    @Override public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(WebSocket socket,Draft draft,ClientHandshake request)throws InvalidDataException {
        try {
            URI uri=URI.create(request.getResourceDescriptor());
            if(!"/api/v1/ws".equals(uri.getPath()))throw new IllegalArgumentException();
            String origin=request.hasFieldValue("Origin")?request.getFieldValue("Origin"):null;
            if(!auth.originAllowed(origin))throw new IllegalArgumentException();
            String arena=Query.parse(uri.getRawQuery()).get("arena");GameSnapshot snapshot=store.get(arena);
            if(snapshot==null||!snapshot.available)throw new IllegalArgumentException();
            String header=request.hasFieldValue("Authorization")?request.getFieldValue("Authorization"):null;
            String protocolToken=null;
            for(String protocol:request.getFieldValue("Sec-WebSocket-Protocol").split(",")) {
                String p=protocol.trim();if(p.startsWith("bedcast.token.")) {
                    if(protocolToken!=null)throw new IllegalArgumentException();
                    protocolToken=new String(Base64.getUrlDecoder().decode(p.substring(14)),StandardCharsets.UTF_8);
                }
            }
            String bearer=header!=null&&header.startsWith("Bearer ")?header.substring(7):null;
            if(header!=null&&bearer==null)throw new IllegalArgumentException();
            if(bearer!=null&&protocolToken!=null&&!java.security.MessageDigest.isEqual(bearer.getBytes(StandardCharsets.UTF_8),protocolToken.getBytes(StandardCharsets.UTF_8)))throw new IllegalArgumentException();
            if(auth.required() && !auth.matches(bearer!=null?bearer:protocolToken))throw new IllegalArgumentException();
            socket.setAttachment(arena);
            return super.onWebsocketHandshakeReceivedAsServer(socket,draft,request);
        }catch(IllegalArgumentException e){throw new InvalidDataException(1008,"握手未通过");}
    }
    @Override public void onOpen(WebSocket socket,ClientHandshake request) {
        if(!slots.tryAcquire()){socket.close(1013,"连接数已达上限");return;}
        admitted.add(socket);dispatcher.register(socket,socket.getAttachment());
    }
    @Override public void onClose(WebSocket socket,int code,String reason,boolean remote){dispatcher.unregister(socket);if(admitted.remove(socket))slots.release();}
    @Override public void onMessage(WebSocket socket,String message){socket.close(1008,"只读订阅不接受数据消息");}
    @Override public void onMessage(WebSocket socket,ByteBuffer message){socket.close(1008,"只读订阅不接受数据消息");}
    @Override public void onError(WebSocket socket,Exception error){if(startup.getCount()>0){failure=error;startup.countDown();}errors.accept(error);}
    @Override public void onStart(){startup.countDown();}
    @Override public void close(){try{stop(1000,"服务关闭");}catch(InterruptedException e){Thread.currentThread().interrupt();}}
}
