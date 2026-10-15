package dev.bedcast.model;

import java.util.*;

/** 不可变直播数据；不持有服务器对象。 */
public final class PotionState {
    public final boolean active;
    public final int amplifier;
    public final int remainingTicks;

    public PotionState(boolean active, int amplifier, int remainingTicks) {
        this.active = active;
        this.amplifier = amplifier;
        this.remainingTicks = remainingTicks;
    }
    public boolean isActive() { return active; }
    public int getAmplifier() { return amplifier; }
    public int getRemainingTicks() { return remainingTicks; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PotionState)) return false;
        PotionState that = (PotionState) other;
        return active == that.active && amplifier == that.amplifier && remainingTicks == that.remainingTicks;
    }
    @Override public int hashCode() { return Objects.hash(active, amplifier, remainingTicks); }
}
