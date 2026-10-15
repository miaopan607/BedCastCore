package dev.bedcast.provider;
public final class ArenaDescriptor {
    public final String arenaId;
    public final String mapName;
    /** 每次底层竞技场实例重建变化，不属于公开比赛 ID。 */
    public final long generation;
    public ArenaDescriptor(String arenaId, String mapName, long generation) {
        this.arenaId = arenaId; this.mapName = mapName; this.generation = generation;
    }
}
