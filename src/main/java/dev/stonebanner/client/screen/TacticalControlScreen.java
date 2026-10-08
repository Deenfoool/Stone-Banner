package dev.stonebanner.client.screen;

import dev.stonebanner.client.hud.OreDiscoveryHud;
import dev.stonebanner.client.ClientKeyMappings;
import dev.stonebanner.client.ClientRuntime;
import dev.stonebanner.client.camera.RpgCameraController;
import dev.stonebanner.client.control.CitizenSelectionController;
import dev.stonebanner.client.control.DesignationController;
import dev.stonebanner.client.control.GameSpeedController;
import dev.stonebanner.client.control.PlayerCommandController;
import dev.stonebanner.client.control.TunnelExtensionController;
import dev.stonebanner.client.control.WorldCursor;
import dev.stonebanner.client.hud.ExcavationLadderStatusHud;
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

/** Transparent input layer for both hero profiles and the separate group-order mode. */
public final class TacticalControlScreen extends Screen {
    private double cursorX, cursorY, dragStartX, dragStartY;
    private boolean selecting, selectionMoved;
    private static boolean commands() { return dev.stonebanner.client.control.HeroInputController.commandMode(); }
    private boolean overUi(double x,double y) { return StoneBannerHudRenderer.actionAt(x,y,width,height,CitizenSelectionController.hasSelection()) != StoneBannerHudRenderer.HudAction.NONE; }
    public double[] edgePan() {
        if (!dev.stonebanner.config.ClientConfig.CAMERA_EDGE_PAN.get()) return new double[]{0,0};
        if (!commands() || overUi(cursorX,cursorY) || selecting || org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(minecraft.getWindow().getWindow(),GLFW.GLFW_FOCUSED)!=1) return new double[]{0,0};
        return dev.stonebanner.control.ScreenInputRules.edgePan(cursorX,cursorY,width,height,10);
    }
    @Override protected void init() { cursorX=width*.5;cursorY=height*.5; }
    @Override public void tick() {
        if(dev.stonebanner.client.control.ConstructionPreviewController.active())return;
        dev.stonebanner.client.control.HeroInputController.tick(
                WorldCursor.pick(minecraft,cursorX,cursorY,width,height).orElse(null), overUi(cursorX,cursorY),
                WorldCursor.pick(minecraft,cursorX,cursorY,width,height,true).orElse(null));
    }
    @Override public void removed() { dev.stonebanner.client.control.HeroInputController.cancel();dev.stonebanner.client.control.ConstructionPreviewController.cancel(); }
    private Optional<HitResult> hoveredTarget = Optional.empty();

    public HitResult hoveredHit(){return hoveredTarget.orElse(null);}
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
        cursorX=mouseX; cursorY=mouseY;
        hoveredTarget = overUi(mouseX,mouseY)?Optional.empty():WorldCursor.pick(minecraft, mouseX, mouseY, width, height);
        dev.stonebanner.client.control.ConstructionPreviewController.update(hoveredTarget.orElse(null));
        if (DesignationController.isActive() && DesignationController.hasSelectionInProgress()) {
            hoveredTarget.filter(BlockHitResult.class::isInstance)
                    .map(BlockHitResult.class::cast)
                    .ifPresent(hit -> DesignationController.updatePreview(hit.getBlockPos()));
        }
        if (TunnelExtensionController.isActive() && TunnelExtensionController.hasSelectedPlan()) {
            hoveredTarget.filter(BlockHitResult.class::isInstance)
                    .map(BlockHitResult.class::cast)
                    .ifPresent(hit -> TunnelExtensionController.updatePreview(hit.getBlockPos()));
        }

        StoneBannerHudRenderer.render(graphics, minecraft, width, height);
        graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(commands()?"hud.stonebanner.commands.controls":"hud.stonebanner.hero.controls").getString(), width - 16), 8, 96, 0xFFD8D2C8);
        if(dev.stonebanner.client.control.ConstructionPreviewController.active()){
            graphics.drawString(font,Component.translatable("construction.stonebanner.ui.preview"),8,110,0xFFE7C46A);
            graphics.drawString(font,Component.translatable("construction.stonebanner.status."+dev.stonebanner.client.control.ConstructionPreviewController.status()),8,122,0xFFD8D2C8);
        }
        ExcavationLadderStatusHud.render(graphics, minecraft, width);

        OreDiscoveryHud.render(graphics, minecraft, width, height);
        dev.stonebanner.client.hud.DebugOverlay.render(graphics, minecraft, width, height);
        if(selecting && selectionMoved) graphics.renderOutline((int)Math.min(dragStartX,mouseX),(int)Math.min(dragStartY,mouseY),(int)Math.abs(mouseX-dragStartX)+1,(int)Math.abs(mouseY-dragStartY)+1,0xFF69DDE7);
        var selectedCitizens = CitizenSelectionController.selectedAll();
        graphics.drawString(font,Component.translatable("hud.stonebanner.selection_count", selectedCitizens.size(),
                selectedCitizens.stream().mapToInt(HumanNpcEntity::hudQueuedMoves).sum()),8,108,0xFF69DDE7);
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
            if (hit instanceof BlockHitResult block && minecraft.level != null
                    && minecraft.level.getBlockState(block.getBlockPos()).getBlock() instanceof net.minecraft.world.level.block.BannerBlock)
                targetLabel = Component.translatable("community.stonebanner.tactical_hint");
            graphics.drawString(font, targetLabel, 8, 58, 0xFFD8D2C8);
        });

        if (DesignationController.isActive()) {
            Component step = Component.translatable(
                    "hud.stonebanner.designation.selection_step",
                    DesignationController.completedClicks() + 1,
                    Component.translatable(
                            "hud.stonebanner.designation.step." + DesignationController.nextStep().serializedName()
                    )
            );
            graphics.drawString(font, step, 8, 70, 0xFFE7C46A);
        }

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
                DesignationController.isActive() ? 82 : 70,
                DesignationController.previewAllowed() ? 0xFFE7C46A : 0xFFFF6868
        ));

        if (DesignationController.activeType().orElse(null) == DesignationType.EXCAVATE) {
            graphics.drawString(
                    font,
                    Component.translatable(
                            "hud.stonebanner.excavation.access",
                            Component.translatable(
                                    "excavation_access.stonebanner."
                                            + DesignationController.excavationAccessMode().serializedName()
                            )
                    ),
                    8,
                    94,
                    0xFFD8D2C8,
                    false
            );
        }

        if (TunnelExtensionController.isActive()) {
            Component extensionHint;
            int extensionColor;
            if (TunnelExtensionController.hasSelectedPlan()) {
                long planId = TunnelExtensionController.selectedPlan().map(plan -> plan.id()).orElse(0L);
                extensionHint = Component.translatable(
                        "hud.stonebanner.excavation.extend.endpoint",
                        planId,
                        TunnelExtensionController.previewLength()
                );
                extensionColor = TunnelExtensionController.previewAllowed() ? 0xFFE7C46A : 0xFFFF6868;
            } else {
                extensionHint = Component.translatable("hud.stonebanner.excavation.extend.select_plan");
                extensionColor = 0xFFE7C46A;
            }
            graphics.drawString(font, extensionHint, 8, 70, extensionColor, false);
        }
        if (dev.stonebanner.client.control.MapLayerState.enabled(dev.stonebanner.client.control.MapLayerState.Layer.RESOURCES)
                && !DesignationController.isActive() && !TunnelExtensionController.isActive()) {
            var snapshot = dev.stonebanner.client.control.MapLayerState.snapshot();
            if (snapshot != null) hoveredTarget.filter(BlockHitResult.class::isInstance).map(BlockHitResult.class::cast).ifPresent(hit -> {
                int cx = hit.getBlockPos().getX() >> 4, cz = hit.getBlockPos().getZ() >> 4;
                snapshot.tiles().stream().filter(tile -> tile.x() == cx && tile.z() == cz).findFirst().ifPresent(tile -> {
                    var labels = dev.stonebanner.client.control.MapLayerOverlay.resourceLabels(tile);
                    int panelWidth = Math.min(280, width - 16);
                    graphics.fill(6, 108, 6 + panelWidth, 130 + labels.size() * 11, 0xD0101518);
                    graphics.drawString(font, Component.translatable("geology.stonebanner.chunk", cx, cz), 10, 112, 0xFFE7C46A);
                    int row = 126;
                    for (var label : labels) {
                        graphics.drawString(font, font.plainSubstrByWidth(label.getString(), panelWidth - 8), 10, row, 0xFFD8D2C8);
                        row += 11;
                    }
                });
            });
        }
        net.minecraft.client.KeyMapping[] layerKeys = {ClientKeyMappings.LAYER_BOUNDARIES, ClientKeyMappings.LAYER_RESOURCES, ClientKeyMappings.LAYER_FERTILITY};
        for (int i = 0; i < 3; i++) {
            var button = dev.stonebanner.client.hud.StoneBannerHudLayout.layerButton(width, height, i);
            if (button.contains(mouseX, mouseY)) graphics.renderTooltip(font,
                    Component.translatable("geology.stonebanner.layer_tooltip",
                            Component.translatable(dev.stonebanner.client.control.MapLayerState.Layer.values()[i].key()),
                            layerKeys[i].getTranslatedKeyMessage()), mouseX, mouseY);
        }
        if (!DesignationController.isActive() && !TunnelExtensionController.isActive()
                && dev.stonebanner.client.control.MapLayerState.enabled(dev.stonebanner.client.control.MapLayerState.Layer.RESOURCES))
            graphics.drawString(font, Component.translatable("geology.stonebanner.survey_hint"), 8, 84, 0xFFE7C46A);

    }

    private int cursorColor() {
        if (TunnelExtensionController.isActive()) {
            return !TunnelExtensionController.hasSelectedPlan() || TunnelExtensionController.previewAllowed()
                    ? 0xFFAD7AF0
                    : 0xFFFF6868;
        }
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
        if(dev.stonebanner.client.control.ConstructionPreviewController.active()){
            if(button==GLFW.GLFW_MOUSE_BUTTON_RIGHT)dev.stonebanner.client.control.ConstructionPreviewController.cancel();
            else if(button==GLFW.GLFW_MOUSE_BUTTON_LEFT&&!overUi(mouseX,mouseY)){
                dev.stonebanner.client.control.ConstructionPreviewController.update(WorldCursor.pick(minecraft,mouseX,mouseY,width,height).orElse(null));
                dev.stonebanner.client.control.ConstructionPreviewController.confirm();
            }
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && RpgCameraController.hasFocus()) {
            RpgCameraController.clearFocus();
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT
                && StoneBannerHudRenderer.actionAt(mouseX, mouseY, width, height, CitizenSelectionController.hasSelection())
                != StoneBannerHudRenderer.HudAction.NONE) return true;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (OreDiscoveryHud.click(mouseX, mouseY, width)) return true;
            if (RpgCameraController.hasFocus()) { RpgCameraController.clearFocus(); return true; }
            HumanNpcEntity selected = CitizenSelectionController.selected().orElse(null);
            StoneBannerHudRenderer.HudAction hudAction = StoneBannerHudRenderer.actionAt(
                    mouseX, mouseY, width, height, selected != null
            );
            switch (hudAction) {
                case CONSUME -> {
                    return true;
                }
                case TAB_BUILD -> {
                    selectManagementTab(0);ConstructionScreen.requestOpen();
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
                case CYCLE_EXCAVATION_ACCESS -> {
                    DesignationController.cycleExcavationAccessMode();
                    return true;
                }
                case EXTEND_TUNNEL -> {
                    activateTunnelExtension();
                    return true;
                }
                case HOTBAR_1, HOTBAR_2, HOTBAR_3, HOTBAR_4, HOTBAR_5,
                     HOTBAR_6, HOTBAR_7, HOTBAR_8, HOTBAR_9 -> {
                    selectHotbarSlot(StoneBannerHudRenderer.hotbarIndex(hudAction));
                    return true;
                }
                case LAYER_BOUNDARIES, LAYER_RESOURCES, LAYER_FERTILITY -> {
                    var layer = switch (hudAction) {
                        case LAYER_BOUNDARIES -> dev.stonebanner.client.control.MapLayerState.Layer.BOUNDARIES;
                        case LAYER_RESOURCES -> dev.stonebanner.client.control.MapLayerState.Layer.RESOURCES;
                        default -> dev.stonebanner.client.control.MapLayerState.Layer.FERTILITY;
                    };
                    dev.stonebanner.client.control.MapLayerState.toggle(layer);
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
            BlockHitResult hit = hoveredTarget.filter(BlockHitResult.class::isInstance)
                    .map(BlockHitResult.class::cast)
                    .orElse(null);
            if (hit != null) {
                DesignationController.updatePreview(hit.getBlockPos());
                DesignationController.click(hit.getBlockPos());
            }
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && TunnelExtensionController.isActive()) {
            hoveredTarget = WorldCursor.pick(minecraft, mouseX, mouseY, width, height);
            BlockHitResult hit = hoveredTarget.filter(BlockHitResult.class::isInstance)
                    .map(BlockHitResult.class::cast)
                    .orElse(null);
            if (hit != null) {
                TunnelExtensionController.updatePreview(hit.getBlockPos());
                TunnelExtensionController.click(hit.getBlockPos());
            }
            return true;
        }

        cursorX=mouseX; cursorY=mouseY;
        hoveredTarget=WorldCursor.pick(minecraft,mouseX,mouseY,width,height);
        if(button==GLFW.GLFW_MOUSE_BUTTON_RIGHT && hasShiftDown() && minecraft.player!=null
                && minecraft.player.getMainHandItem().isEmpty() && hoveredTarget.orElse(null) instanceof BlockHitResult storageHit
                && dev.stonebanner.storage.StorageManagementService.supported(minecraft.level,storageHit.getBlockPos())) {
            dev.stonebanner.client.control.HeroInputController.cancel();
            PlayerCommandController.manageStorage(storageHit);return true;
        }
        if(button==GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if(commands()) { selecting=true; selectionMoved=false; dragStartX=mouseX;dragStartY=mouseY; return true; }
            if (hasAltDown() && dev.stonebanner.config.ClientConfig.controlMode() == dev.stonebanner.control.ControlMode.HYBRID) {
                dev.stonebanner.client.control.HeroInputController.setMoveHeld(false);
                var ground = WorldCursor.pick(minecraft,mouseX,mouseY,width,height,true).orElse(null);
                if (ground instanceof BlockHitResult block) PlayerCommandController.queueMoveTo(block);
                return true;
            }
            if(hoveredTarget.orElse(null) instanceof EntityHitResult e) PlayerCommandController.interactEntity(e.getEntity());
            else if(hoveredTarget.orElse(null) instanceof BlockHitResult b && PlayerCommandController.isInteractiveBlock(b.getBlockPos())) PlayerCommandController.interactBlock(b);
            else if(dev.stonebanner.config.ClientConfig.controlMode()==dev.stonebanner.control.ControlMode.HYBRID) {
                var hit=WorldCursor.pick(minecraft,mouseX,mouseY,width,height,true).orElse(null);
                if(hit instanceof BlockHitResult b) PlayerCommandController.moveTo(b);
                dev.stonebanner.client.control.HeroInputController.setMoveHeld(true);
            }
            return true;
        }
        if(button==GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if(!commands()) { dev.stonebanner.client.control.HeroInputController.setActionHeld(true); return true; }
            if(DesignationController.isActive()) { if(!DesignationController.undoSelectionStep())DesignationController.deactivate(); return true; }
            if(TunnelExtensionController.isActive()) { if(!TunnelExtensionController.undoSelectionStep())TunnelExtensionController.deactivate(); return true; }
            if(hoveredTarget.orElse(null) instanceof EntityHitResult e) CitizenSelectionController.commandTarget(e.getEntity());
            else if(hoveredTarget.orElse(null) instanceof BlockHitResult b) {
                if (hasAltDown()) CitizenSelectionController.moveSelected(b, true);
                else if(hasShiftDown() && dev.stonebanner.client.control.MapLayerState.enabled(dev.stonebanner.client.control.MapLayerState.Layer.RESOURCES)) {
                    dev.stonebanner.network.StoneBannerNetwork.sendGeologyAction(b.getBlockPos().relative(b.getDirection()),dev.stonebanner.geology.GeologyService.Action.SURVEY,CitizenSelectionController.selected().map(n->n.getId()).orElse(-1));
                } else if(PlayerCommandController.isInteractiveBlock(b.getBlockPos())) PlayerCommandController.interactBlock(b);
                else if(!CitizenSelectionController.workSelected(b) && !CitizenSelectionController.moveSelected(b))PlayerCommandController.moveTo(b);
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
            TunnelExtensionController.deactivate();
        }
    }

    private static void activateDesignation(DesignationType type) {
        if (!commands()) dev.stonebanner.client.control.HeroInputController.toggleCommands();
        StoneBannerHudRenderer.selectBottomTab(1);
        TunnelExtensionController.deactivate();
        DesignationController.activate(type);
        CitizenSelectionController.clear();
        PlayerCommandController.stop();
    }

    private static void activateTunnelExtension() {
        if (!commands()) dev.stonebanner.client.control.HeroInputController.toggleCommands();
        StoneBannerHudRenderer.selectBottomTab(1);
        DesignationController.deactivate();
        TunnelExtensionController.activate();
        CitizenSelectionController.clear();
        PlayerCommandController.stop();
    }

    private void selectHotbarSlot(int slot) {
        if (slot < 0 || slot >= 9 || minecraft.player == null) return;
        dev.stonebanner.client.control.HeroInputController.cancel();
        minecraft.player.getInventory().selected = slot;
        minecraft.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if(button==GLFW.GLFW_MOUSE_BUTTON_RIGHT) { dev.stonebanner.client.control.HeroInputController.setActionHeld(false); return true; }
        if(button==GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dev.stonebanner.client.control.HeroInputController.setMoveHeld(false);
            if(selecting) {
                selecting=false;
                if(selectionMoved) CitizenSelectionController.selectArea(dragStartX,dragStartY,mouseX,mouseY,width,height,hasShiftDown());
                else {
                    var hit=WorldCursor.pick(minecraft,mouseX,mouseY,width,height).orElse(null);
                    if(hit instanceof EntityHitResult e) CitizenSelectionController.select(e.getEntity(),hasShiftDown());
                    else if(!hasShiftDown())CitizenSelectionController.clear();
                }
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        cursorX=mouseX;cursorY=mouseY;
        if(button==GLFW.GLFW_MOUSE_BUTTON_LEFT&&selecting) { selectionMoved |= Math.hypot(mouseX-dragStartX,mouseY-dragStartY)>5; return true; }
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            if (commands() && hasShiftDown()) RpgCameraController.panByMouse(dragX, dragY);
            else RpgCameraController.rotateByMouseDrag(dragX, dragY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if(StoneBannerHudRenderer.hotbarIndex(StoneBannerHudRenderer.actionAt(mouseX,mouseY,width,height,CitizenSelectionController.hasSelection()))>=0 && minecraft.player!=null) {
            dev.stonebanner.client.control.HeroInputController.cancel();
            selectHotbarSlot(Math.floorMod(minecraft.player.getInventory().selected+(delta>0?-1:1),9));
        } else if(!overUi(mouseX,mouseY)) RpgCameraController.adjustZoom(delta);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if(dev.stonebanner.client.control.ConstructionPreviewController.active()){
            if(keyCode==GLFW.GLFW_KEY_R)dev.stonebanner.client.control.ConstructionPreviewController.rotate();
            else if(keyCode==GLFW.GLFW_KEY_ESCAPE)dev.stonebanner.client.control.ConstructionPreviewController.cancel();
            return true;
        }
        if(ClientKeyMappings.BUILDING.matches(keyCode,scanCode)){ConstructionScreen.requestOpen();return true;}
        // Numbers remain hotbar keys for the hero, but address control groups in orders mode.
        if (commands() && keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            selecting = false;
            int slot = keyCode - GLFW.GLFW_KEY_1;
            if (hasControlDown()) CitizenSelectionController.saveGroup(slot);
            else CitizenSelectionController.recallGroup(slot, hasShiftDown());
            return true;
        }
        if (ClientKeyMappings.DEBUG_OVERLAY.matches(keyCode, scanCode)) {
            dev.stonebanner.client.hud.DebugOverlay.toggle();
            return true;
        }
        net.minecraft.client.KeyMapping[] layers = {ClientKeyMappings.LAYER_BOUNDARIES, ClientKeyMappings.LAYER_RESOURCES, ClientKeyMappings.LAYER_FERTILITY};
        for (int i = 0; i < layers.length; i++) {
            if (layers[i].matches(keyCode, scanCode)) {
                dev.stonebanner.client.control.MapLayerState.toggle(dev.stonebanner.client.control.MapLayerState.Layer.values()[i]);
                return true;
            }
        }
        if (ClientKeyMappings.ORE_JOURNAL.matches(keyCode, scanCode)) {
            minecraft.setScreen(new OreDiscoveriesScreen(this)); return true;
        }
        if(ClientKeyMappings.PRODUCTION.matches(keyCode,scanCode)){if(minecraft.getConnection()!=null)minecraft.getConnection().sendCommand("sbproduction menu");return true;}
        if (ClientKeyMappings.RECENTER_CAMERA.matches(keyCode, scanCode)) { RpgCameraController.recenter(); return true; }
        if (ClientKeyMappings.FOCUS_SELECTED.matches(keyCode, scanCode)) { RpgCameraController.focusSelected(); return true; }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            selecting=false; dev.stonebanner.client.control.HeroInputController.toggleCommands(); return true;
        }
        if (keyCode == GLFW.GLFW_KEY_SPACE && commands()) {
            CitizenSelectionController.stopAndClear(); PlayerCommandController.stop(); return true;
        }
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
            TunnelExtensionController.deactivate();
            ClientRuntime.cycleControlMode(minecraft);
            return true;
        }
        if (ClientKeyMappings.CYCLE_DESIGNATION_MODE.matches(keyCode, scanCode)) {
            StoneBannerHudRenderer.selectBottomTab(1);
            TunnelExtensionController.deactivate();
            if (!commands()) dev.stonebanner.client.control.HeroInputController.toggleCommands();
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
