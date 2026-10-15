package dev.bedcast.model;

import java.util.*;

/** 不可变直播数据；不持有服务器对象。 */
public final class TeamState {
    public final String teamId;
    public final String teamName;
    public final String displayName;
    public final String color;
    public final boolean bedAlive;
    public final boolean eliminated;
    public final int playerCount;
    public final int alivePlayers;
    public final Integer kills;
    public final Integer deaths;
    public final Integer finalKills;
    public final Integer bedsDestroyed;
    public final boolean sharpness;
    public final int protectionLevel;
    public final int hasteLevel;
    public final int forgeLevel;
    public final List<TrapState> trapQueue;
    public final TrapState activeTrap;
    public final Map<String,Object> extensions;

    public TeamState(String teamId, String teamName, String displayName, String color, boolean bedAlive, boolean eliminated, int playerCount, int alivePlayers, Integer kills, Integer deaths, Integer finalKills, Integer bedsDestroyed, boolean sharpness, int protectionLevel, int hasteLevel, int forgeLevel, List<TrapState> trapQueue, TrapState activeTrap, Map<String,Object> extensions) {
        this.teamId = teamId;
        this.teamName = teamName;
        this.displayName = displayName;
        this.color = color;
        this.bedAlive = bedAlive;
        this.eliminated = eliminated;
        this.playerCount = playerCount;
        this.alivePlayers = alivePlayers;
        this.kills = kills;
        this.deaths = deaths;
        this.finalKills = finalKills;
        this.bedsDestroyed = bedsDestroyed;
        this.sharpness = sharpness;
        this.protectionLevel = protectionLevel;
        this.hasteLevel = hasteLevel;
        this.forgeLevel = forgeLevel;
        this.trapQueue = Immutable.list(trapQueue);
        this.activeTrap = activeTrap;
        this.extensions = Immutable.json(extensions);
    }
    public String getTeamId() { return teamId; }
    public String getTeamName() { return teamName; }
    public String getDisplayName() { return displayName; }
    public String getColor() { return color; }
    public boolean isBedAlive() { return bedAlive; }
    public boolean isEliminated() { return eliminated; }
    public int getPlayerCount() { return playerCount; }
    public int getAlivePlayers() { return alivePlayers; }
    public Integer getKills() { return kills; }
    public Integer getDeaths() { return deaths; }
    public Integer getFinalKills() { return finalKills; }
    public Integer getBedsDestroyed() { return bedsDestroyed; }
    public boolean isSharpness() { return sharpness; }
    public int getProtectionLevel() { return protectionLevel; }
    public int getHasteLevel() { return hasteLevel; }
    public int getForgeLevel() { return forgeLevel; }
    public List<TrapState> getTrapQueue() { return trapQueue; }
    public TrapState getActiveTrap() { return activeTrap; }
    public Map<String,Object> getExtensions() { return extensions; }
    public TeamState withTotals(boolean eliminated, int playerCount, int alivePlayers, Integer kills, Integer deaths, Integer finalKills, Integer bedsDestroyed) {
        return new TeamState(this.teamId, this.teamName, this.displayName, this.color, this.bedAlive, eliminated, playerCount, alivePlayers, kills, deaths, finalKills, bedsDestroyed, this.sharpness, this.protectionLevel, this.hasteLevel, this.forgeLevel, this.trapQueue, this.activeTrap, this.extensions);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TeamState)) return false;
        TeamState that = (TeamState) other;
        return Objects.equals(teamId, that.teamId) && Objects.equals(teamName, that.teamName) && Objects.equals(displayName, that.displayName) && Objects.equals(color, that.color) && bedAlive == that.bedAlive && eliminated == that.eliminated && playerCount == that.playerCount && alivePlayers == that.alivePlayers && Objects.equals(kills, that.kills) && Objects.equals(deaths, that.deaths) && Objects.equals(finalKills, that.finalKills) && Objects.equals(bedsDestroyed, that.bedsDestroyed) && sharpness == that.sharpness && protectionLevel == that.protectionLevel && hasteLevel == that.hasteLevel && forgeLevel == that.forgeLevel && Objects.equals(trapQueue, that.trapQueue) && Objects.equals(activeTrap, that.activeTrap) && Objects.equals(extensions, that.extensions);
    }
    @Override public int hashCode() { return Objects.hash(teamId, teamName, displayName, color, bedAlive, eliminated, playerCount, alivePlayers, kills, deaths, finalKills, bedsDestroyed, sharpness, protectionLevel, hasteLevel, forgeLevel, trapQueue, activeTrap, extensions); }
}
