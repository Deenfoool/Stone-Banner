package dev.stonebanner;

import com.mojang.logging.LogUtils;
import dev.stonebanner.config.ClientConfig;
import dev.stonebanner.entity.ModEntities;
import dev.stonebanner.network.StoneBannerNetwork;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(StoneAndBanner.MOD_ID)
public final class StoneAndBanner {
    public static final String MOD_ID = "stonebanner";
    public static final Logger LOGGER = LogUtils.getLogger();

    public StoneAndBanner(FMLJavaModLoadingContext context) {
        ModEntities.register(context.getModEventBus());
        dev.stonebanner.citizen.MedicalItems.register(context.getModEventBus());
        dev.stonebanner.production.ProductionBlocks.register(context.getModEventBus());
        dev.stonebanner.geology.ResearchBlocks.register(context.getModEventBus());
        dev.stonebanner.village.VillageBlocks.register(context.getModEventBus());
        StoneBannerNetwork.register();

        context.registerConfig(
                ModConfig.Type.CLIENT,
                ClientConfig.SPEC,
                "stonebanner-client.toml"
        );

        LOGGER.info("Stone & Banner is loading");
    }
}
