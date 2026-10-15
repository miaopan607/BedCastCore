package dev.bedcast.websocket;
import org.java_websocket.WebSocket;
import org.java_websocket.WebSocketImpl;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
/** 实际底层发送队列的字节上限，不仅计算应用队列。 */
final class ClientSession {
    final WebSocket socket;
    final String arenaId;
    boolean ready;
    ClientSession(WebSocket socket,String arenaId){this.socket=socket;this.arenaId=arenaId;}
    void send(String text) {
        if(!socket.isOpen())return;
        long bytes=text.getBytes(StandardCharsets.UTF_8).length+14;
        for(ByteBuffer buffer:((WebSocketImpl)socket).outQueue) bytes+=buffer.remaining();
        if(bytes>1024*1024) {ready=false;socket.close(1013,"客户端读取过慢，请重新同步");return;}
        try {socket.send(text);}catch(org.java_websocket.exceptions.WebsocketNotConnectedException ignored){ready=false;}
    }
}
