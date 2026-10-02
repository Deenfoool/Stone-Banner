package dev.stonebanner.citizen;

import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.entity.HumanNpcEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;

/**
 * Minimal physical food source for Citizen hunger.
 *
 * Until settlement storage/logistics exists, hungry Citizens can consume real edible ItemEntity stacks
 * dropped in the world. The controller deliberately uses the shared command system, so EAT movement obeys
 * the same pathing behavior as every other Citizen action and can later swap to stockpile/meal providers.
 */
@Mod.EventBusSubscriber(modid = StoneAndBanner.MOD_ID)
public final class CitizenFoodSystem {
    static final double SEARCH_RANGE = 12.0D;
    static final double EAT_RANGE_SQR = 1.65D * 1.65D;
    static final double RELIEF_PER_NUTRITION = 8.0D;

    private CitizenFoodSystem() {
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof HumanNpcEntity npc)
                || npc.level().isClientSide
                || npc.tickCount % 20 != 0
                || npc.brainState() != CitizenBrainState.EAT) {
            return;
        }

        tickEating(npc);
    }

    private static void tickEating(HumanNpcEntity npc) {
        if (!(npc.level() instanceof ServerLevel level)) {
            return;
        }

        CitizenNeeds needs = npc.citizenData().needs();
        if (!needs.isHungry()) {
            stopEatMovement(npc);
            npc.setBrainState(CitizenBrainState.IDLE);
            return;
        }

        ItemEntity food = level.getEntitiesOfClass(
                        ItemEntity.class,
                        npc.getBoundingBox().inflate(SEARCH_RANGE),
                        CitizenFoodSystem::isEdibleFood
                ).stream()
                .min(Comparator.comparingDouble(npc::distanceToSqr))
                .orElse(null);

        if (food == null) {
            stopEatMovement(npc);
            npc.setBrainState(CitizenBrainState.EAT);
            return;
        }

        if (npc.distanceToSqr(food) <= EAT_RANGE_SQR) {
            consumeOne(npc, food);
            return;
        }

        if (!npc.commandController().hasActiveCommand()
                || npc.commandController().movementState() != CitizenBrainState.EAT) {
            if (!npc.commandController().issueSystemMove(food.blockPosition(), CitizenBrainState.EAT)) {
                npc.setBrainState(CitizenBrainState.EAT);
            }
        }
    }

    private static boolean isEdibleFood(ItemEntity entity) {
        if (entity == null || !entity.isAlive()) {
            return false;
        }
        ItemStack stack = entity.getItem();
        return !stack.isEmpty() && stack.isEdible();
    }

    private static void consumeOne(HumanNpcEntity npc, ItemEntity entity) {
        ItemStack stack = entity.getItem();
        FoodProperties food = stack.getFoodProperties(npc);
        if (food == null) {
            return;
        }

        npc.citizenData().needs().eat(reliefForNutrition(food.getNutrition()));
        stack.shrink(1);
        if (stack.isEmpty()) {
            entity.discard();
        }

        stopEatMovement(npc);
        npc.setBrainState(npc.citizenData().needs().isHungry()
                ? CitizenBrainState.EAT
                : CitizenBrainState.IDLE);
    }

    private static void stopEatMovement(HumanNpcEntity npc) {
        if (npc.commandController().hasActiveCommand()
                && npc.commandController().movementState() == CitizenBrainState.EAT) {
            npc.commandController().stop();
        }
    }

    static double reliefForNutrition(int nutrition) {
        return Math.max(0, nutrition) * RELIEF_PER_NUTRITION;
    }
}
