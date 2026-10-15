package dev.bedcast.model;

import java.util.*;

/** 不可变直播数据；不持有服务器对象。 */
public final class EquipmentState {
    public final ItemState heldItem;
    public final String swordLevel;
    public final String armorLevel;
    public final String pickaxeLevel;
    public final String axeLevel;
    public final boolean hasBow;
    public final Map<String,Object> extensions;

    public EquipmentState(ItemState heldItem, String swordLevel, String armorLevel, String pickaxeLevel, String axeLevel, boolean hasBow, Map<String,Object> extensions) {
        this.heldItem = heldItem;
        this.swordLevel = swordLevel;
        this.armorLevel = armorLevel;
        this.pickaxeLevel = pickaxeLevel;
        this.axeLevel = axeLevel;
        this.hasBow = hasBow;
        this.extensions = Immutable.json(extensions);
    }
    public ItemState getHeldItem() { return heldItem; }
    public String getSwordLevel() { return swordLevel; }
    public String getArmorLevel() { return armorLevel; }
    public String getPickaxeLevel() { return pickaxeLevel; }
    public String getAxeLevel() { return axeLevel; }
    public boolean isHasBow() { return hasBow; }
    public Map<String,Object> getExtensions() { return extensions; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof EquipmentState)) return false;
        EquipmentState that = (EquipmentState) other;
        return Objects.equals(heldItem, that.heldItem) && Objects.equals(swordLevel, that.swordLevel) && Objects.equals(armorLevel, that.armorLevel) && Objects.equals(pickaxeLevel, that.pickaxeLevel) && Objects.equals(axeLevel, that.axeLevel) && hasBow == that.hasBow && Objects.equals(extensions, that.extensions);
    }
    @Override public int hashCode() { return Objects.hash(heldItem, swordLevel, armorLevel, pickaxeLevel, axeLevel, hasBow, extensions); }
}
