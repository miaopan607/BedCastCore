package dev.bedcast.model;

import java.util.*;

/** 不可变直播数据；不持有服务器对象。 */
public final class GameState {
    public final String arenaId;
    public final String gameId;
    public final String matchId;
    public final String mapName;
    public final GamePhase gameState;
    public final Long startTime;
    public final long elapsedTime;
    public final int currentRound;
    public final int totalRounds;
    public final String gameMode;
    public final int teamCount;
    public final int aliveTeamCount;
    public final String winnerTeamId;
    public final int diamondLevel;
    public final int emeraldLevel;
    public final Integer nextDiamondUpgrade;
    public final Integer nextEmeraldUpgrade;
    public final Map<String,Object> extensions;

    public GameState(String arenaId, String gameId, String matchId, String mapName, GamePhase gameState, Long startTime, long elapsedTime, int currentRound, int totalRounds, String gameMode, int teamCount, int aliveTeamCount, String winnerTeamId, int diamondLevel, int emeraldLevel, Integer nextDiamondUpgrade, Integer nextEmeraldUpgrade, Map<String,Object> extensions) {
        this.arenaId = arenaId;
        this.gameId = gameId;
        this.matchId = matchId;
        this.mapName = mapName;
        this.gameState = gameState;
        this.startTime = startTime;
        this.elapsedTime = elapsedTime;
        this.currentRound = currentRound;
        this.totalRounds = totalRounds;
        this.gameMode = gameMode;
        this.teamCount = teamCount;
        this.aliveTeamCount = aliveTeamCount;
        this.winnerTeamId = winnerTeamId;
        this.diamondLevel = diamondLevel;
        this.emeraldLevel = emeraldLevel;
        this.nextDiamondUpgrade = nextDiamondUpgrade;
        this.nextEmeraldUpgrade = nextEmeraldUpgrade;
        this.extensions = Immutable.json(extensions);
    }
    public String getArenaId() { return arenaId; }
    public String getGameId() { return gameId; }
    public String getMatchId() { return matchId; }
    public String getMapName() { return mapName; }
    public GamePhase getGameState() { return gameState; }
    public Long getStartTime() { return startTime; }
    public long getElapsedTime() { return elapsedTime; }
    public int getCurrentRound() { return currentRound; }
    public int getTotalRounds() { return totalRounds; }
    public String getGameMode() { return gameMode; }
    public int getTeamCount() { return teamCount; }
    public int getAliveTeamCount() { return aliveTeamCount; }
    public String getWinnerTeamId() { return winnerTeamId; }
    public int getDiamondLevel() { return diamondLevel; }
    public int getEmeraldLevel() { return emeraldLevel; }
    public Integer getNextDiamondUpgrade() { return nextDiamondUpgrade; }
    public Integer getNextEmeraldUpgrade() { return nextEmeraldUpgrade; }
    public Map<String,Object> getExtensions() { return extensions; }
    public GameState withSession(String gameId, String matchId, int currentRound, int totalRounds, GamePhase gameState, long elapsedTime, String winnerTeamId) {
        return new GameState(this.arenaId, gameId, matchId, this.mapName, gameState, this.startTime, elapsedTime, currentRound, totalRounds, this.gameMode, this.teamCount, this.aliveTeamCount, winnerTeamId, this.diamondLevel, this.emeraldLevel, this.nextDiamondUpgrade, this.nextEmeraldUpgrade, this.extensions);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GameState)) return false;
        GameState that = (GameState) other;
        return Objects.equals(arenaId, that.arenaId) && Objects.equals(gameId, that.gameId) && Objects.equals(matchId, that.matchId) && Objects.equals(mapName, that.mapName) && Objects.equals(gameState, that.gameState) && Objects.equals(startTime, that.startTime) && elapsedTime == that.elapsedTime && currentRound == that.currentRound && totalRounds == that.totalRounds && Objects.equals(gameMode, that.gameMode) && teamCount == that.teamCount && aliveTeamCount == that.aliveTeamCount && Objects.equals(winnerTeamId, that.winnerTeamId) && diamondLevel == that.diamondLevel && emeraldLevel == that.emeraldLevel && Objects.equals(nextDiamondUpgrade, that.nextDiamondUpgrade) && Objects.equals(nextEmeraldUpgrade, that.nextEmeraldUpgrade) && Objects.equals(extensions, that.extensions);
    }
    @Override public int hashCode() { return Objects.hash(arenaId, gameId, matchId, mapName, gameState, startTime, elapsedTime, currentRound, totalRounds, gameMode, teamCount, aliveTeamCount, winnerTeamId, diamondLevel, emeraldLevel, nextDiamondUpgrade, nextEmeraldUpgrade, extensions); }
}
