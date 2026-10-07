package dev.stonebanner.designation;

import dev.stonebanner.StoneAndBanner;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Defers local excavation-plan repair until world mutations for the current tick are complete. */
@Mod.EventBusSubscriber(modid = StoneAndBanner.MOD_ID)
public final class ExcavationWorldChangeEvents {
    private static final Set<ResourceKey<Level>> PENDING_LEVELS = new LinkedHashSet<>();

    private ExcavationWorldChangeEvents() {
    }

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        mark(event.getLevel(), event.getPos());
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            for (var snapshot : multi.getReplacedBlockSnapshots()) {
                mark(event.getLevel(), snapshot.getPos());
            }
        } else {
            mark(event.getLevel(), event.getPos());
        }
    }

    @SubscribeEvent
    public static void onFluidPlaced(BlockEvent.FluidPlaceBlockEvent event) {
        mark(event.getLevel(), event.getPos());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING_LEVELS.clear();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING_LEVELS.isEmpty()) {
            return;
        }
        for (ResourceKey<Level> key : List.copyOf(PENDING_LEVELS)) {
            PENDING_LEVELS.remove(key);
            ServerLevel level = event.getServer().getLevel(key);
            if (level != null) {
                ExcavationPlanData.forLevel(level).reconcileDirty(level);
            }
        }
    }

    private static void mark(LevelAccessor accessor, BlockPos changed) {
        if (accessor instanceof ServerLevel level
                && ExcavationPlanData.forLevel(level).markWorldChanged(changed) > 0) {
            PENDING_LEVELS.add(level.dimension());
        }
    }
}
