package dev.stonebanner.entity;

import dev.stonebanner.StoneAndBanner;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, StoneAndBanner.MOD_ID);

    public static final RegistryObject<EntityType<HumanNpcEntity>> HUMAN_NPC = ENTITY_TYPES.register(
            "human_npc",
            () -> EntityType.Builder.of(HumanNpcEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .updateInterval(3)
                    .build(StoneAndBanner.MOD_ID + ":human_npc")
    );

    private ModEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
