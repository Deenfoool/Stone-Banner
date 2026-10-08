package dev.stonebanner.storage;

import dev.stonebanner.control.TacticalInteractionRules;
import dev.stonebanner.network.StoneBannerNetwork;
import dev.stonebanner.network.packet.StorageManagementSnapshotPacket;
import dev.stonebanner.settlement.SettlementData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Local, authoritative management of real storage; no item movement or command permission required. */
public final class StorageManagementService {
    public enum Action { OPEN, REFRESH, REGISTER, UNREGISTER }
    public enum Result { READY, ADDED, REMOVED, ALREADY_REGISTERED, LIMIT_REACHED, FORBIDDEN, INVALID }
    private static final Map<ServerPlayer,Long> LAST_REQUEST = new WeakHashMap<>();
    private StorageManagementService() {}

    public static boolean supported(Level level, BlockPos pos) {
        var entity = level.getBlockEntity(pos);
        return entity instanceof ChestBlockEntity || entity instanceof BarrelBlockEntity;
    }

    /** Both halves must be loaded and still form the same chest. A broken pair is never partly registered. */
    public static List<BlockPos> members(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !supported(level,pos)) return List.of();
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE)
            return List.of(pos.immutable());
        var other = pos.relative(ChestBlock.getConnectedDirection(state));
        if (!level.hasChunkAt(other) || !(level.getBlockEntity(other) instanceof ChestBlockEntity)) return List.of();
        var neighbor = level.getBlockState(other);
        if (neighbor.getBlock() != state.getBlock() || neighbor.getValue(ChestBlock.TYPE) == ChestType.SINGLE
                || neighbor.getValue(ChestBlock.TYPE) == state.getValue(ChestBlock.TYPE)
                || neighbor.getValue(ChestBlock.FACING) != state.getValue(ChestBlock.FACING)
                || !other.relative(ChestBlock.getConnectedDirection(neighbor)).equals(pos)) return List.of();
        return List.of(pos.immutable(),other.immutable());
    }

    private static boolean valid(ServerPlayer player, BlockPos pos, List<BlockPos> members) {
        var level = player.serverLevel();
        if (!player.isAlive() || player.isSpectator() || members.isEmpty() || !player.canReach(pos,0)
                || !TacticalInteractionRules.visible(level,player,Vec3.atCenterOf(pos),pos)) return false;
        for (var member : members) {
            if (!level.getWorldBorder().isWithinBounds(member) || !level.mayInteract(player,member)
                    || !(level.getBlockEntity(member) instanceof BaseContainerBlockEntity container) || !container.canOpen(player)) return false;
        }
        return true;
    }

    private static boolean canManage(ServerPlayer player, List<BlockPos> members) {
        boolean operator = player.hasPermissions(2);
        return StorageData.forLevel(player.serverLevel()).canManage(members,player.getUUID(),operator)
                && (operator || SettlementData.forLevel(player.serverLevel()).communities().stream()
                    .noneMatch(c -> !c.owner().equals(player.getUUID()) && members.stream().anyMatch(c::contains)));
    }

    public static void execute(ServerPlayer player, BlockPos pos, Action action) {
        var level = player.serverLevel();
        long now=level.getGameTime();Long last=LAST_REQUEST.get(player);
        if (last!=null && now>=last && now-last<3) return;
        LAST_REQUEST.put(player,now);
        var positions=members(level,pos);
        if (!valid(player,pos,positions)) {
            player.displayClientMessage(Component.translatable("storage.stonebanner.result.invalid"),true);return;
        }
        var data=StorageData.forLevel(level);Result result=Result.READY;
        if (action==Action.REGISTER || action==Action.UNREGISTER) {
            if (!canManage(player,positions)) result=Result.FORBIDDEN;
            else if (action==Action.REGISTER) result=switch(data.registerManaged(level,positions,player.getUUID(),player.hasPermissions(2))) {
                case ADDED -> Result.ADDED;
                case ALREADY_REGISTERED -> Result.ALREADY_REGISTERED;
                case LIMIT_REACHED -> Result.LIMIT_REACHED;
                case FORBIDDEN -> Result.FORBIDDEN;
                case NOT_A_CONTAINER -> Result.INVALID;
            };
            else result=data.unregisterManaged(positions,player.getUUID(),player.hasPermissions(2))?Result.REMOVED:Result.FORBIDDEN;
        }
        int registered=0,occupied=0,slots=0;long items=0;
        for (var member:positions) {
            if (data.isRegistered(member)) registered++;
            var container=(BaseContainerBlockEntity)level.getBlockEntity(member);
            slots+=container.getContainerSize();
            for(int i=0;i<container.getContainerSize();i++)if(!container.getItem(i).isEmpty()) {
                occupied++;items+=container.getItem(i).getCount();
            }
        }
        StoneBannerNetwork.sendStorageManagement(player,new StorageManagementSnapshotPacket(level.dimension().location(),pos,
                action==Action.OPEN,registered,positions.size(),canManage(player,positions),occupied,slots,(int)Math.min(Integer.MAX_VALUE,items),result));
    }
}
