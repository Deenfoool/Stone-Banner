package dev.stonebanner.production;

import com.mojang.brigadier.arguments.*;
import dev.stonebanner.StoneAndBanner;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.Locale;

@Mod.EventBusSubscriber(modid=StoneAndBanner.MOD_ID)
public final class ProductionCommands {
    @SubscribeEvent public static void register(RegisterCommandsEvent e){
        var root=Commands.literal("sbproduction").requires(s->s.getEntity() instanceof ServerPlayer);
        root.then(Commands.literal("menu").executes(c->{var p=c.getSource().getPlayerOrException();ProductionService.reconcile(p.serverLevel());dev.stonebanner.network.StoneBannerNetwork.sendProduction(p,true);return 1;}));
        root.then(Commands.literal("refresh").executes(c->{var p=c.getSource().getPlayerOrException();ProductionService.reconcile(p.serverLevel());dev.stonebanner.network.StoneBannerNetwork.sendProduction(p,false);return 1;}));
        root.then(Commands.literal("farm").then(Commands.argument("crop",StringArgumentType.word())
                .suggests((c,b)->SharedSuggestionProvider.suggest(java.util.Arrays.stream(FarmCrop.values()).map(v->v.name().toLowerCase(Locale.ROOT)),b))
                .then(Commands.argument("from",BlockPosArgument.blockPos()).then(Commands.argument("to",BlockPosArgument.blockPos()).executes(c->{
                    var p=c.getSource().getPlayerOrException();var a=BlockPosArgument.getLoadedBlockPos(c,"from");var b=BlockPosArgument.getLoadedBlockPos(c,"to");
                    FarmCrop crop;try{crop=FarmCrop.valueOf(StringArgumentType.getString(c,"crop").toUpperCase(Locale.ROOT));}catch(IllegalArgumentException ex){return reject(c.getSource());}
                    if(!canConfigure(p,a,64)||!canConfigure(p,b,64))return reject(c.getSource());
                    long id=ProductionData.forLevel(p.serverLevel()).addField(p.getUUID(),a,b,crop);if(id<0)return reject(c.getSource());
                    ProductionService.reconcile(p.serverLevel());c.getSource().sendSuccess(()->Component.translatable("production.stonebanner.created",id),false);return 1;
                })))));
        root.then(Commands.literal("bill").then(Commands.argument("station",BlockPosArgument.blockPos()).then(Commands.argument("recipe",ResourceLocationArgument.id())
                .suggests((c,b)->SharedSuggestionProvider.suggestResource(c.getSource().getLevel().getRecipeManager().getRecipeIds(),b))
                .then(Commands.argument("mode",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(new String[]{"make","maintain"},b))
                .then(Commands.argument("amount",IntegerArgumentType.integer(1,4096)).executes(c->{
                    var p=c.getSource().getPlayerOrException();var station=BlockPosArgument.getLoadedBlockPos(c,"station");var id=ResourceLocationArgument.getId(c,"recipe");
                    if(!canConfigure(p,station,8)||!ProductionService.station(p.serverLevel(),station))return reject(c.getSource());
                    var recipe=p.serverLevel().getRecipeManager().byKey(id).orElse(null);
                    if(!(recipe instanceof net.minecraft.world.item.crafting.CraftingRecipe crafting)||!CraftingPlan.supported(crafting)||!ProductionService.stationAccepts(p.serverLevel(),station,crafting))return reject(c.getSource());
                    ProductionData.Mode mode;try{mode=ProductionData.Mode.valueOf(StringArgumentType.getString(c,"mode").toUpperCase(Locale.ROOT));}catch(IllegalArgumentException ex){return reject(c.getSource());}
                    long bill=ProductionData.forLevel(p.serverLevel()).addBill(p.getUUID(),station,id,mode,IntegerArgumentType.getInteger(c,"amount"));if(bill<0)return reject(c.getSource());
                    ProductionService.reconcile(p.serverLevel());c.getSource().sendSuccess(()->Component.translatable("production.stonebanner.created",bill),false);return 1;
                }))))));
        root.then(Commands.literal("list").executes(c->{
            var p=c.getSource().getPlayerOrException();var data=ProductionData.forLevel(p.serverLevel());
            for(var f:data.fields())if(f.owner.equals(p.getUUID()))p.sendSystemMessage(Component.literal("#"+f.id+" "+f.crop+" "+f.min.toShortString()+" -> "+f.max.toShortString()+" ").append(Component.translatable(f.paused?"production.stonebanner.paused":"production.stonebanner.ready")));
            for(var b:data.bills())if(b.owner.equals(p.getUUID()))p.sendSystemMessage(Component.literal("#"+b.id+" "+b.recipe+" "+b.mode+" "+b.made+"/"+b.amount+" @ "+b.station.toShortString()+" ").append(Component.translatable("production.stonebanner."+(b.paused?"paused":b.status))));return 1;
        }));
        for(String action:new String[]{"pause","resume","remove","fertilize"})root.then(Commands.literal(action).then(Commands.argument("id",LongArgumentType.longArg(1)).executes(c->{
            var p=c.getSource().getPlayerOrException();if(!p.isAlive()||p.isSpectator()||!ProductionData.forLevel(p.serverLevel()).edit(p.getUUID(),LongArgumentType.getLong(c,"id"),action))return reject(c.getSource());
            ProductionService.reconcile(p.serverLevel());c.getSource().sendSuccess(()->Component.translatable("production.stonebanner.updated"),false);return 1;
        })));
        root.then(Commands.literal("update").then(Commands.argument("id",LongArgumentType.longArg(1))
                .then(Commands.argument("mode",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(new String[]{"make","maintain"},b))
                .then(Commands.argument("amount",IntegerArgumentType.integer(1,4096)).executes(c->{
                    var p=c.getSource().getPlayerOrException();
                    ProductionData.Mode mode;try{mode=ProductionData.Mode.valueOf(StringArgumentType.getString(c,"mode").toUpperCase(Locale.ROOT));}catch(IllegalArgumentException ex){return reject(c.getSource());}
                    if(!p.isAlive()||p.isSpectator()||!ProductionData.forLevel(p.serverLevel()).updateBill(p.getUUID(),LongArgumentType.getLong(c,"id"),mode,IntegerArgumentType.getInteger(c,"amount")))return reject(c.getSource());
                    return updated(c.getSource(),p);
                })))));
        for(String action:new String[]{"up","down"})root.then(Commands.literal(action).then(Commands.argument("id",LongArgumentType.longArg(1)).executes(c->{
            var p=c.getSource().getPlayerOrException();
            if(!p.isAlive()||p.isSpectator()||!ProductionData.forLevel(p.serverLevel()).moveBill(p.getUUID(),LongArgumentType.getLong(c,"id"),action.equals("up")?-1:1))return reject(c.getSource());
            return updated(c.getSource(),p);
        })));
        e.getDispatcher().register(root);
    }
    private static int updated(CommandSourceStack source,ServerPlayer player){
        ProductionService.reconcile(player.serverLevel());source.sendSuccess(()->Component.translatable("production.stonebanner.updated"),false);return 1;
    }
    public static boolean canConfigure(ServerPlayer p,BlockPos pos,int range){return p.isAlive()&&!p.isSpectator()&&p.serverLevel().hasChunkAt(pos)&&p.serverLevel().getWorldBorder().isWithinBounds(pos)&&pos.getY()>=p.level().getMinBuildHeight()&&pos.getY()<p.level().getMaxBuildHeight()&&p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos))<=range*range;}
    private static int reject(CommandSourceStack source){source.sendFailure(Component.translatable("production.stonebanner.rejected"));return 0;}
}
