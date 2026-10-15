package dev.bedcast.model;

import java.util.*;

/** 不可变直播数据；不持有服务器对象。 */
public final class Position {
    public final String world;
    public final double x;
    public final double y;
    public final double z;

    public Position(String world, double x, double y, double z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }
    public String getWorld() { return world; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Position)) return false;
        Position that = (Position) other;
        return Objects.equals(world, that.world) && Double.compare(x, that.x) == 0 && Double.compare(y, that.y) == 0 && Double.compare(z, that.z) == 0;
    }
    @Override public int hashCode() { return Objects.hash(world, x, y, z); }
}
