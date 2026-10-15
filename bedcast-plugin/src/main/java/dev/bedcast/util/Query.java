package dev.bedcast.util;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
public final class Query {
    private Query() { }
    public static Map<String,String> parse(String raw) {
        Map<String,String> result=new HashMap<>(); if(raw==null || raw.isEmpty())return result;
        for(String part:raw.split("&")) { String[] entry=part.split("=",2);
            String key=decode(entry[0]), value=entry.length==1?"":decode(entry[1]);
            if(result.put(key,value)!=null)throw new IllegalArgumentException("重复 query 键"); }
        return result;
    }
    public static String decode(String value) { return URLDecoder.decode(value,StandardCharsets.UTF_8); }
}
