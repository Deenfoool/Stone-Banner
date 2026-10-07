package dev.stonebanner.gametest;

import com.mojang.authlib.GameProfile;
import dev.stonebanner.citizen.*;
import dev.stonebanner.network.packet.TreatCitizenPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder("stonebanner")
@PrefixGameTestTemplate(false)
public final class CitizenMedicineGameTests {
    @GameTest(template="empty",timeoutTicks=100)
    public static void armourReducesLocalizedDamageAndFallTargetsLegs(GameTestHelper h) {
        var npc=CitizenQueueGameTests.prepare(h);npc.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,new ItemStack(Items.DIAMOND_CHESTPLATE));
        // Vanilla updates equipment attribute modifiers during the next living tick.
        h.runAfterDelay(2, () -> {
            npc.hurt(npc.damageSources().cactus(),6);
            h.assertTrue(npc.getArmorValue()>0&&npc.citizenData().health().injury(BodyPart.TORSO)==InjuryState.WOUNDED,"Pre-armour amount used for body injury; HP="+npc.getHealth());
            npc.invulnerableTime=0;npc.hurt(npc.damageSources().fall(),3);
            h.assertTrue(npc.citizenData().health().injury(BodyPart.LEFT_LEG)!=InjuryState.NORMAL||npc.citizenData().health().injury(BodyPart.RIGHT_LEG)!=InjuryState.NORMAL,"Fall did not damage legs");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void forgeCancelledDamageDoesNotCreateTrauma(GameTestHelper h) {
        var npc=CitizenQueueGameTests.prepare(h);java.util.function.Consumer<net.minecraftforge.event.entity.living.LivingDamageEvent> listener=e->{if(e.getEntity()==npc)e.setCanceled(true);};
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            float hp=npc.getHealth();npc.hurt(npc.damageSources().generic(),6);
            h.assertTrue(npc.getHealth()==hp&&npc.citizenData().health().injury(BodyPart.TORSO)==InjuryState.NORMAL,"Forge cancelled damage still caused trauma");
        } finally { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener); }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void seriousInjuryReleasesJobsAndDoesNotAcceptQueuedOrders(GameTestHelper h) {
        var npc=CitizenQueueGameTests.prepare(h);var target=new BlockPos(4,1,2);h.setBlock(target,net.minecraft.world.level.block.Blocks.OAK_LOG);
        var board=CitizenJobBoard.forLevel(h.getLevel());long id=board.publish(WorkType.FORESTRY,h.absolutePos(target),h.getLevel().getGameTime());
        h.assertTrue(npc.workController().assign(board.job(id).orElseThrow()),"Initial job rejected");npc.hurt(npc.damageSources().generic(),6);
        h.assertTrue(!npc.workController().hasActiveJob()&&!npc.commandController().hasActiveCommand(),"Severe core injury retained job/order");
        h.assertTrue(!npc.commandController().queueMove(h.absolutePos(new BlockPos(5,1,2))),"Treatment preemption bypassed by queue");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void realDamageAbsorptionAndInvulnerability(GameTestHelper h) {
        var npc=CitizenQueueGameTests.prepare(h);
        npc.setInvulnerable(true);npc.hurt(npc.damageSources().generic(),6);
        h.assertTrue(npc.citizenData().health().injury(BodyPart.TORSO)==InjuryState.NORMAL,"Rejected damage created injury");
        npc.setInvulnerable(false);npc.setAbsorptionAmount(8);npc.hurt(npc.damageSources().generic(),6);
        h.assertTrue(npc.citizenData().health().injury(BodyPart.TORSO)==InjuryState.NORMAL,"Absorbed damage created injury");
        npc.invulnerableTime=0;npc.setAbsorptionAmount(0);float before=npc.getHealth();npc.hurt(npc.damageSources().generic(),6);
        h.assertTrue(npc.getHealth()<before&&npc.citizenData().health().injury(BodyPart.TORSO)==InjuryState.HEAVY_WOUND,"Actual HP loss not localized");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void firstAidIsPhysicalAndOwnerOnly(GameTestHelper h) {
        var npc=CitizenQueueGameTests.prepare(h);var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"MedicOwner"));p.setPos(npc.getX(),npc.getY(),npc.getZ());p.getInventory().clearContent();npc.citizenData().setRecruitedBy(p.getUUID());
        npc.citizenData().health().setInjury(BodyPart.HEAD,InjuryState.HEAVY_WOUND);p.getInventory().setItem(0,new ItemStack(MedicalItems.BANDAGE.get(),2));
        var packet=new TreatCitizenPacket(npc.getId(),npc.getUUID(),h.getLevel().dimension().location(),false);
        var stranger=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"MedicGuest"));stranger.setPos(npc.getX(),npc.getY(),npc.getZ());stranger.getInventory().setItem(0,new ItemStack(MedicalItems.BANDAGE.get()));
        h.assertTrue(!TreatCitizenPacket.apply(stranger,packet),"Guest treated foreign NPC");
        h.assertTrue(!TreatCitizenPacket.apply(p,new TreatCitizenPacket(npc.getId(),UUID.randomUUID(),packet.dimension(),false)),"Stale UUID accepted");
        h.assertTrue(!TreatCitizenPacket.apply(p,new TreatCitizenPacket(npc.getId(),npc.getUUID(),net.minecraft.resources.ResourceLocation.parse("minecraft:the_nether"),false)),"Wrong dimension accepted");
        h.assertTrue(TreatCitizenPacket.apply(p,packet)&&p.getInventory().getItem(0).getCount()==1,"Bandage not physically consumed");
        h.assertTrue(!TreatCitizenPacket.apply(p,packet)&&p.getInventory().getItem(0).getCount()==1,"Repeated treatment consumed supplies");
        h.assertTrue(!npc.citizenData().health().isBleeding(),"Dressing did not stop core bleeding");
        npc.citizenData().health().setInjury(BodyPart.RIGHT_ARM,InjuryState.WOUNDED);
        p.interactOn(npc,net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(p.getInventory().getItem(0).isEmpty()&&npc.citizenData().health().recoverySeconds(BodyPart.RIGHT_ARM)==60,"Held medical item interaction did not treat NPC");
        var recipe=h.getLevel().getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse("stonebanner:bandage")).orElseThrow();
        var output=recipe.getResultItem(h.getLevel().registryAccess());
        h.assertTrue(output.is(MedicalItems.BANDAGE.get())&&output.getCount()==2,"Bandage crafting recipe not registered");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void remoteOccludedAndWrongSuppliesRejected(GameTestHelper h) {
        var npc=CitizenQueueGameTests.prepare(h);var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"MedicRange"));p.getInventory().clearContent();npc.citizenData().health().setInjury(BodyPart.LEFT_LEG,InjuryState.FRACTURE);
        p.setPos(npc.getX()+8,npc.getY(),npc.getZ());p.getInventory().setItem(0,new ItemStack(MedicalItems.SPLINT.get(),2));
        h.assertTrue(!CitizenMedicalService.fromInventory(p,npc,true),"Remote treatment accepted");
        p.setPos(npc.getX(),npc.getY(),npc.getZ());h.assertTrue(!CitizenMedicalService.fromInventory(p,npc,false),"Splint treated wound / wrong supply accepted");
        p.setPos(npc.getX()+3,npc.getY(),npc.getZ());h.setBlock(new BlockPos(4,1,2),net.minecraft.world.level.block.Blocks.STONE);h.setBlock(new BlockPos(4,2,2),net.minecraft.world.level.block.Blocks.STONE);
        h.assertTrue(!CitizenMedicalService.fromInventory(p,npc,true),"Treatment through wall accepted");
        p.setPos(npc.getX(),npc.getY(),npc.getZ());h.assertTrue(CitizenMedicalService.fromInventory(p,npc,true),"Nearby splint rejected");h.assertTrue(p.getInventory().getItem(0).getCount()==1,"Rejected care consumed supplies");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=260)
    public static void bleedingRequiresFirstAidAndRestPersists(GameTestHelper h) {
        var npc=CitizenQueueGameTests.prepare(h);npc.citizenData().health().setInjury(BodyPart.TORSO,InjuryState.HEAVY_WOUND);float hp=npc.getHealth();
        h.startSequence().thenExecuteAfter(205,()->{
            h.assertTrue(npc.getHealth()<hp&&npc.brainState()==CitizenBrainState.SLEEP,"Untreated core injury did not bleed / force rest");
            npc.citizenData().health().treat(BodyPart.TORSO,false);
            var tag=new net.minecraft.nbt.CompoundTag();npc.addAdditionalSaveData(tag);npc.readAdditionalSaveData(tag);
            h.assertTrue(npc.citizenData().health().recoverySeconds(BodyPart.TORSO)==120&&!npc.citizenData().health().isBleeding(),"Reload lost treatment");
        }).thenExecuteAfter(20,()->h.assertTrue(npc.citizenData().health().recoverySeconds(BodyPart.TORSO)<120,"Actual resting NPC did not recover")).thenSucceed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void completedTreatmentChangesBodyAndHealsActualHp(GameTestHelper h) {
        var npc=CitizenQueueGameTests.prepare(h);npc.setHealth(10);npc.citizenData().health().setInjury(BodyPart.RIGHT_ARM,InjuryState.WOUNDED);npc.citizenData().health().treat(BodyPart.RIGHT_ARM,false);
        var saved=npc.citizenData().health().save();saved.getCompound("Recovery").putInt("right_arm",1);npc.citizenData().health().load(saved);npc.setBrainState(CitizenBrainState.SLEEP);
        h.runAfterDelay(25,()->{h.assertTrue(npc.citizenData().health().injury(BodyPart.RIGHT_ARM)==InjuryState.NORMAL&&npc.getHealth()==12,"Recovery did not change body and HP together");h.succeed();});
    }
}
