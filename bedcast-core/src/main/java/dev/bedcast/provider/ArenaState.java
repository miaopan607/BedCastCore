package dev.bedcast.provider;
import dev.bedcast.model.*;
import java.util.*;
/** 同一次主线程观察产生的完整竞技场数据。 */
public final class ArenaState {
    public final GameState game;
    public final Map<String,PlayerState> players;
    public final Map<String,TeamState> teams;
    public final ArenaMap map;
    public ArenaState(GameState game, Map<String,PlayerState> players, Map<String,TeamState> teams, ArenaMap map) {
        this.game = game; this.players = Immutable.map(players); this.teams = Immutable.map(teams); this.map = map;
    }
}
