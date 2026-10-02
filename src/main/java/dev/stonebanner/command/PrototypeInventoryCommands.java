package dev.stonebanner.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.entity.HumanNpcEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Temporary dev commands for inspecting/filling the real Citizen personal inventory. */
@Mod.EventBusSubscriber(modid = StoneAndBanner.MOD_ID)
public final class PrototypeInventoryCommands {
    private static final SimpleCommandExceptionType NOT_HUMAN_NPC =
            new SimpleCommandExceptionType(Component.literal("Target is not a Stone & Banner Human NPC"));
    private static final SimpleCommandExceptionType INVALID_ITEM =
            new SimpleCommandExceptionType(Component.literal("Unknown Minecraft item id"));

    private PrototypeInventoryCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("stonebanner")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("npc")
                                .then(Commands.literal("give")
                                        .then(Commands.argument("npc", EntityArgument.entity())
                                                .then(Commands.argument("item", StringArgumentType.word())
                                                        .executes(context -> give(
                                                                context.getSource(),
                                                                EntityArgument.getEntity(context, "npc"),
                                                                StringArgumentType.getString(context, "item"),
                                                                1
                                                        ))
                                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 576))
                                                                .executes(context -> give(
                                                                        context.getSource(),
                                                                        EntityArgument.getEntity(context, "npc"),
                                                                        StringArgumentType.getString(context, "item"),
                                                                        IntegerArgumentType.getInteger(context, "count")
                                                                ))))))
                                .then(Commands.literal("inventory")
                                        .then(Commands.argument("npc", EntityArgument.entity())
                                                .executes(context -> inventory(
                                                        context.getSource(),
                                                        EntityArgument.getEntity(context, "npc")
                                                ))))
                        )
        );
    }

    private static int give(CommandSourceStack source, Entity entity, String itemName, int count)
            throws CommandSyntaxException {
        HumanNpcEntity npc = requireHumanNpc(entity);
        ResourceLocation id = ResourceLocation.tryParse(itemName);
        if (id == null) {
            throw INVALID_ITEM.create();
        }
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElseThrow(INVALID_ITEM::create);
        ItemStack remainder = npc.citizenData().inventory().add(new ItemStack(item, count));
        int inserted = count - remainder.getCount();
        source.sendSuccess(
                () -> Component.literal("Inserted " + inserted + "x " + id + " into Citizen inventory"),
                false
        );
        return inserted;
    }

    private static int inventory(CommandSourceStack source, Entity entity) throws CommandSyntaxException {
        HumanNpcEntity npc = requireHumanNpc(entity);
        StringBuilder text = new StringBuilder("Citizen inventory:");
        for (int slot = 0; slot < dev.stonebanner.citizen.CitizenInventory.SLOT_COUNT; slot++) {
            ItemStack stack = npc.citizenData().inventory().stack(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            text.append(" [").append(slot).append(':').append(id).append('x').append(stack.getCount()).append(']');
        }
        source.sendSuccess(() -> Component.literal(text.toString()), false);
        return 1;
    }

    private static HumanNpcEntity requireHumanNpc(Entity entity) throws CommandSyntaxException {
        if (entity instanceof HumanNpcEntity npc) {
            return npc;
        }
        throw NOT_HUMAN_NPC.create();
    }
}
