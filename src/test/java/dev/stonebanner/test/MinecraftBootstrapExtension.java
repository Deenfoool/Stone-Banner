package dev.stonebanner.test;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Initializes vanilla registries before tests touch ItemStack, Items or other registry-backed classes. */
public final class MinecraftBootstrapExtension implements BeforeAllCallback {
    private static boolean initialized;

    @Override
    public void beforeAll(ExtensionContext context) {
        synchronized (MinecraftBootstrapExtension.class) {
            if (initialized) {
                return;
            }
            SharedConstants.tryDetectVersion();
            try {
                Bootstrap.bootStrap();
            } catch (ExceptionInInitializerError forgeNetworkInitializationFailure) {
                // Forge's patched bootstrap initializes vanilla registries before trying to create its
                // handshake channel. The latter needs ModLauncher transformations that plain JUnit lacks.
                if (BuiltInRegistries.ITEM.keySet().isEmpty()) {
                    throw forgeNetworkInitializationFailure;
                }
            }
            initialized = true;
        }
    }
}
