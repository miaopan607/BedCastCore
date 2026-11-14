package dev.bedcast.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ArenaMapTest {
    @ParameterizedTest @ValueSource(strings={"wool","alternating","air"})
    void consumersRecoverEveryLegacyStateInScanOrder(String scenario) {
        int[] states=new int[4096];
        for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++) {
            int i=(y<<8)|(z<<4)|x;
            states[i]=scenario.equals("wool")?(35<<4)|14:scenario.equals("alternating")?(35<<4)|(i&15):
                    ((x+z+y)%3==0?0:(1<<4)|((x+2*z+3*y)&15));
        }
        int[] expected=states.clone();ArenaMap.Section section=ArenaMap.Section.encode(5,states);
        Arrays.fill(states,0);
        byte[] bytes=Base64.getDecoder().decode(section.blocks);int[] decoded=new int[4096];int index=0;
        for(int offset=0;offset<bytes.length;offset+=4) {
            int length=(bytes[offset]&255)|((bytes[offset+1]&255)<<8);
            int state=(bytes[offset+2]&255)|((bytes[offset+3]&255)<<8);
            assertTrue(length>=1&&length<=4096);assertTrue(index+length<=4096);
            Arrays.fill(decoded,index,index+length,state);index+=length;
        }
        assertEquals(4096,index);assertArrayEquals(expected,decoded);assertEquals(5,section.sectionY);
    }
}
