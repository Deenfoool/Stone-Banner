package dev.stonebanner.settlement;

import dev.stonebanner.citizen.CitizenParticipation;
import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.network.StoneBannerNetwork;
import dev.stonebanner.network.packet.BannerCommunitySnapshotPacket;
import dev.stonebanner.storage.StorageData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;
import java.util.Locale;

/** All banner commands are validated server-side against the actual banner and current provisioning. */
public final class BannerCommunityService {
    private BannerCommunityService() {}
    public enum Action { OPEN, CREATE, MOVE, PROMOTE, ADD_RESIDENT }
    public static boolean isStandingBanner(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getBlockState(pos).getBlock() instanceof BannerBlock;
    }
    public static void execute(ServerPlayer player, BlockPos pos, Action action, String name, int entityId) {
        ServerLevel level = player.serverLevel();
        if (player.isSpectator() || player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > 16 * 16
                || !isStandingBanner(level, pos)) {
            feedback(player, "invalid_banner"); return;
        }
        var data = SettlementData.forLevel(level);
        var linked = data.atBanner(pos).orElse(null);
        if (action != Action.OPEN && linked != null && !linked.owner().equals(player.getUUID())) {
            feedback(player, "not_owner"); return;
        }
        SettlementData.Result result = null;
        switch (action) {
            case OPEN -> { }
            case CREATE -> {
                if (!territoryInsideBorder(level, pos) || nearVillage(level, pos)) {
                    feedback(player, "village"); return;
                }
                result = data.create(player.getUUID(), name, pos);
            }
            case MOVE -> result = data.relocate(player.getUUID(), pos);
            case PROMOTE -> {
                var community = data.ownedBy(player.getUUID()).orElse(null);
                if (community == null || !community.banner().equals(pos) || !community.bannerActive()) {
                    feedback(player, "restore_first"); return;
                }
                result = data.promote(player.getUUID(), readiness(level, community));
            }
            case ADD_RESIDENT -> {
                var community = data.ownedBy(player.getUUID()).orElse(null);
                var entity = level.getEntity(entityId);
                if (community == null || !community.banner().equals(pos) || !community.bannerActive()) {
                    feedback(player, "restore_first"); return;
                }
                if (!(entity instanceof HumanNpcEntity npc) || !npc.isAlive() || npc.distanceToSqr(player) > 16 * 16
                        || npc.citizenData().participation() != CitizenParticipation.SETTLER) {
                    feedback(player, "needs_settler"); return;
                }
                var readiness = readiness(level, community);
                int peopleAfter = community.residents().contains(npc.getUUID()) ? community.residents().size() + 1 : community.residents().size() + 2;
                if (readiness.beds() < peopleAfter || readiness.food() < peopleAfter * 4 || readiness.stores() < 1) {
                    feedback(player, "provisions"); return;
                }
                result = data.addResident(player.getUUID(), npc.getUUID());
                if (result == SettlementData.Result.JOINED) {
                    npc.citizenData().home().assign(community.id().toString(),
                            new BlockPos((community.centerX() << 4) + 8, pos.getY(), (community.centerZ() << 4) + 8), 1);
                }
            }
        }
        if (result != null) feedback(player, result.name().toLowerCase(Locale.ROOT));
        open(player, pos);
    }
    private static boolean territoryInsideBorder(ServerLevel level, BlockPos pos) {
        int minX = ((pos.getX() >> 4) - 1) << 4;
        int minZ = ((pos.getZ() >> 4) - 1) << 4;
        return level.getWorldBorder().isWithinBounds(new BlockPos(minX, pos.getY(), minZ))
                && level.getWorldBorder().isWithinBounds(new BlockPos(minX + 47, pos.getY(), minZ + 47));
    }
    private static boolean nearVillage(ServerLevel level, BlockPos pos) {
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        for (int x = cx - 1; x <= cx + 1; x++) for (int z = cz - 1; z <= cz + 1; z++)
            if (level.isCloseToVillage(new BlockPos((x << 4) + 8, pos.getY(), (z << 4) + 8), 1)) return true;
        return false;
    }
    public static SettlementData.Readiness readiness(ServerLevel level, SettlementData.Community c) {
        int residents = 0, beds = 0, food = 0, stores = 0;
        for (var id : c.residents()) {
            var entity = level.getEntity(id);
            if (entity instanceof HumanNpcEntity npc && npc.isAlive()
                    && npc.citizenData().home().communityId().equals(c.id().toString())) residents++;
        }
        for (int x = c.centerX() - 1; x <= c.centerX() + 1; x++) for (int z = c.centerZ() - 1; z <= c.centerZ() + 1; z++) {
            var chunk = level.getChunkSource().getChunkNow(x, z);
            if (chunk == null) continue;
            for (var blockEntity : chunk.getBlockEntities().values()) {
                if (blockEntity instanceof BedBlockEntity && blockEntity.getBlockState().hasProperty(BedBlock.PART)
                        && blockEntity.getBlockState().getValue(BedBlock.PART) == BedPart.HEAD) beds++;
            }
        }
        for (var pos : StorageData.forLevel(level).registeredPositions()) {
            if (!c.contains(pos) || !level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof Container container)) continue;
            stores++;
            for (int i = 0; i < container.getContainerSize(); i++) {
                var stack = container.getItem(i);
                if (stack.isEdible()) food += stack.getCount();
            }
        }
        // Beds and food cover all assigned residents even while their entities are temporarily unloaded.
        return new SettlementData.Readiness(residents, beds, food, stores, c.residents().size());
    }
    public static void open(ServerPlayer player, BlockPos pos) {
        var data = SettlementData.forLevel(player.serverLevel());
        var linked = data.atBanner(pos).orElse(null);
        var c = linked == null ? data.ownedBy(player.getUUID()).orElse(null) : linked;
        var r = c == null ? new SettlementData.Readiness(0, 0, 0, 0) : readiness(player.serverLevel(), c);
        StoneBannerNetwork.sendBannerCommunity(player, new BannerCommunitySnapshotPacket(
                player.serverLevel().dimension().location(), pos, c != null, c == null ? "" : c.name(),
                c != null && c.owner().equals(player.getUUID()), c != null && c.settlement(),
                c != null && c.bannerActive(), c == null ? pos : c.banner(),
                c == null ? pos.getX() >> 4 : c.centerX(), c == null ? pos.getZ() >> 4 : c.centerZ(),
                r.residents(), c == null ? 0 : c.residents().size(), r.beds(), r.food(), r.stores()));
    }
    private static void feedback(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("community.stonebanner.result." + key), true);
    }
}
