package dev.bedcast.api;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.RequestLimit;
import io.undertow.server.handlers.RequestLimitingHandler;
import io.undertow.util.*;
import dev.bedcast.snapshot.SnapshotStore;
import dev.bedcast.util.*;
import java.nio.ByteBuffer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.xnio.Options;
public final class ApiServer implements AutoCloseable {
    private static final int NETWORK_TIMEOUT_MILLIS=10_000;
    private static final HttpString ALLOW_ORIGIN=new HttpString("Access-Control-Allow-Origin");
    private static final HttpString ALLOW_METHODS=new HttpString("Access-Control-Allow-Methods");
    private static final HttpString ALLOW_HEADERS=new HttpString("Access-Control-Allow-Headers");
    private final Undertow server;
    private final ApiRouter router;
    private final TokenAuthenticator auth;
    private final JsonCodec json;
    private final HttpHandler handler=this::handle;
    public ApiServer(InetSocketAddress address,SnapshotStore store,TokenAuthenticator auth,JsonCodec json) {
        this.auth=auth;this.json=json;router=new ApiRouter(store);
        RequestLimit limit=new RequestLimit(16,128);
        limit.setFailureHandler(exchange->{if(prepare(exchange))send(exchange,503,ApiResponse.error("SERVER_BUSY","HTTP 请求过多",null));});
        server=Undertow.builder()
                .addHttpListener(address.getPort(),address.getAddress().getHostAddress())
                .setIoThreads(2).setWorkerThreads(4)
                .setWorkerOption(Options.WORKER_NAME,"BedCast-http")
                .setWorkerOption(Options.THREAD_DAEMON,true)
                .setSocketOption(Options.BACKLOG,64)
                .setSocketOption(Options.CONNECTION_HIGH_WATER,256)
                .setSocketOption(Options.CONNECTION_LOW_WATER,192)
                .setSocketOption(Options.READ_TIMEOUT,NETWORK_TIMEOUT_MILLIS)
                .setSocketOption(Options.WRITE_TIMEOUT,NETWORK_TIMEOUT_MILLIS)
                .setServerOption(UndertowOptions.REQUEST_PARSE_TIMEOUT,NETWORK_TIMEOUT_MILLIS)
                .setServerOption(UndertowOptions.NO_REQUEST_TIMEOUT,30_000)
                .setServerOption(UndertowOptions.IDLE_TIMEOUT,30_000)
                .setServerOption(UndertowOptions.DECODE_URL,false)
                .setServerOption(UndertowOptions.SHUTDOWN_TIMEOUT,1_000)
                .setHandler(new RequestLimitingHandler(limit,handler)).build();
    }
    public void start(){server.start();}
    public int port(){return ((InetSocketAddress)server.getListenerInfo().get(0).getAddress()).getPort();}
    private void handle(HttpServerExchange exchange) {
        // 快照路由和编码交给工作线程。
        if(exchange.isInIoThread()){exchange.dispatch(handler);return;}
        if(!prepare(exchange))return;
        String query=exchange.getQueryString();URI uri;
        try{uri=URI.create(exchange.getRequestURI()+(query.isEmpty()?"":"?"+query));}
        catch(IllegalArgumentException error){send(exchange,400,ApiResponse.error("INVALID_QUERY","查询参数无效",null));return;}
        ApiRouter.Result result=router.route(uri);send(exchange,result.status,result.body);
    }
    private boolean prepare(HttpServerExchange exchange) {
        HeaderMap headers=exchange.getResponseHeaders();headers.put(Headers.CONTENT_TYPE,"application/json; charset=utf-8");headers.put(Headers.CACHE_CONTROL,"no-store");
        // 只读接口不消费请求体，未读完的连接在响应后关闭。
        if(!exchange.isRequestComplete())exchange.setPersistent(false);
        String origin=exchange.getRequestHeaders().getFirst(Headers.ORIGIN);
        if(!auth.originAllowed(origin)){send(exchange,403,ApiResponse.error("ORIGIN_NOT_ALLOWED","来源未允许",null));return false;}
        if(origin!=null){headers.put(ALLOW_ORIGIN,origin);headers.put(Headers.VARY,"Origin");}
        if(Methods.OPTIONS.equals(exchange.getRequestMethod())) {
            headers.put(ALLOW_METHODS,"GET, OPTIONS");headers.put(ALLOW_HEADERS,"Authorization");exchange.setStatusCode(204);exchange.endExchange();return false;
        }
        if(!auth.authorize(exchange.getRequestHeaders().getFirst(Headers.AUTHORIZATION))){send(exchange,401,ApiResponse.error("UNAUTHORIZED","鉴权失败",null));return false;}
        if(!Methods.GET.equals(exchange.getRequestMethod())){headers.put(Headers.ALLOW,"GET, OPTIONS");send(exchange,405,ApiResponse.error("METHOD_NOT_ALLOWED","只支持 GET",null));return false;}
        return true;
    }
    private void send(HttpServerExchange exchange,int status,ApiResponse response) {
        byte[] bytes=json.encode(response).getBytes(StandardCharsets.UTF_8);exchange.setStatusCode(status);
        exchange.getResponseSender().send(ByteBuffer.wrap(bytes));
    }
    @Override public void close(){server.stop();}
}
