package dev.bedcast.util;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
public final class JsonCodec {
    private final Gson gson=new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
    public String encode(Object value) { return gson.toJson(value); }
}
