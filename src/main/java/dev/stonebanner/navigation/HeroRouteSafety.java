package dev.stonebanner.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Conservative hero-only automation rules; manual movement and NPC jobs retain their own policy. */
public final class HeroRouteSafety {
    public enum Reason { NONE, UNLOADED, BORDER, FIRE, WATER, NO_AIR, COLLISION, DROP, NO_PATH, BLOCKED, RETRIES }
    private HeroRouteSafety(){}
    public static boolean lowAir(Player player){return player.isUnderWater() && player.getAirSupply()<60;}
    public static Reason terrain(Level level, BlockPos feet) {
        if(feet.getY()<level.getMinBuildHeight() || feet.getY()+2>=level.getMaxBuildHeight())return Reason.BORDER;
        if(!level.getWorldBorder().isWithinBounds(feet))return Reason.BORDER;
        for(var pos:List.of(feet.below(),feet,feet.above(),feet.above(2))) {
            if(!level.hasChunkAt(pos))return Reason.UNLOADED;
            var state=level.getBlockState(pos);
            if(danger(state))return Reason.FIRE;
        }
        // Do not start unattended routes under water; shallow water is allowed if the head has air.
        if(level.getFluidState(feet).is(FluidTags.WATER)
                && (level.getFluidState(feet.above()).is(FluidTags.WATER)
                    || level.getFluidState(feet.below()).is(FluidTags.WATER)
                        && level.getBlockState(feet.below()).getCollisionShape(level,feet.below()).isEmpty()))return Reason.WATER;
        if(level.getBlockState(feet).getCollisionShape(level,feet).isEmpty()
                && level.getBlockState(feet.below()).getCollisionShape(level,feet.below()).isEmpty()
                && !level.getFluidState(feet).is(FluidTags.WATER)
                && !BlockPathfinder.isClimbable(level,feet) && !BlockPathfinder.isClimbable(level,feet.below()))return Reason.DROP;
        return Reason.NONE;
    }
    public static boolean permitted(Level level,BlockPos pos){return terrain(level,pos)==Reason.NONE;}
    private static boolean danger(BlockState state) {
        return state.getFluidState().is(FluidTags.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POWDER_SNOW)
                || state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT);
    }
    /** Sweep the real player body; samples include every crossed chunk and block. */
    public static Reason swimmingSegment(Level level,Player player,Vec3 end) {
        if(lowAir(player))return Reason.NO_AIR;
        double distance=player.position().distanceTo(end);
        if(distance>64)return Reason.NO_PATH;
        int steps=Math.max(1,(int)Math.ceil(distance/.25));
        for(int i=1;i<=steps;i++) {
            Vec3 at=player.position().lerp(end,(double)i/steps);
            AABB body=player.getBoundingBox().move(at.subtract(player.position())).deflate(.02);
            for(BlockPos pos:BlockPos.betweenClosed(BlockPos.containing(body.minX,body.minY,body.minZ),
                    BlockPos.containing(body.maxX,body.maxY,body.maxZ))) {
                if(!level.hasChunkAt(pos))return Reason.UNLOADED;
                if(!level.getWorldBorder().isWithinBounds(pos))return Reason.BORDER;
                if(danger(level.getBlockState(pos)))return Reason.FIRE;
            }
            if(!level.noCollision(player,body))return Reason.COLLISION;
            // A direct swim order must stay in water, never steer unsupported across a shore/cliff.
            if(!level.getFluidState(BlockPos.containing(at)).is(FluidTags.WATER))return Reason.WATER;
        }
        return Reason.NONE;
    }
}
