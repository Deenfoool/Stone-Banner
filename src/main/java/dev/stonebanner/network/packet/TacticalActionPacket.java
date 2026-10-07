package dev.stonebanner.network.packet;

import dev.stonebanner.control.TacticalInteractionRules;
import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.network.StoneBannerNetwork;
import dev.stonebanner.settlement.BannerCommunityService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Executes through the real hero with server-authoritative reach, visibility and vanilla game rules. */
public record TacticalActionPacket(Action action,int entityId,BlockPos block,Direction face,Vec3 hit) {
    public enum Action { ATTACK, INTERACT_ENTITY, USE_BLOCK }
    public TacticalActionPacket {block=block.immutable();}
    public static void encode(TacticalActionPacket p,FriendlyByteBuf b){b.writeEnum(p.action);b.writeVarInt(p.entityId);b.writeBlockPos(p.block);b.writeEnum(p.face);b.writeDouble(p.hit.x);b.writeDouble(p.hit.y);b.writeDouble(p.hit.z);}
    public static TacticalActionPacket decode(FriendlyByteBuf b){return new TacticalActionPacket(b.readEnum(Action.class),b.readVarInt(),b.readBlockPos(),b.readEnum(Direction.class),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()));}
    public static void handle(TacticalActionPacket p,Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();var player=ctx.getSender();if(player!=null)ctx.enqueueWork(()->execute(player,p));ctx.setPacketHandled(true);}
    private static void execute(ServerPlayer player,TacticalActionPacket p) {
        if(player.isSpectator()||!player.isAlive())return;
        var level=player.serverLevel();
        if(p.action==Action.USE_BLOCK) {
            if(!level.hasChunkAt(p.block)
                    ||!TacticalInteractionRules.validHit(p.block,p.hit,
                            level.getBlockState(p.block).getShape(level,p.block,net.minecraft.world.phys.shapes.CollisionContext.of(player)).toAabbs())
                    ||!level.getWorldBorder().isWithinBounds(p.block)||!level.mayInteract(player,p.block)
                    ||!player.canReach(p.block,0)||!TacticalInteractionRules.visible(level,player,p.hit,p.block))return;
            if(level.getBlockState(p.block).getBlock() instanceof BannerBlock) {
                BannerCommunityService.execute(player,p.block,BannerCommunityService.Action.OPEN,"",-1);return;
            }
            var actual=TacticalInteractionRules.trace(level,player,p.hit);
            var hit=actual.getType()==HitResult.Type.BLOCK?actual:new BlockHitResult(p.hit,p.face,p.block,false);
            for (var hand : InteractionHand.values()) {
                var result = player.gameMode.useItemOn(player,level,player.getItemInHand(hand),hand,hit);
                if (result.shouldSwing()) player.swing(hand, true);
                if (result.consumesAction()) break;
            }
            return;
        }
        var entity=level.getEntity(p.entityId);
        if(entity==null||entity==player||!entity.isAlive()||!player.canReach(entity,0)
                ||!TacticalInteractionRules.visible(level,player,entity.getBoundingBox().getCenter(),null))return;
        if(p.action==Action.ATTACK) {
            if(entity instanceof ItemEntity||entity instanceof ExperienceOrb||entity instanceof AbstractArrow
                    ||!entity.isAttackable()||player.getAttackStrengthScale(.5f)<.9f||player.isUsingItem())return;
            player.attack(entity);player.swing(InteractionHand.MAIN_HAND,true);
        } else if(entity instanceof net.minecraft.world.entity.npc.Villager villager
                &&dev.stonebanner.village.VillageService.talk(player,villager)) { }
        else if(entity instanceof HumanNpcEntity npc) {
            if(!dev.stonebanner.village.VillageReturnService.talk(player,npc))StoneBannerNetwork.openTacticalNpc(player,entity.getId());
        }
        else {
            for (var hand : InteractionHand.values()) {
                Vec3 local = entity.getBoundingBox().getCenter().subtract(entity.position());
                var result = net.minecraftforge.common.ForgeHooks.onInteractEntityAt(player, entity, local, hand);
                if (result == null) result = entity.interactAt(player, local, hand);
                if (!result.consumesAction()) result = player.interactOn(entity, hand);
                if (result.shouldSwing()) player.swing(hand, true);
                if (result.consumesAction()) break;
            }
        }
    }
}
