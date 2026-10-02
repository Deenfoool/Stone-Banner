package dev.stonebanner.client.screen;

import dev.stonebanner.client.ClientKeyMappings;
import dev.stonebanner.client.ClientRuntime;
import dev.stonebanner.client.camera.RpgCameraController;
import dev.stonebanner.client.control.CitizenSelectionController;
import dev.stonebanner.client.control.PlayerCommandController;
import dev.stonebanner.client.control.WorldCursor;
import dev.stonebanner.entity.HumanNpcEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;

/** Transparent, non-pausing input layer used while tactical mouse control is active. */
public final class TacticalControlScreen extends Screen {
    private Optional<HitResult> hoveredTarget = Optional.empty();

    public TacticalControlScreen() {
        super(Component.translatable("screen.stonebanner.tactical"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hoveredTarget = WorldCursor.pick(minecraft, mouseX, mouseY, width, height);
        int color = hoveredTarget.map(hit -> hit instanceof EntityHitResult ? 0xFF69DDE7 : 0xFFE7C46A)
                .orElse(0xFFBA4A4A);

        graphics.renderOutline(mouseX - 5, mouseY - 5, 11, 11, color);
        graphics.hLine(mouseX - 8, mouseX - 3, mouseY, color);
        graphics.hLine(mouseX + 3, mouseX + 8, mouseY, color);
        graphics.vLine(mouseX, mouseY - 8, mouseY - 3, color);
        graphics.vLine(mouseX, mouseY + 3, mouseY + 8, color);

        Component hint;
        int hintColor;
        HumanNpcEntity selectedNpc = CitizenSelectionController.selected().orElse(null);
        if (selectedNpc != null) {
            hint = Component.translatable(
                    "hud.stonebanner.tactical.npc_selected",
                    selectedNpc.getDisplayName(),
                    selectedNpc.brainState().serializedName()
            );
            hintColor = 0xFF8CE673;
        } else if (PlayerCommandController.status() == PlayerCommandController.CommandStatus.UNREACHABLE) {
            hint = Component.translatable("hud.stonebanner.tactical.unreachable");
            hintColor = 0xFFFF6868;
        } else if (PlayerCommandController.hasMoveTarget()) {
            hint = Component.translatable(
                    "hud.stonebanner.tactical.moving",
                    PlayerCommandController.pathSnapshot().size()
            );
            hintColor = 0xFFE7C46A;
        } else if (PlayerCommandController.status() == PlayerCommandController.CommandStatus.TARGET_SELECTED) {
            hint = PlayerCommandController.selectedEntity()
                    .map(entity -> Component.translatable(
                            "hud.stonebanner.tactical.selected",
                            entity.getDisplayName()
                    ))
                    .orElse(Component.translatable("hud.stonebanner.tactical.hint"));
            hintColor = 0xFF69DDE7;
        } else {
            hint = Component.translatable("hud.stonebanner.tactical.hint");
            hintColor = 0xFFE7C46A;
        }
        graphics.drawCenteredString(font, hint, width / 2, height - 24, hintColor);

        hoveredTarget.ifPresent(hit -> {
            Component targetLabel = hit instanceof EntityHitResult entityHit
                    ? Component.translatable("hud.stonebanner.tactical.entity", entityHit.getEntity().getDisplayName())
                    : Component.translatable(
                            "hud.stonebanner.tactical.block",
                            ((BlockHitResult) hit).getBlockPos().getX(),
                            ((BlockHitResult) hit).getBlockPos().getY(),
                            ((BlockHitResult) hit).getBlockPos().getZ()
                    );
            graphics.drawString(font, targetLabel, 8, 8, 0xFFFFFFFF);
        });
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            hoveredTarget = WorldCursor.pick(minecraft, mouseX, mouseY, width, height);
            hoveredTarget.ifPresent(hit -> {
                if (hit instanceof EntityHitResult entityHit) {
                    if (CitizenSelectionController.select(entityHit.getEntity())) {
                        PlayerCommandController.stop();
                    } else {
                        CitizenSelectionController.clear();
                        PlayerCommandController.contextAction(entityHit.getEntity());
                    }
                } else {
                    BlockHitResult blockHit = (BlockHitResult) hit;
                    if (!CitizenSelectionController.moveSelected(blockHit)) {
                        PlayerCommandController.moveTo(blockHit);
                    }
                }
            });
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (!CitizenSelectionController.stopAndClear()) {
                PlayerCommandController.stop();
            }
            return true;
        }
        return button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            RpgCameraController.rotateByMouseDrag(dragX, dragY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        RpgCameraController.adjustZoom(delta);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            minecraft.setScreen(new PauseScreen(true));
            return true;
        }
        if (ClientKeyMappings.CYCLE_CONTROL_MODE.matches(keyCode, scanCode)) {
            ClientRuntime.cycleControlMode(minecraft);
            return true;
        }
        if (minecraft.options.keyInventory.matches(keyCode, scanCode) && minecraft.player != null) {
            minecraft.setScreen(new InventoryScreen(minecraft.player));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
