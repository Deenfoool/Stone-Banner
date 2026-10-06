package dev.stonebanner.client.hud;

import dev.stonebanner.client.control.ExcavationOverlayState;
import dev.stonebanner.client.control.StorageSummaryState;
import dev.stonebanner.network.packet.ExcavationPlanSnapshotPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Compact contextual ladder status for narrow vertical excavation plans. */
public final class ExcavationLadderStatusHud {
    private static final ItemStack LADDER_ICON = new ItemStack(Items.LADDER);
    private static final int TEXT = 0xFFF4F1E8;
    private static final int GOOD = 0xFF67C96B;
    private static final int WARNING = 0xFFFFC84A;
    private static final int PANEL = 0xD8181B1E;
    private static final int BORDER = 0xFF4E3D2C;

    private ExcavationLadderStatusHud() {
    }

    public static void render(GuiGraphics graphics, Minecraft minecraft, int screenWidth) {
        if (graphics == null || minecraft == null || minecraft.font == null || minecraft.level == null) {
            return;
        }

        int missing = missingLadders(minecraft);
        if (missing <= 0) {
            return;
        }

        int stock = StorageSummaryState.ladderCount();
        Font font = minecraft.font;
        Component text = Component.translatable("hud.stonebanner.excavation.ladders", missing, stock);
        int width = Math.min(250, Math.max(145, font.width(text) + 34));
        int x = 8;
        int y = 100;

        graphics.fill(x, y, x + width, y + 24, PANEL);
        graphics.renderOutline(x, y, width, 24, BORDER);
        graphics.renderItem(LADDER_ICON, x + 4, y + 4);
        graphics.drawString(font, text, x + 24, y + 8, stock >= missing ? GOOD : WARNING, false);
    }

    static int missingLadders(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null) {
            return 0;
        }
        int missing = 0;
        for (ExcavationPlanSnapshotPacket.PlanSnapshot plan : ExcavationOverlayState.plans()) {
            if (!isNarrowVertical(plan)) {
                continue;
            }
            int total = Math.max(0, plan.max().getY() - plan.min().getY());
            int installed = 0;
            for (int y = plan.max().getY(); y > plan.min().getY(); y--) {
                BlockPos ladder = new BlockPos(plan.min().getX(), y, plan.min().getZ());
                if (minecraft.level.hasChunkAt(ladder)
                        && minecraft.level.getBlockState(ladder).is(Blocks.LADDER)) {
                    installed++;
                }
            }
            missing += Math.max(0, total - installed);
        }
        return missing;
    }

    private static boolean isNarrowVertical(ExcavationPlanSnapshotPacket.PlanSnapshot plan) {
        if (plan == null || plan.modeCode() != 0) {
            return false;
        }
        int sizeX = plan.max().getX() - plan.min().getX() + 1;
        int sizeZ = plan.max().getZ() - plan.min().getZ() + 1;
        return sizeX < 2 || sizeZ < 2;
    }
}
