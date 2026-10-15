package dev.bedcast.snapshot;
import dev.bedcast.model.*;
import java.util.*;
public final class SnapshotDiff {
    public final boolean gameChanged;
    public final Map<String,PlayerState> players;
    public final Map<String,TeamState> teams;
    public final List<String> removedPlayers, removedTeams;
    public SnapshotDiff(GameSnapshot before, GameSnapshot after) {
        gameChanged=before==null || !Objects.equals(before.game,after.game);
        Map<String,PlayerState> p=new LinkedHashMap<>(); Map<String,TeamState> t=new LinkedHashMap<>();
        after.players.forEach((id,value)-> { if(before==null || !value.equals(before.players.get(id))) p.put(id,value); });
        after.teams.forEach((id,value)-> { if(before==null || !value.equals(before.teams.get(id))) t.put(id,value); });
        players=Immutable.map(p); teams=Immutable.map(t);
        List<String> rp=new ArrayList<>(), rt=new ArrayList<>();
        if(before!=null) { for(String id:before.players.keySet()) if(!after.players.containsKey(id)) rp.add(id);
            for(String id:before.teams.keySet()) if(!after.teams.containsKey(id)) rt.add(id); }
        removedPlayers=Immutable.list(rp); removedTeams=Immutable.list(rt);
    }
}
