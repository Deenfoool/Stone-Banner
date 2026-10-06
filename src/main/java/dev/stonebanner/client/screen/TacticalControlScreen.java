package dev.stonebanner.client.screen;

import dev.stonebanner.client.ClientKeyMappings;
import dev.stonebanner.client.ClientRuntime;
import dev.stonebanner.client.camera.RpgCameraController;
import dev.stonebanner.client.control.CitizenSelectionController;
import dev.stonebanner.client.control.DesignationController;
import dev.stonebanner.client.control.GameSpeedController;
import dev.stonebanner.client.control.PlayerCommandController;
import dev.stonebanner.client.control.WorldCursor;
import dev.stonebanner.client.hud.StoneBannerHudRenderer;
import dev.stonebanner.designation.DesignationType;
import dev.stonebanner.entity.HumanNpcEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
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
        if (DesignationController.isActive() && DesignationController.dragStart().isPresent()) {
            hoveredTarget.filter(BlockHitResult.class::isInstance)
                    .map(BlockHitResult.class::cast)
                    .ifPresent(hit -> DesignationController.update(hit.getBlockPos()));
        }

        StoneBannerHudRenderer.render(graphics, minecraft, width, height);

        int color = cursorColor();
        graphics.renderOutline(mouseX - 5, mouseY - 5, 11, 11, color);
        graphics.hLine(mouseX - 8, mouseX - 3, mouseY, color);
        graphics.hLine(mouseX + 3, mouseX + 8, mouseY, color);
        graphics.vLine(mouseX, mouseY - 8, mouseY - 3, color);
        graphics.vLine(mouseX, mouseY + 3, mouseY + 8, color);

        hoveredTarget.ifPresent(hit -> {
            Component targetLabel = hit instanceof EntityHitResult entityHit
                    ? Component.translatable("hud.stonebanner.tactical.entity", entityHit.getEntity().getDisplayName())
                    : Component.translatable(
                            "hud.stonebanner.tactical.block",
                            ((BlockHitResult) hit).getBlockPos().getX(),
                            ((BlockHitResult) hit).getBlockPos().getY(),
                            ((BlockHitResult) hit).getBlockPos().getZ()
                    );
            graphics.drawString(font, targetLabel, 8, 58, 0xFFD8D2C8);
        });

        DesignationController.previewDimensions().ifPresent(dimensions -> graphics.drawString(
                font,
                Component.translatable(
                        "hud.stonebanner.designation.selection_size",
                        dimensions.sizeX(),
                        dimensions.sizeY(),
                        dimensions.sizeZ(),
                        dimensions.volume()
                ),
                8,
                70,
                DesignationController.previewAllowed() ? 0xFFE7C46A : 0xFFFF6868
        ));
    }

    private int cursorColor() {
        DesignationType type = DesignationController.activeType().orElse(null);
        if (type != null) {
            return DesignationController.previewAllowed() ? designationColor(type) : 0xFFFF6868;
        }
        return hoveredTarget.map(hit -> hit instanceof EntityHitResult ? 0xFF69DDE7 : 0xFFE7C46A)
                .orElse(0xFFBA4A4A);
    }

    private static int designationColor(DesignationType type) {
        return switch (type) {
            case CHOP -> 0xFF79D46C;
            case MINE -> 0xFFE2B85C;
            case EXCAVATE -> 0xFFDB8438;
            case TUNNEL -> 0xFFAD7AF0;
            case CLEAR -> 0xFF8ED9C3;
            case CANCEL -> 0xFFFF6868;
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            HumanNpcEntity selected = CitizenSelectionController.selected().orElse(null);
            StoneBannerHudRenderer.HudAction hudAction = StoneBannerHudRenderer.actionAt(
                    mouseX, mouseY, width, height, selected != null
            );
            switch (hudAction) {
                case CONSUME -> {
                    return true;
                }
                case TAB_BUILD -> {
                    selectManagementTab(0);
                    return true;
                }
                case TAB_ORDERS -> {
                    selectManagementTab(1);
                    return true;
                }
                case TAB_ZONES -> {
                    selectManagementTab(2);
                    return true;
                }
                case TAB_RESEARCH -> {
                    selectManagementTab(3);
                    return true;
                }
                case TAB_CRAFTING -> {
                    selectManagementTab(4);
                    return true;
                }
                case TOGGLE_BOTTOM_DOCK -> {
                    StoneBannerHudRenderer.toggleBottomDock();
                    return true;
                }
                case TOGGLE_CITIZEN -> {
                    StoneBannerHudRenderer.toggleCitizenPanel();
                    return true;
                }
                case OPEN_CITIZEN_OVERVIEW -> {
                    openCitizen(selected, CitizenDetailsScreen.Tab.OVERVIEW);
                    return true;
                }
                case OPEN_CITIZEN_HEALTH -> {
                    openCitizen(selected, CitizenDetailsScreen.Tab.HEALTH);
                    return true;
                }
                case OPEN_CITIZEN_SKILLS -> {
                    openCitizen(selected, CitizenDetailsScreen.Tab.SKILLS);
                    return true;
                }
                case OPEN_WORK -> {
                    openCitizen(selected, CitizenDetailsScreen.Tab.WORK);
                    return true;
                }
                case OPEN_CITIZEN_INVENTORY -> {
                    openCitizen(selected, CitizenDetailsScreen.Tab.INVENTORY);
                    return true;
                }
                case DESIGNATE_CHOP -> {
                    activateDesignation(DesignationType.CHOP);
                    return true;
                }
                case DESIGNATE_MINE -> {
                    activateDesignation(DesignationType.MINE);
                    return true;
                }
                case DESIGNATE_EXCAVATE -> {
                    activateDesignation(DesignationType.EXCAVATE);
                    return true;
                }
                case DESIGNATE_TUNNEL -> {
                    activateDesignation(DesignationType.TUNNEL);
                    return true;
                }
                case DESIGNATE_CLEAR -> {
                    activateDesignation(DesignationType.CLEAR);
                    return true;
                }
                case DESIGNATE_CANCEL -> {
                    activateDesignation(DesignationType.CANCEL);
                    return true;
                }
                case HOTBAR_1, HOTBAR_2, HOTBAR_3, HOTBAR_4, HOTBAR_5,
                     HOTBAR_6, HOTBAR_7, HOTBAR_8, HOTBAR_9 -> {
                    selectHotbarSlot(StoneBannerHudRenderer.hotbarIndex(hudAction));
                    return true;
                }
                case TIME_PAUSE -> {
                    GameSpeedController.setSpeed(GameSpeedController.Speed.PAUSED);
                    return true;
                }
                case TIME_NORMAL -> {
                    GameSpeedController.setSpeed(GameSpeedController.Speed.NORMAL);
                    return true;
                }
                case TIME_DOUBLE -> {
                    GameSpeedController.setSpeed(GameSpeedController.Speed.DOUBLE);
                    return true;
                }
                case TIME_TRIPLE -> {
                    GameSpeedController.setSpeed(GameSpeedController.Speed.TRIPLE);
                    return true;
                }
                case NONE -> {
                }
            }
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && DesignationController.isActive()) {
            hoveredTarget = WorldCursor.pick(minecraft, mouseX, mouseY, width, height);
            hoveredTarget.filter(BlockHitResult.class::isInstance)
                    .map(BlockHitResult.class::cast)
                    .ifPresent(hit -> DesignationController.begin(hit.getBlockPos()));
            return true;
        }

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
            if (DesignationController.isActive()) {
                if (DesignationController.dragStart().isPresent()) {
                    DesignationController.cancelDrag();
                } else {
                    DesignationController.deactivate();
                }
                return true;
            }
            if (!CitizenSelectionController.stopAndClear()) {
                PlayerCommandController.stop();
            }
            return true;
        }
        return button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE || super.mouseClicked(mouseX, mouseY, button);
    }

    private void openCitizen(HumanNpcEntity selected, CitizenDetailsScreen.Tab tab) {
        if (selected == null) return;
        minecraft.setScreen(new CitizenDetailsScreen(this, selected.getId(), tab));
    }

    private void selectManagementTab(int index) {
        StoneBannerHudRenderer.selectBottomTab(index);
        if (index != 1) {
            DesignationController.deactivate();
        }
    }

    private static void activateDesignation(DesignationType type) {
        StoneBannerHudRenderer.selectBottomTab(1);
        DesignationController.activate(type);
        CitizenSelectionController.clear();
        PlayerCommandController.stop();
    }

    private void selectHotbarSlot(int slot) {
        if (slot < 0 || slot >= 9 || minecraft.player == null) return;
        minecraft.player.getInventory().selected = slot;
        minecraft.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && DesignationController.isActive()) {
            hoveredTarget = WorldCursor.pick(minecraft, mouseX, mouseY, width, height);
            BlockHitResult hit = hoveredTarget.filter(BlockHitResult.class::isInstance)
                    .map(BlockHitResult.class::cast)
                    .orElse(null);
            if (hit != null) {
                DesignationController.finish(hit.getBlockPos());
            } else {
                DesignationController.cancelDrag();
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && DesignationController.isActive()) {
            hoveredTarget = WorldCursor.pick(minecraft, mouseX, mouseY, width, height);
            hoveredTarget.filter(BlockHitResult.class::isInstance)
                    .map(BlockHitResult.class::cast)
                    .ifPresent(hit -> DesignationController.update(hit.getBlockPos()));
            return true;
        }
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
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            selectHotbarSlot(keyCode - GLFW.GLFW_KEY_1);
            return true;
        }
        if (ClientKeyMappings.CYCLE_CONTROL_MODE.matches(keyCode, scanCode)) {
            DesignationController.deactivate();
            ClientRuntime.cycleControlMode(minecraft);
            return true;
        }
        if (ClientKeyMappings.CYCLE_DESIGNATION_MODE.matches(keyCode, scanCode)) {
            StoneBannerHudRenderer.selectBottomTab(1);
            DesignationController.cycleMode();
            CitizenSelectionController.clear();
            PlayerCommandController.stop();
            return true;
        }
        if (minecraft.options.keyInventory.matches(keyCode, scanCode) && minecraft.player != null) {
            minecraft.setScreen(new InventoryScreen(minecraft.player));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
