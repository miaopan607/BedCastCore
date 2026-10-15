package dev.bedcast.provider.hmo;

import dev.bedcast.model.*;
import com.andrei1058.bedwars.api.BedWars;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.*;
import java.util.*;

final class HmoInventoryReader {
    private final BedWars api;
    private static final List<String> ITEM_KEYS=Arrays.asList("tnt","fireball","goldenApple","enderPearl","bridgeEgg","waterBucket");
    private static final String[] ARMOR_SLOTS={"boots","leggings","chestplate","helmet"};
    private static final PotionState ABSENT_POTION=new PotionState(false,0,0);
    private static final ItemState AIR=new ItemState("AIR",0,0,Collections.emptyMap());
    HmoInventoryReader(BedWars api){this.api=api;}
    PlayerState read(Player player,String team,boolean participant,boolean participating,boolean spectator,boolean respawning,boolean qualified,
                     Integer kills,Integer deaths,Integer finals,Integer beds,String area) {
        String sword="NONE",pickaxe="NONE",axe="NONE";boolean bow=false;
        int iron=0,gold=0,diamond=0,emerald=0;
        Map<String,Integer> items=new LinkedHashMap<>();
        for(String key:ITEM_KEYS)items.put(key,0);
        for(int slot=0;slot<36;slot++) {
            ItemStack item=player.getInventory().getItem(slot);if(item==null||item.getType()==Material.AIR)continue;
            String material=item.getType().name();int amount=item.getAmount();
            if(material.endsWith("_SWORD"))sword=highest(sword,material.substring(0,material.length()-6),false);
            if(material.endsWith("_PICKAXE"))pickaxe=highest(pickaxe,material.substring(0,material.length()-8),true);
            if(material.endsWith("_AXE"))axe=highest(axe,material.substring(0,material.length()-4),true);
            if(item.getType()==Material.BOW)bow=true;
            switch(item.getType()) {
                case IRON_INGOT:iron+=amount;break;case GOLD_INGOT:gold+=amount;break;case DIAMOND:diamond+=amount;break;case EMERALD:emerald+=amount;break;
                case TNT:increment(items,"tnt",amount);break;case FIREBALL:increment(items,"fireball",amount);break;
                case GOLDEN_APPLE:increment(items,"goldenApple",amount);break;case ENDER_PEARL:increment(items,"enderPearl",amount);break;
                case EGG:increment(items,"bridgeEgg",amount);break;case WATER_BUCKET:increment(items,"waterBucket",amount);break;default:break;
            }
        }
        ItemStack[] armor=player.getInventory().getArmorContents();Map<String,Object> armorSlots=new LinkedHashMap<>();
        for(int i=0;i<armor.length;i++)armorSlots.put(ARMOR_SLOTS[i],armor[i]==null?"AIR":armor[i].getType().name());
        ItemStack leggings=armor.length>1?armor[1]:null;String armorLevel=leggings==null?"NONE":leggings.getType().name().replace("_LEGGINGS","");
        EquipmentState equipment=new EquipmentState(item(player.getItemInHand()),sword,armorLevel,pickaxe,axe,bow,Collections.singletonMap("armor",armorSlots));
        Map<String,PotionState> potions=new LinkedHashMap<>();
        potions.put("invisibility",ABSENT_POTION);potions.put("speed",ABSENT_POTION);potions.put("jumpBoost",ABSENT_POTION);
        for(PotionEffect effect:player.getActivePotionEffects()) {
            String key=effect.getType().equals(PotionEffectType.INVISIBILITY)?"invisibility":effect.getType().equals(PotionEffectType.SPEED)?"speed":effect.getType().equals(PotionEffectType.JUMP)?"jumpBoost":null;
            if(key!=null)potions.put(key,new PotionState(true,effect.getAmplifier(),effect.getDuration()));
        }
        Location location=player.getLocation();
        return new PlayerState(player.getUniqueId().toString(),player.getName(),team,player.isOnline(),participating&&!spectator&&!respawning&&!player.isDead(),
                spectator,respawning,player.getHealth(),player.getMaxHealth(),api.getVersionSupport().getAbsorption(player),player.getFoodLevel(),
                kills,deaths,finals,beds,null,null,null,null,null,equipment,items,new ResourceState(iron,gold,diamond,emerald),potions,
                new Position(location.getWorld().getName(),location.getX(),location.getY(),location.getZ()),location.getYaw(),location.getPitch(),area,
                Immutable.data("participant",participant,"participating",participating,"qualified",qualified));
    }
    private static void increment(Map<String,Integer> counts,String key,int amount){counts.put(key,counts.get(key)+amount);}
    private static String highest(String current,String candidate,boolean tool) {
        List<String> order=tool?TOOL_ORDER:SWORD_ORDER;return order.indexOf(candidate)>order.indexOf(current)?candidate:current;
    }
    private static final List<String> SWORD_ORDER=Arrays.asList("NONE","WOOD","GOLD","STONE","IRON","DIAMOND");
    private static final List<String> TOOL_ORDER=Arrays.asList("NONE","WOOD","STONE","IRON","GOLD","DIAMOND");
    private static ItemState item(ItemStack stack) {
        if(stack==null||stack.getType()==Material.AIR)return AIR;
        Map<String,Integer> enchantments=new LinkedHashMap<>();stack.getEnchantments().forEach((key,value)->enchantments.put(key.getName(),value));
        return new ItemState(stack.getType().name(),stack.getAmount(),stack.getDurability(),enchantments);
    }
}
