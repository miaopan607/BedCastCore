package dev.bedcast.model;

import java.util.*;

/** 不可变直播数据；不持有服务器对象。 */
public final class ItemState {
    public final String material;
    public final int amount;
    public final int durability;
    public final Map<String,Integer> enchantments;

    public ItemState(String material, int amount, int durability, Map<String,Integer> enchantments) {
        this.material = material;
        this.amount = amount;
        this.durability = durability;
        this.enchantments = Immutable.map(enchantments);
    }
    public String getMaterial() { return material; }
    public int getAmount() { return amount; }
    public int getDurability() { return durability; }
    public Map<String,Integer> getEnchantments() { return enchantments; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ItemState)) return false;
        ItemState that = (ItemState) other;
        return Objects.equals(material, that.material) && amount == that.amount && durability == that.durability && Objects.equals(enchantments, that.enchantments);
    }
    @Override public int hashCode() { return Objects.hash(material, amount, durability, enchantments); }
}
