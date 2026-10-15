package dev.bedcast.event;
public final class EventQueryException extends IllegalArgumentException {
    public final String code;
    public EventQueryException(String code) { super(code); this.code=code; }
}
