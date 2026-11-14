package dev.bedcast.model;

import java.util.Base64;
import java.util.Collections;
import java.util.List;

/** 单个竞技场 generation 的初始地图；仅 READY 发布完整区块。 */
public final class ArenaMap {
    public enum Status { PREPARING, READY, INVALID_CONFIG, INITIAL_STATE_MISSED, CAPTURE_FAILED }
    public final Status status;
    public final String mapId, world;
    public final Long capturedAt;
    public final Bounds bounds, excludedWaitingRegion;
    public final List<Chunk> chunks;

    public ArenaMap(Status status, String mapId, String world, Long capturedAt, Bounds bounds, Bounds excludedWaitingRegion, List<Chunk> chunks) {
        this.status=status;this.mapId=mapId;this.world=world;this.capturedAt=capturedAt;
        this.bounds=bounds;this.excludedWaitingRegion=excludedWaitingRegion;
        this.chunks=status==Status.READY?Immutable.list(chunks):Collections.emptyList();
    }
    /** 所有轴均为最小值包含、最大值不包含。 */
    public static final class Bounds {
        public final int minX, minY, minZ, maxX, maxY, maxZ;
        public Bounds(int minX,int minY,int minZ,int maxX,int maxY,int maxZ) {
            this.minX=minX;this.minY=minY;this.minZ=minZ;this.maxX=maxX;this.maxY=maxY;this.maxZ=maxZ;
        }
    }
    public static final class Chunk {
        public final int x,z;
        public final List<Section> sections;
        public Chunk(int x,int z,List<Section> sections){this.x=x;this.z=z;this.sections=Immutable.list(sections);}
    }
    public static final class Section {
        public final int sectionY;
        public final String blocks;
        public Section(int sectionY,String blocks){this.sectionY=sectionY;this.blocks=blocks;}
        /** 按 X、Z、Y 顺序输入 4096 个值，编码为 length/state 的 u16 小端 RLE。 */
        public static Section encode(int sectionY,int[] states) {
            if(sectionY<0||sectionY>15||states.length!=4096)throw new IllegalArgumentException("区段编号或方块数量无效");
            int runs=1;
            for(int i=1;i<states.length;i++)if(states[i]!=states[i-1])runs++;
            byte[] bytes=new byte[runs*4];int offset=0;
            for(int start=0;start<states.length;) {
                int end=start+1,state=states[start];
                if(state<0||state>65535)throw new IllegalArgumentException("方块值超出 u16 范围");
                while(end<states.length&&states[end]==state)end++;
                int length=end-start;
                bytes[offset++]=(byte)length;bytes[offset++]=(byte)(length>>>8);
                bytes[offset++]=(byte)state;bytes[offset++]=(byte)(state>>>8);start=end;
            }
            return new Section(sectionY,Base64.getEncoder().encodeToString(bytes));
        }
    }
}
