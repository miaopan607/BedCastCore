package dev.bedcast.model;

import java.util.*;

/** 不可变直播数据；不持有服务器对象。 */
public final class TrapState {
    public final String id;
    public final String displayName;

    public TrapState(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }
    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TrapState)) return false;
        TrapState that = (TrapState) other;
        return Objects.equals(id, that.id) && Objects.equals(displayName, that.displayName);
    }
    @Override public int hashCode() { return Objects.hash(id, displayName); }
}
