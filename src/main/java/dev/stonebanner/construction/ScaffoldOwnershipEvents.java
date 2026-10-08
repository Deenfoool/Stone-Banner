package dev.stonebanner.construction;

import dev.stonebanner.StoneAndBanner;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** A player replacing/breaking a temporary cell relinquishes it; later cleanup must not refund it. */
@Mod.EventBusSubscriber(modid=StoneAndBanner.MOD_ID)
public final class ScaffoldOwnershipEvents {
    private static final ThreadLocal<Integer> INTERNAL=ThreadLocal.withInitial(()->0);
    private ScaffoldOwnershipEvents(){}
    static void begin(){INTERNAL.set(INTERNAL.get()+1);}
    static void end(){int depth=INTERNAL.get()-1;if(depth==0)INTERNAL.remove();else INTERNAL.set(depth);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void broken(BlockEvent.BreakEvent event){
        if(INTERNAL.get()==0&&event.getLevel() instanceof ServerLevel level)ConstructionData.forLevel(level).forget(event.getPos());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void placed(BlockEvent.EntityPlaceEvent event){
        if(INTERNAL.get()==0&&event.getLevel() instanceof ServerLevel level){
            var data=ConstructionData.forLevel(level);data.forget(event.getPos());
            if(event instanceof BlockEvent.EntityMultiPlaceEvent multiple)for(var snapshot:multiple.getReplacedBlockSnapshots())data.forget(snapshot.getPos());
        }
    }
}
