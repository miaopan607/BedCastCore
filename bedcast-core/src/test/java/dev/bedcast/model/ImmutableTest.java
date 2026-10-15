package dev.bedcast.model;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ImmutableTest {
    @Test void publishedValuesDoNotFollowLaterInventoryOrExtensionChanges() {
        Map<String,Integer> enchantments = new LinkedHashMap<>();
        enchantments.put("DAMAGE_ALL", 1);
        ItemState item = new ItemState("IRON_SWORD", 1, 0, enchantments);
        enchantments.put("DAMAGE_ALL", 5);
        assertEquals(1, item.enchantments.get("DAMAGE_ALL"));
        assertThrows(UnsupportedOperationException.class, () -> item.enchantments.clear());
        List<Object> logos = new ArrayList<>();
        logos.add("red.svg");
        Map<String,Object> extensions = new LinkedHashMap<>();
        extensions.put("logos", logos);
        Map<String,Object> frozen = Immutable.json(extensions);
        logos.set(0, "blue.svg");
        assertEquals(Collections.singletonList("red.svg"), frozen.get("logos"));
        assertThrows(UnsupportedOperationException.class, () -> ((List<?>)frozen.get("logos")).clear());
        assertThrows(IllegalArgumentException.class, () -> Immutable.json(Collections.singletonMap("serverObject", new Object())));
    }
}
