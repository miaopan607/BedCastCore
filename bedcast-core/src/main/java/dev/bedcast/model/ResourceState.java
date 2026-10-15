package dev.bedcast.model;

import java.util.*;

/** 不可变直播数据；不持有服务器对象。 */
public final class ResourceState {
    public final int iron;
    public final int gold;
    public final int diamond;
    public final int emerald;

    public ResourceState(int iron, int gold, int diamond, int emerald) {
        this.iron = iron;
        this.gold = gold;
        this.diamond = diamond;
        this.emerald = emerald;
    }
    public int getIron() { return iron; }
    public int getGold() { return gold; }
    public int getDiamond() { return diamond; }
    public int getEmerald() { return emerald; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ResourceState)) return false;
        ResourceState that = (ResourceState) other;
        return iron == that.iron && gold == that.gold && diamond == that.diamond && emerald == that.emerald;
    }
    @Override public int hashCode() { return Objects.hash(iron, gold, diamond, emerald); }
}
