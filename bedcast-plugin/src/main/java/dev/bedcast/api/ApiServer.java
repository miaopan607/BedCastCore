package dev.bedcast.api;
import com.sun.net.httpserver.*;
import dev.bedcast.snapshot.SnapshotStore;
import dev.bedcast.util.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
public final class ApiServer implements AutoCloseable {
    private final HttpServer server;
    private final ThreadPoolExecutor workers;
    private final ApiRouter router;
    private final TokenAuthenticator auth;
    private final JsonCodec json;
    public ApiServer(InetSocketAddress address,SnapshotStore store,TokenAuthenticator auth,JsonCodec json)throws IOException {
        this.auth=auth;this.json=json;router=new ApiRouter(store);
        server=HttpServer.create(address,64);
        workers=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(128),r->{Thread t=new Thread(r,"BedCast-http");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
        server.setExecutor(workers);server.createContext("/",this::handle);
    }
    public void start(){server.start();}
    public int port(){return server.getAddress().getPort();}
    private void handle(HttpExchange exchange)throws IOException {
        try {
            Headers headers=exchange.getResponseHeaders();headers.set("Content-Type","application/json; charset=utf-8");headers.set("Cache-Control","no-store");
            String origin=exchange.getRequestHeaders().getFirst("Origin");
            if(!auth.originAllowed(origin)){send(exchange,403,ApiResponse.error("ORIGIN_NOT_ALLOWED","来源未允许",null));return;}
            if(origin!=null){headers.set("Access-Control-Allow-Origin",origin);headers.set("Vary","Origin");}
            if(exchange.getRequestMethod().equals("OPTIONS")) {
                headers.set("Access-Control-Allow-Methods","GET, OPTIONS");headers.set("Access-Control-Allow-Headers","Authorization");exchange.sendResponseHeaders(204,-1);return;
            }
            if(!auth.authorize(exchange.getRequestHeaders().getFirst("Authorization"))){send(exchange,401,ApiResponse.error("UNAUTHORIZED","鉴权失败",null));return;}
            if(!exchange.getRequestMethod().equals("GET")){headers.set("Allow","GET, OPTIONS");send(exchange,405,ApiResponse.error("METHOD_NOT_ALLOWED","只支持 GET",null));return;}
            ApiRouter.Result result=router.route(exchange.getRequestURI());send(exchange,result.status,result.body);
        } finally{exchange.close();}
    }
    private void send(HttpExchange exchange,int status,ApiResponse response)throws IOException {
        byte[] bytes=json.encode(response).getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,bytes.length);
        try(OutputStream output=exchange.getResponseBody()){output.write(bytes);}
    }
    @Override public void close(){server.stop(0);workers.shutdownNow();}
}
