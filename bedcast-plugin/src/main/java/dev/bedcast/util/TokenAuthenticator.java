package dev.bedcast.util;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
public final class TokenAuthenticator {
    private final byte[] token;
    private final Set<String> origins;
    public TokenAuthenticator(String token,Collection<String> origins) {
        this.token=token.getBytes(StandardCharsets.UTF_8);this.origins=Collections.unmodifiableSet(new HashSet<>(origins));
    }
    public boolean originAllowed(String origin) { return origin==null || origins.contains(origin); }
    public boolean matches(String supplied) { return supplied!=null && MessageDigest.isEqual(token,supplied.getBytes(StandardCharsets.UTF_8)); }
    public boolean required() { return token.length>0; }
    public boolean authorize(String header) { return !required() || (header!=null && header.startsWith("Bearer ") && matches(header.substring(7))); }
}
