package dev.stonebanner.control;

import dev.stonebanner.entity.HumanNpcEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;

/** Shared by client choices and server execution; protected actors never become automatic attacks. */
public final class HeroActionRules {
    private HeroActionRules(){}
    public static boolean protectedTarget(Player player,Entity target) {
        return target==player || target instanceof HumanNpcEntity || target instanceof AbstractVillager
                || target instanceof Player || target instanceof TamableAnimal pet && pet.isTame()
                || player!=null && target.isAlliedTo(player);
    }
    public static boolean canAttack(Player player,Entity target) {
        return target!=null && target.isAlive() && target.isAttackable() && !target.isSpectator()
                && !protectedTarget(player,target)
                && !(target instanceof net.minecraft.world.entity.item.ItemEntity)
                && !(target instanceof net.minecraft.world.entity.ExperienceOrb)
                && !(target instanceof net.minecraft.world.entity.projectile.Projectile);
    }
}
