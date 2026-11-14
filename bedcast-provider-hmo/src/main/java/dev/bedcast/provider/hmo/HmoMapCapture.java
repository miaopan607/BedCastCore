package dev.bedcast.provider.hmo;

import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.configuration.ConfigPath;
import dev.bedcast.model.ArenaMap;
import dev.bedcast.model.GamePhase;
import org.bukkit.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 仅在 Bukkit 主线程推进，不保留世界区块或快照引用。 */
final class HmoMapCapture {
    private final IArena arena;
    private final Logger logger;
    private final String mapId=UUID.randomUUID().toString();
    private final List<ArenaMap.Chunk> chunks=new ArrayList<>();
    private ArenaMap map;
    private ArenaMap.Bounds bounds,waiting;
    private int chunkX,chunkZ,minChunkX,maxChunkX,maxChunkZ;

    HmoMapCapture(IArena arena,Logger logger) {
        this.arena=arena;this.logger=logger;publish(ArenaMap.Status.PREPARING);
    }
    ArenaMap current(){return map;}
    void stopBeforeRunning() {
        if(map.status==ArenaMap.Status.PREPARING){chunks.clear();publish(ArenaMap.Status.INITIAL_STATE_MISSED);}
    }
    private void publish(ArenaMap.Status status) {
        map=new ArenaMap(status,mapId,arena.getWorld()==null?null:arena.getWorld().getName(),
                status==ArenaMap.Status.READY?System.currentTimeMillis():null,bounds,waiting,chunks);
    }
    private void initialize(World world) {
        WorldBorder border=world.getWorldBorder();double size=border.getSize();Location center=border.getCenter();
        if(!Double.isFinite(size)||size<=0||!Double.isFinite(center.getX())||!Double.isFinite(center.getZ()))
            throw new IllegalArgumentException("世界边界必须为有限正数且中心有效");
        double minX=Math.floor(center.getX()-size/2),minZ=Math.floor(center.getZ()-size/2);
        double maxX=Math.ceil(center.getX()+size/2),maxZ=Math.ceil(center.getZ()+size/2);
        if(minX<Integer.MIN_VALUE||minZ<Integer.MIN_VALUE||maxX>Integer.MAX_VALUE||maxZ>Integer.MAX_VALUE||minX>=maxX||minZ>=maxZ)
            throw new IllegalArgumentException("世界边界超出方块坐标范围或范围为空");
        bounds=new ArenaMap.Bounds((int)minX,0,(int)minZ,(int)maxX,world.getMaxHeight(),(int)maxZ);
        Location a=arena.getConfig().getArenaLoc(ConfigPath.ARENA_WAITING_POS1),b=arena.getConfig().getArenaLoc(ConfigPath.ARENA_WAITING_POS2);
        if(a==null||b==null||!world.equals(a.getWorld())||!world.equals(b.getWorld()))
            throw new IllegalArgumentException("等待区角点缺失、无法解析或不属于竞技世界");
        waiting=new ArenaMap.Bounds(Math.min(a.getBlockX(),b.getBlockX()),Math.min(a.getBlockY(),b.getBlockY()),Math.min(a.getBlockZ(),b.getBlockZ()),
                Math.max(a.getBlockX(),b.getBlockX()),Math.max(a.getBlockY(),b.getBlockY()),Math.max(a.getBlockZ(),b.getBlockZ()));
        if(waiting.minX>=waiting.maxX||waiting.minY>=waiting.maxY||waiting.minZ>=waiting.maxZ||waiting.minY<0||waiting.maxY>bounds.maxY)
            throw new IllegalArgumentException("等待区范围为空或高度超出世界范围");
        minChunkX=Math.floorDiv(bounds.minX,16);maxChunkX=Math.floorDiv(bounds.maxX-1,16);
        chunkX=minChunkX;chunkZ=Math.floorDiv(bounds.minZ,16);maxChunkZ=Math.floorDiv(bounds.maxZ-1,16);
    }
    void advanceChunk() {
        if(map.status!=ArenaMap.Status.PREPARING)return;
        GamePhase phase=HmoStateMapper.phase(arena);
        if(phase==GamePhase.RUNNING){stopBeforeRunning();return;}
        if(phase!=GamePhase.WAITING&&phase!=GamePhase.COUNTDOWN) {
            if(bounds!=null)stopBeforeRunning();
            return;
        }
        World world=arena.getWorld();if(world==null)return;
        if(bounds==null) {
            try{initialize(world);}catch(RuntimeException error) {
                chunks.clear();publish(ArenaMap.Status.INVALID_CONFIG);
                logger.log(Level.WARNING,"竞技场 "+arena.getArenaName()+" 地图配置无效: "+error.getMessage());return;
            }
        }
        try {
            ChunkSnapshot snapshot=world.getChunkAt(chunkX,chunkZ).getChunkSnapshot(false,false,false);
            List<ArenaMap.Section> sections=new ArrayList<>();int[] states=null;
            for(int sectionY=0;sectionY<(bounds.maxY+15)/16;sectionY++) {
                if(snapshot.isSectionEmpty(sectionY))continue;
                if(states==null)states=new int[4096];else Arrays.fill(states,0);
                boolean nonAir=false;
                for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++) {
                    int wx=chunkX*16+x,wy=sectionY*16+y,wz=chunkZ*16+z;
                    if(wx<bounds.minX||wx>=bounds.maxX||wz<bounds.minZ||wz>=bounds.maxZ||wy>=bounds.maxY)continue;
                    if(wx>=waiting.minX&&wx<waiting.maxX&&wy>=waiting.minY&&wy<waiting.maxY&&wz>=waiting.minZ&&wz<waiting.maxZ)continue;
                    int state=(snapshot.getBlockTypeId(x,wy,z)<<4)|(snapshot.getBlockData(x,wy,z)&15);
                    states[(y<<8)|(z<<4)|x]=state;nonAir|=state!=0;
                }
                if(nonAir)sections.add(ArenaMap.Section.encode(sectionY,states));
            }
            chunks.add(new ArenaMap.Chunk(chunkX,chunkZ,sections));
            if(chunkX==maxChunkX){chunkX=minChunkX;chunkZ++;}else chunkX++;
            if(chunkZ>maxChunkZ){publish(ArenaMap.Status.READY);chunks.clear();}
        }catch(RuntimeException error) {
            chunks.clear();publish(ArenaMap.Status.CAPTURE_FAILED);
            logger.log(Level.WARNING,"竞技场 "+arena.getArenaName()+" 初始地图采集失败",error);
        }
    }
}
