package dev.stonebanner.network;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

/** Catches client classes in packet constant pools before dedicated-server verification resolves them. */
class ScreenPacketIsolationTest {
    @Test void commonScreenPacketsDoNotReferenceMinecraftClientClasses() throws Exception {
        for (String packet : new String[]{"OpenTacticalNpcPacket", "GeologySnapshotPacket", "BannerCommunitySnapshotPacket", "VillageJournalPacket", "VillageRecruitmentPacket", "CitizenInventorySnapshotPacket", "StorageManagementSnapshotPacket"}) {
            String resource = "dev/stonebanner/network/packet/" + packet + ".class";
            try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(stream, resource);
                String pool = new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
                assertFalse(pool.contains("net/minecraft/client/"), "Direct Minecraft client reference in " + packet);
                assertFalse(pool.contains("dev/stonebanner/client/screen/"), "Direct screen reference in " + packet);
            }
        }
    }
}
