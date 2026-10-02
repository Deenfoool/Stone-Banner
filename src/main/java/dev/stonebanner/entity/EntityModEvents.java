package dev.stonebanner.entity;

import dev.stonebanner.StoneAndBanner;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = StoneAndBanner.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class EntityModEvents {
    private EntityModEvents() {
    }

    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.HUMAN_NPC.get(), HumanNpcEntity.createAttributes().build());
    }
}
