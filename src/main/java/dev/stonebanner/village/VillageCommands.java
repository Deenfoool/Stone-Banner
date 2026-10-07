package dev.stonebanner.village;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.stonebanner.StoneAndBanner;
import net.minecraft.commands.Commands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid=StoneAndBanner.MOD_ID)
public final class VillageCommands {
    @SubscribeEvent public static void commands(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("sbvillage").requires(s->s.getEntity() instanceof net.minecraft.server.level.ServerPlayer)
            .then(Commands.literal("journal").executes(c->{VillageJournalService.open(c.getSource().getPlayerOrException());return 1;}))
            .then(Commands.literal("recruitment").executes(c->{VillageRecruitmentService.open(c.getSource().getPlayerOrException(),VillageRecruitmentService.NONE,false);return 1;})
                .then(Commands.argument("village",StringArgumentType.word()).executes(c->{
                    try{VillageRecruitmentService.open(c.getSource().getPlayerOrException(),UUID.fromString(StringArgumentType.getString(c,"village")),false);return 1;}
                    catch(IllegalArgumentException ex){VillageService.feedback(c.getSource().getPlayerOrException(),VillageService.Result.INVALID);return 0;}
                })))
            .then(Commands.literal("manage").executes(c->{VillageRecruitmentService.open(c.getSource().getPlayerOrException(),VillageRecruitmentService.NONE,true);return 1;}))
            .then(Commands.literal("contracts").executes(c->{VillageReturnService.list(c.getSource().getPlayerOrException());return 1;}))
            .then(Commands.literal("dismiss").then(Commands.argument("village",StringArgumentType.word())
                .then(Commands.argument("npc",StringArgumentType.word()).executes(c->execute(c,"dismiss")))))
            .then(Commands.literal("accept").then(Commands.argument("village",StringArgumentType.word())
                .then(Commands.argument("quest",StringArgumentType.word()).executes(c->execute(c,"accept")))))
            .then(Commands.literal("submit").then(Commands.argument("village",StringArgumentType.word())
                .then(Commands.argument("quest",StringArgumentType.word()).then(Commands.argument("npc",StringArgumentType.word()).executes(c->execute(c,"submit"))))))
            .then(Commands.literal("hire").then(Commands.argument("village",StringArgumentType.word())
                .then(Commands.argument("npc",StringArgumentType.word()).then(Commands.argument("mode",StringArgumentType.word()).executes(c->execute(c,"hire")))))));
    }
    private static int execute(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> c,String action) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var p=c.getSource().getPlayerOrException();VillageService.Result result;
        try{
            UUID id=UUID.fromString(StringArgumentType.getString(c,"village"));
            if(action.equals("dismiss"))result=VillageReturnService.dismiss(p,id,UUID.fromString(StringArgumentType.getString(c,"npc")));
            else if(action.equals("hire")){
                String mode=StringArgumentType.getString(c,"mode");
                result=mode.equals("companion")||mode.equals("settler")?VillageService.hire(p,id,UUID.fromString(StringArgumentType.getString(c,"npc")),mode.equals("settler")):VillageService.Result.INVALID;
            }else{
                var quest=VillageData.QuestType.valueOf(StringArgumentType.getString(c,"quest").toUpperCase(Locale.ROOT));
                result=action.equals("accept")?VillageService.accept(p,id,quest):VillageService.submit(p,id,quest,UUID.fromString(StringArgumentType.getString(c,"npc")));
            }
        }catch(IllegalArgumentException ex){result=VillageService.Result.INVALID;}
        VillageService.feedback(p,result);return result==VillageService.Result.OK?1:0;
    }
}
