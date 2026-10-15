package dev.bedcast.model;

import java.util.*;

/** 不可变直播数据；不持有服务器对象。 */
public final class PlayerState {
    public final String uuid;
    public final String name;
    public final String team;
    public final boolean online;
    public final boolean alive;
    public final boolean spectator;
    public final boolean respawning;
    public final Double health;
    public final Double maxHealth;
    public final Double absorption;
    public final Integer foodLevel;
    public final Integer kills;
    public final Integer deaths;
    public final Integer finalKills;
    public final Integer bedsDestroyed;
    public final Integer currentKillStreak;
    public final String lastKiller;
    public final String lastVictim;
    public final Long lastDeathTime;
    public final Long lastKillTime;
    public final EquipmentState equipment;
    public final Map<String,Integer> items;
    public final ResourceState resources;
    public final Map<String,PotionState> potions;
    public final Position position;
    public final Float yaw;
    public final Float pitch;
    public final String currentArea;
    public final Map<String,Object> extensions;

    public PlayerState(String uuid, String name, String team, boolean online, boolean alive, boolean spectator, boolean respawning, Double health, Double maxHealth, Double absorption, Integer foodLevel, Integer kills, Integer deaths, Integer finalKills, Integer bedsDestroyed, Integer currentKillStreak, String lastKiller, String lastVictim, Long lastDeathTime, Long lastKillTime, EquipmentState equipment, Map<String,Integer> items, ResourceState resources, Map<String,PotionState> potions, Position position, Float yaw, Float pitch, String currentArea, Map<String,Object> extensions) {
        this.uuid = uuid;
        this.name = name;
        this.team = team;
        this.online = online;
        this.alive = alive;
        this.spectator = spectator;
        this.respawning = respawning;
        this.health = health;
        this.maxHealth = maxHealth;
        this.absorption = absorption;
        this.foodLevel = foodLevel;
        this.kills = kills;
        this.deaths = deaths;
        this.finalKills = finalKills;
        this.bedsDestroyed = bedsDestroyed;
        this.currentKillStreak = currentKillStreak;
        this.lastKiller = lastKiller;
        this.lastVictim = lastVictim;
        this.lastDeathTime = lastDeathTime;
        this.lastKillTime = lastKillTime;
        this.equipment = equipment;
        this.items = Immutable.map(items);
        this.resources = resources;
        this.potions = Immutable.map(potions);
        this.position = position;
        this.yaw = yaw;
        this.pitch = pitch;
        this.currentArea = currentArea;
        this.extensions = Immutable.json(extensions);
    }
    public String getUuid() { return uuid; }
    public String getName() { return name; }
    public String getTeam() { return team; }
    public boolean isOnline() { return online; }
    public boolean isAlive() { return alive; }
    public boolean isSpectator() { return spectator; }
    public boolean isRespawning() { return respawning; }
    public Double getHealth() { return health; }
    public Double getMaxHealth() { return maxHealth; }
    public Double getAbsorption() { return absorption; }
    public Integer getFoodLevel() { return foodLevel; }
    public Integer getKills() { return kills; }
    public Integer getDeaths() { return deaths; }
    public Integer getFinalKills() { return finalKills; }
    public Integer getBedsDestroyed() { return bedsDestroyed; }
    public Integer getCurrentKillStreak() { return currentKillStreak; }
    public String getLastKiller() { return lastKiller; }
    public String getLastVictim() { return lastVictim; }
    public Long getLastDeathTime() { return lastDeathTime; }
    public Long getLastKillTime() { return lastKillTime; }
    public EquipmentState getEquipment() { return equipment; }
    public Map<String,Integer> getItems() { return items; }
    public ResourceState getResources() { return resources; }
    public Map<String,PotionState> getPotions() { return potions; }
    public Position getPosition() { return position; }
    public Float getYaw() { return yaw; }
    public Float getPitch() { return pitch; }
    public String getCurrentArea() { return currentArea; }
    public Map<String,Object> getExtensions() { return extensions; }
    public PlayerState withCombat(Integer currentKillStreak, String lastKiller, String lastVictim, Long lastDeathTime, Long lastKillTime) {
        return new PlayerState(this.uuid, this.name, this.team, this.online, this.alive, this.spectator, this.respawning, this.health, this.maxHealth, this.absorption, this.foodLevel, this.kills, this.deaths, this.finalKills, this.bedsDestroyed, currentKillStreak, lastKiller, lastVictim, lastDeathTime, lastKillTime, this.equipment, this.items, this.resources, this.potions, this.position, this.yaw, this.pitch, this.currentArea, this.extensions);
    }
    public PlayerState withPresence(boolean online, boolean alive, boolean spectator, boolean respawning, Map<String,Object> extensions) {
        return new PlayerState(this.uuid, this.name, this.team, online, alive, spectator, respawning, this.health, this.maxHealth, this.absorption, this.foodLevel, this.kills, this.deaths, this.finalKills, this.bedsDestroyed, this.currentKillStreak, this.lastKiller, this.lastVictim, this.lastDeathTime, this.lastKillTime, this.equipment, this.items, this.resources, this.potions, this.position, this.yaw, this.pitch, this.currentArea, extensions);
    }
    public PlayerState withStatistics(Integer kills, Integer deaths, Integer finalKills, Integer bedsDestroyed) {
        return new PlayerState(this.uuid, this.name, this.team, this.online, this.alive, this.spectator, this.respawning, this.health, this.maxHealth, this.absorption, this.foodLevel, kills, deaths, finalKills, bedsDestroyed, this.currentKillStreak, this.lastKiller, this.lastVictim, this.lastDeathTime, this.lastKillTime, this.equipment, this.items, this.resources, this.potions, this.position, this.yaw, this.pitch, this.currentArea, this.extensions);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PlayerState)) return false;
        PlayerState that = (PlayerState) other;
        return Objects.equals(uuid, that.uuid) && Objects.equals(name, that.name) && Objects.equals(team, that.team) && online == that.online && alive == that.alive && spectator == that.spectator && respawning == that.respawning && Objects.equals(health, that.health) && Objects.equals(maxHealth, that.maxHealth) && Objects.equals(absorption, that.absorption) && Objects.equals(foodLevel, that.foodLevel) && Objects.equals(kills, that.kills) && Objects.equals(deaths, that.deaths) && Objects.equals(finalKills, that.finalKills) && Objects.equals(bedsDestroyed, that.bedsDestroyed) && Objects.equals(currentKillStreak, that.currentKillStreak) && Objects.equals(lastKiller, that.lastKiller) && Objects.equals(lastVictim, that.lastVictim) && Objects.equals(lastDeathTime, that.lastDeathTime) && Objects.equals(lastKillTime, that.lastKillTime) && Objects.equals(equipment, that.equipment) && Objects.equals(items, that.items) && Objects.equals(resources, that.resources) && Objects.equals(potions, that.potions) && Objects.equals(position, that.position) && Objects.equals(yaw, that.yaw) && Objects.equals(pitch, that.pitch) && Objects.equals(currentArea, that.currentArea) && Objects.equals(extensions, that.extensions);
    }
    @Override public int hashCode() { return Objects.hash(uuid, name, team, online, alive, spectator, respawning, health, maxHealth, absorption, foodLevel, kills, deaths, finalKills, bedsDestroyed, currentKillStreak, lastKiller, lastVictim, lastDeathTime, lastKillTime, equipment, items, resources, potions, position, yaw, pitch, currentArea, extensions); }
}
