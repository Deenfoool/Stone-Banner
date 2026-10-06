package dev.stonebanner.geology;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;
import java.util.*;

/** Research and surveyed knowledge are separate. Hidden resource names never enter a client snapshot. */
public final class GeologyRules {
    public static final int MAX_TIER = 4;
    private GeologyRules() {}
    public enum Ore {
        COAL(160,480), COPPER(100,300), IRON(80,240), GOLD(24,72), REDSTONE(80,240),
        LAPIS(16,48), DIAMOND(8,24), EMERALD(4,12), QUARTZ(160,480), ANCIENT_DEBRIS(3,9), OTHER(32,96);
        public final int medium, rich;
        Ore(int medium,int rich) { this.medium=medium;this.rich=rich; }
        public int richness(int count) { return count<=0?0:count<medium?1:count<rich?2:3; }
        public boolean ordinary() { return this==COAL || this==COPPER || this==IRON || this==QUARTZ; }
        public String key() { return "geology.stonebanner.ore."+name().toLowerCase(Locale.ROOT); }
        public static Ore of(BlockState state) {
            if(state.is(Tags.Blocks.ORES_COAL)) return COAL;
            if(state.is(Tags.Blocks.ORES_COPPER)) return COPPER;
            if(state.is(Tags.Blocks.ORES_IRON)) return IRON;
            if(state.is(Tags.Blocks.ORES_GOLD)) return GOLD;
            if(state.is(Tags.Blocks.ORES_REDSTONE)) return REDSTONE;
            if(state.is(Tags.Blocks.ORES_LAPIS)) return LAPIS;
            if(state.is(Tags.Blocks.ORES_DIAMOND)) return DIAMOND;
            if(state.is(Tags.Blocks.ORES_EMERALD)) return EMERALD;
            if(state.is(Tags.Blocks.ORES_QUARTZ)) return QUARTZ;
            if(state.is(Tags.Blocks.ORES_NETHERITE_SCRAP)) return ANCIENT_DEBRIS;
            return OTHER;
        }
    }
    public record Entry(Ore ore,int richness) {
        public Entry { Objects.requireNonNull(ore);if(richness<1||richness>3)throw new IllegalArgumentException("Richness"); }
    }
    public static List<Entry> visible(int research,int surveyed,Map<Ore,Integer> counts) {
        int tier=Math.min(research,surveyed);
        if(tier<2) return List.of();
        var visible=new ArrayList<Entry>();
        for(var ore:Ore.values()) {
            int count=counts.getOrDefault(ore,0);
            if(count>0 && (tier>=3 || ore.ordinary())) visible.add(new Entry(ore,tier>=4||ore.ordinary()?ore.richness(count):1));
        }
        return List.copyOf(visible);
    }
    public static int totalRichness(Map<Ore,Integer> counts) {
        return counts.entrySet().stream().mapToInt(e->e.getKey().richness(e.getValue())).max().orElse(0);
    }
}
