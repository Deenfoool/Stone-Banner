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
import dev.stonebanner.control.InputContext;
import dev.stonebanner.control.ActionResolver;
import dev.stonebanner.client.control.HeroInputController;
import dev.stonebanner.client.control.InputBindings;

/** Transparent input layer for both hero profiles and the separate group-order mode. */
public final class TacticalControlScreen extends Screen {
    private double cursorX, cursorY, dragStartX, dragStartY;
    private boolean selecting, selectionMoved;
    private final dev.stonebanner.control.HeroOrdersGesture ordersGesture = new dev.stonebanner.control.HeroOrdersGesture();
    private final dev.stonebanner.control.DoublePressGesture homeGesture = new dev.stonebanner.control.DoublePressGesture();
    private final dev.stonebanner.control.DoublePressGesture[] groupGestures = java.util.stream.IntStream.range(0, 9)
            .mapToObj(i -> new dev.stonebanner.control.DoublePressGesture()).toArray(dev.stonebanner.control.DoublePressGesture[]::new);

    private void applyOrdersGesture(dev.stonebanner.control.HeroOrdersGesture.Change change) {
        if (change == dev.stonebanner.control.HeroOrdersGesture.Change.NONE) return;
        boolean shouldEnter = change == dev.stonebanner.control.HeroOrdersGesture.Change.ENTER_ORDERS;
        if (commands() != shouldEnter) {
            selecting = false;
            HeroInputController.toggleCommands();
        }
    }
    private static boolean commands() { return dev.stonebanner.client.control.HeroInputController.commandMode(); }
    private boolean overUi(double x,double y) { return OreDiscoveryHud.contains(x,y,width) || StoneBannerHudRenderer.actionAt(x,y,width,height,CitizenSelectionController.hasSelection()) != StoneBannerHudRenderer.HudAction.NONE; }
    public double[] edgePan() {
        if (!dev.stonebanner.config.ClientConfig.CAMERA_EDGE_PAN.get()) return new double[]{0,0};
        if (!commands() || overUi(cursorX,cursorY) || selecting || org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(minecraft.getWindow().getWindow(),GLFW.GLFW_FOCUSED)!=1) return new double[]{0,0};
        return dev.stonebanner.control.ScreenInputRules.edgePan(cursorX,cursorY,width,height,10);
    }
    @Override protected void init() { cursorX=width*.5;cursorY=height*.5; }
    @Override public void tick() {
        if (ordersGesture.pressed()) {
            boolean focused = GLFW.glfwGetWindowAttrib(minecraft.getWindow().getWindow(), GLFW.GLFW_FOCUSED) == 1;
            if (!focused) applyOrdersGesture(ordersGesture.interrupt(commands()));
            else if (!InputBindings.held(ClientKeyMappings.ORDERS))
                applyOrdersGesture(ordersGesture.release(net.minecraft.Util.getMillis(),
                        dev.stonebanner.config.ClientConfig.ORDERS_HOLD_MS.get(), commands()));
        }
        if (!InputBindings.held(ClientKeyMappings.RECENTER_CAMERA)) homeGesture.release();
        for (int i = 0; i < groupGestures.length; i++)
            if (!InputBindings.held(ClientKeyMappings.RECALL_GROUP[i])) groupGestures[i].release();
        if(context(false)==InputContext.CONSTRUCTION || context(false)==InputContext.DESIGNATION){HeroInputController.cancel();return;}
        dev.stonebanner.client.control.HeroInputController.tick(
                WorldCursor.pick(minecraft,cursorX,cursorY,width,height).orElse(null), overUi(cursorX,cursorY),
                WorldCursor.pick(minecraft,cursorX,cursorY,width,height,true).orElse(null));
    }
    @Override public void removed() {
        applyOrdersGesture(ordersGesture.interrupt(commands()));
        homeGesture.reset();
        for (var gesture : groupGestures) gesture.reset();
        PlayerCommandController.cancelPendingActions();
        dev.stonebanner.client.control.BlockPlacementPreview.reset();
        HeroInputController.resetGroundClicks();
        HeroInputController.cancel();
        dev.stonebanner.client.control.ConstructionPreviewController.cancel();
    }
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
        graphics.drawString(font, font.plainSubstrByWidth(controlHint().getString(), width - 16), 8, 96, 0xFFD8D2C8);
        if(!commands() && PlayerCommandController.moving())graphics.drawString(font,
                Component.translatable("movement.stonebanner.pace."+PlayerCommandController.movePace().name().toLowerCase(java.util.Locale.ROOT)),8,110,0xFF69DDE7);
        if(PlayerCommandController.rejectedGoal().isPresent())graphics.drawString(font,
                Component.translatable("movement.stonebanner.refused",Component.translatable("movement.stonebanner.reason."+
                        PlayerCommandController.failureReason().name().toLowerCase(java.util.Locale.ROOT))),8,122,0xFFFF6868);
        if(dev.stonebanner.client.control.ConstructionPreviewController.active()){
            graphics.drawString(font,Component.translatable("construction.stonebanner.ui.preview"),8,110,0xFFE7C46A);
            graphics.drawString(font,Component.translatable("construction.stonebanner.status."+dev.stonebanner.client.control.ConstructionPreviewController.status()),8,122,0xFFD8D2C8);
        }
        float progress=HeroInputController.miningProgress();
        if(progress>0){graphics.fill(width/2-40,height/2+18,width/2+40,height/2+23,0xAA222222);
            graphics.fill(width/2-40,height/2+18,width/2-40+(int)(80*Math.min(1,progress)),height/2+23,0xDDCDA96E);}
        if(minecraft.player!=null && minecraft.player.isUsingItem() && minecraft.player.getUseItem().getItem() instanceof net.minecraft.world.item.BowItem){
            float charge=net.minecraft.world.item.BowItem.getPowerForTime(minecraft.player.getTicksUsingItem());
            graphics.drawString(font,Component.translatable("hud.stonebanner.bow_charge",(int)(charge*100)),width/2-35,height/2+28,0xFFD8D2C8);
        }
        ExcavationLadderStatusHud.render(graphics, minecraft, width);

        OreDiscoveryHud.render(graphics, minecraft, width, height);
        dev.stonebanner.client.hud.DebugOverlay.render(graphics, minecraft, width, height);
        if(selecting && selectionMoved) graphics.renderOutline((int)Math.min(dragStartX,mouseX),(int)Math.min(dragStartY,mouseY),(int)Math.abs(mouseX-dragStartX)+1,(int)Math.abs(mouseY-dragStartY)+1,0xFF69DDE7);
        var selectedCitizens = CitizenSelectionController.selectedAll();
        if(commands() && !dev.stonebanner.client.control.ConstructionPreviewController.active())graphics.drawString(font,Component.translatable("hud.stonebanner.selection_count", selectedCitizens.size(),
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
        // In Orders a hostile hover is visually distinct from a friendly selection.
        if (commands() && hoveredTarget.orElse(null) instanceof EntityHitResult e) {
            return e.getEntity() instanceof net.minecraft.world.entity.monster.Monster
                    ? 0xFFFF6868 : 0xFF69DDE7;
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
        if (overUi(mouseX,mouseY)) {HeroInputController.resetGroundClicks();HeroInputController.cancel();}
        if(dev.stonebanner.client.control.ConstructionPreviewController.active() && !overUi(mouseX,mouseY)){
            if(minecraft.options.keyUse.matchesMouse(button))dev.stonebanner.client.control.ConstructionPreviewController.cancel();
            else if(minecraft.options.keyAttack.matchesMouse(button)){
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

        cursorX=mouseX; cursorY=mouseY;
        if (keyPressed(10000+button,0,0)) return true;
        if (minecraft.options.keyAttack.matchesMouse(button)) return worldAction(true);
        if (minecraft.options.keyUse.matchesMouse(button)) return worldAction(false);
        return ClientKeyMappings.CAMERA_ROTATE.matchesMouse(button) || super.mouseClicked(mouseX,mouseY,button);
    }

    private boolean worldAction(boolean primary) {
        hoveredTarget=WorldCursor.pick(minecraft,cursorX,cursorY,width,height);
        if(minecraft.player==null || minecraft.level==null || !minecraft.player.isAlive())return true;
        var context=context(overUi(cursorX,cursorY));
        // Resolve modal priority before any world command, including keyboard-bound actions.
        var route=ActionResolver.resolve(context,primary?ActionResolver.Button.PRIMARY:ActionResolver.Button.SECONDARY,
                ActionResolver.Target.NONE,false,false,false);
        if(route==ActionResolver.Action.CONSUME && context!=InputContext.WASD && context!=InputContext.MOUSE)return true;
        if(context==InputContext.CONSTRUCTION) {
            if(primary)dev.stonebanner.client.control.ConstructionPreviewController.confirm();
            else dev.stonebanner.client.control.ConstructionPreviewController.cancel();
            return true;
        }
        if(context==InputContext.DESIGNATION) {
            if(DesignationController.isActive()) {
                if(!primary){if(!DesignationController.undoSelectionStep())DesignationController.deactivate();}
                else if(hoveredTarget.orElse(null) instanceof BlockHitResult b){DesignationController.updatePreview(b.getBlockPos());DesignationController.click(b.getBlockPos());}
            } else {
                if(!primary){if(!TunnelExtensionController.undoSelectionStep())TunnelExtensionController.deactivate();}
                else if(hoveredTarget.orElse(null) instanceof BlockHitResult b){TunnelExtensionController.updatePreview(b.getBlockPos());TunnelExtensionController.click(b.getBlockPos());}
            }
            return true;
        }
        if(!commands()) {
            if(!primary && hasAltDown()) {openActionMenu();return true;}
            if(!primary && hasShiftDown() && minecraft.player.getMainHandItem().isEmpty()
                    && hoveredTarget.orElse(null) instanceof BlockHitResult b
                    && dev.stonebanner.storage.StorageManagementService.supported(minecraft.level,b.getBlockPos())) {
                HeroInputController.cancel();PlayerCommandController.manageStorage(b);return true;
            }
            return heroAction(primary);
        }
        if(primary) { selecting=true;selectionMoved=false;dragStartX=cursorX;dragStartY=cursorY;return true; }
        if(hoveredTarget.orElse(null) instanceof EntityHitResult e)
            CitizenSelectionController.commandTarget(e.getEntity(), hasAltDown());
        else if(hoveredTarget.orElse(null) instanceof BlockHitResult b) {
            if(hasAltDown() && hasShiftDown() && CitizenSelectionController.workSelected(b,true)) { }
            else if(hasAltDown())CitizenSelectionController.moveSelected(b,true);
            else if(hasShiftDown() && dev.stonebanner.client.control.MapLayerState.enabled(dev.stonebanner.client.control.MapLayerState.Layer.RESOURCES)) {
                dev.stonebanner.network.StoneBannerNetwork.sendGeologyAction(b.getBlockPos().relative(b.getDirection()),
                        dev.stonebanner.geology.GeologyService.Action.SURVEY,CitizenSelectionController.selected().map(n->n.getId()).orElse(-1));
            } else if(PlayerCommandController.isInteractiveBlock(b.getBlockPos()))PlayerCommandController.interactBlock(b);
            else if(!CitizenSelectionController.workSelected(b))CitizenSelectionController.moveSelected(b);
        }
        return true;
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
        for (int i = 0; i < groupGestures.length; i++)
            if (ClientKeyMappings.RECALL_GROUP[i].getKey().getType()
                    == com.mojang.blaze3d.platform.InputConstants.Type.MOUSE
                    && ClientKeyMappings.RECALL_GROUP[i].getKey().getValue() == button)
                groupGestures[i].release();
        if (InputBindings.matches(ClientKeyMappings.ORDERS, 10000 + button, 0)
                || InputBindings.matches(ClientKeyMappings.RECENTER_CAMERA, 10000 + button, 0))
            return keyReleased(10000 + button, 0, 0);
        if(minecraft.options.keyUse.matchesMouse(button)) { if(overUi(mouseX,mouseY))HeroInputController.cancel();else HeroInputController.release(minecraft.options.keyUse); return true; }
        if(minecraft.options.keyAttack.matchesMouse(button)) {
            HeroInputController.release(minecraft.options.keyAttack);
            dev.stonebanner.client.control.HeroInputController.setMoveHeld(false);
            finishSelection(mouseX,mouseY);
            return true;
        }
        return keyReleased(10000+button,0,0) || super.mouseReleased(mouseX, mouseY, button);
    }

    private void finishSelection(double mouseX,double mouseY) {
            if(selecting) {
                selecting=false;
                if(selectionMoved) CitizenSelectionController.selectArea(dragStartX,dragStartY,mouseX,mouseY,width,height,hasShiftDown());
                else {
                    var hit=WorldCursor.pick(minecraft,mouseX,mouseY,width,height).orElse(null);
                    if(hit instanceof EntityHitResult e) CitizenSelectionController.select(e.getEntity(),hasShiftDown());
                    else if(!hasShiftDown())CitizenSelectionController.clear();
                }
            }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        cursorX=mouseX;cursorY=mouseY;
        if(minecraft.options.keyAttack.matchesMouse(button)&&selecting) { selectionMoved |= Math.hypot(mouseX-dragStartX,mouseY-dragStartY)>5; return true; }
        if (ClientKeyMappings.CAMERA_ROTATE.matchesMouse(button)) {
            if (commands() && hasShiftDown()) RpgCameraController.panByMouse(dragX, dragY);
            else RpgCameraController.rotateByMouseDrag(dragX, dragY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override public void mouseMoved(double x,double y) {
        if (ClientKeyMappings.CAMERA_ROTATE.getKey().getType()!=com.mojang.blaze3d.platform.InputConstants.Type.MOUSE
                && InputBindings.held(ClientKeyMappings.CAMERA_ROTATE))RpgCameraController.rotateByMouseDrag(x-cursorX,y-cursorY);
        cursorX=x;cursorY=y;
        super.mouseMoved(x,y);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if(StoneBannerHudRenderer.hotbarIndex(StoneBannerHudRenderer.actionAt(mouseX,mouseY,width,height,CitizenSelectionController.hasSelection()))>=0 && minecraft.player!=null) {
            dev.stonebanner.client.control.HeroInputController.cancel();
            selectHotbarSlot(Math.floorMod(minecraft.player.getInventory().selected+(delta>0?-1:1),9));
        } else if(!overUi(mouseX,mouseY)) {
            if(dev.stonebanner.client.control.InputBindings.held(ClientKeyMappings.ROTATE_PLACEMENT_MODIFIER)
                    && dev.stonebanner.client.control.ConstructionPreviewController.active())
                dev.stonebanner.client.control.ConstructionPreviewController.rotate(delta>0?1:-1);
            else if(dev.stonebanner.client.control.InputBindings.held(ClientKeyMappings.ROTATE_PLACEMENT_MODIFIER)
                    && dev.stonebanner.client.control.BlockPlacementPreview.rotate(delta>0?1:-1)) { }
            else RpgCameraController.adjustZoom(delta);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if(dev.stonebanner.client.control.ConstructionPreviewController.active()){
            if(InputBindings.matches(ClientKeyMappings.ROTATE_BLUEPRINT,keyCode,scanCode)){dev.stonebanner.client.control.ConstructionPreviewController.rotate();return true;}
            else if(keyCode==GLFW.GLFW_KEY_ESCAPE){dev.stonebanner.client.control.ConstructionPreviewController.cancel();return true;}
            if(InputBindings.matches(minecraft.options.keyAttack,keyCode,scanCode))return worldAction(true);
            if(InputBindings.matches(minecraft.options.keyUse,keyCode,scanCode))return worldAction(false);
        }
        if(InputBindings.matches(ClientKeyMappings.CONTROLS_HELP,keyCode,scanCode)){minecraft.setScreen(new ControlBindingsScreen(this));return true;}
        if(InputBindings.matches(ClientKeyMappings.BUILDING,keyCode,scanCode)){ConstructionScreen.requestOpen();return true;}
        if (commands()) for (int i=0;i<9;i++) {
            if (InputBindings.matches(ClientKeyMappings.SAVE_GROUP[i],keyCode,scanCode)) {
                selecting=false; CitizenSelectionController.saveGroup(i); groupGestures[i].reset(); return true;
            }
            if (InputBindings.matches(ClientKeyMappings.RECALL_GROUP[i],keyCode,scanCode)) {
                selecting=false;
                boolean doubleRecall = groupGestures[i].press(net.minecraft.Util.getMillis(),
                        dev.stonebanner.config.ClientConfig.GROUP_DOUBLE_MS.get());
                var members = CitizenSelectionController.recallGroup(i,hasShiftDown());
                if (doubleRecall && !members.isEmpty()) RpgCameraController.focusGroup(members);
                return true;
            }
        }
        if (InputBindings.matches(ClientKeyMappings.DEBUG_OVERLAY,keyCode,scanCode)) {
            dev.stonebanner.client.hud.DebugOverlay.toggle();
            return true;
        }
        net.minecraft.client.KeyMapping[] layers = {ClientKeyMappings.LAYER_BOUNDARIES, ClientKeyMappings.LAYER_RESOURCES, ClientKeyMappings.LAYER_FERTILITY};
        for (int i = 0; i < layers.length; i++) {
            if (InputBindings.matches(layers[i],keyCode,scanCode)) {
                dev.stonebanner.client.control.MapLayerState.toggle(dev.stonebanner.client.control.MapLayerState.Layer.values()[i]);
                return true;
            }
        }
        if (InputBindings.matches(ClientKeyMappings.ORE_JOURNAL,keyCode,scanCode)) {
            minecraft.setScreen(new OreDiscoveriesScreen(this)); return true;
        }
        if(InputBindings.matches(ClientKeyMappings.PRODUCTION,keyCode,scanCode)){if(minecraft.getConnection()!=null)minecraft.getConnection().sendCommand("sbproduction menu");return true;}
        if (InputBindings.matches(ClientKeyMappings.RECENTER_CAMERA,keyCode,scanCode)) {
            boolean twice = homeGesture.press(net.minecraft.Util.getMillis(),
                    dev.stonebanner.config.ClientConfig.HOME_DOUBLE_MS.get());
            if (twice) RpgCameraController.resetDefaultView();
            else RpgCameraController.recenter();
            return true;
        }
        if (InputBindings.matches(ClientKeyMappings.FOCUS_SELECTED,keyCode,scanCode)) { RpgCameraController.focusSelected(); return true; }
        if (InputBindings.matches(ClientKeyMappings.ORDERS,keyCode,scanCode)) {
            applyOrdersGesture(ordersGesture.press(net.minecraft.Util.getMillis(), commands()));
            return true;
        }
        if (InputBindings.matches(ClientKeyMappings.STOP,keyCode,scanCode) && commands()) {
            CitizenSelectionController.stopAndClear(); PlayerCommandController.stop(); return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            minecraft.setScreen(new PauseScreen(true));
            return true;
        }
        for (int i=0;i<9;i++) if(InputBindings.matches(minecraft.options.keyHotbarSlots[i],keyCode,scanCode)) {
            selectHotbarSlot(i); return true;
        }
        if (InputBindings.matches(ClientKeyMappings.CYCLE_CONTROL_MODE,keyCode,scanCode)) {
            DesignationController.deactivate();
            TunnelExtensionController.deactivate();
            ClientRuntime.cycleControlMode(minecraft);
            return true;
        }
        if (InputBindings.matches(ClientKeyMappings.CYCLE_DESIGNATION_MODE,keyCode,scanCode)) {
            StoneBannerHudRenderer.selectBottomTab(1);
            TunnelExtensionController.deactivate();
            if (!commands()) dev.stonebanner.client.control.HeroInputController.toggleCommands();
            DesignationController.cycleMode();
            CitizenSelectionController.clear();
            PlayerCommandController.stop();
            return true;
        }
        if (InputBindings.matches(minecraft.options.keyInventory,keyCode,scanCode) && minecraft.player != null) {
            minecraft.setScreen(new InventoryScreen(minecraft.player));
            return true;
        }
        if (InputBindings.matches(minecraft.options.keyChat,keyCode,scanCode)) {
            minecraft.setScreen(new net.minecraft.client.gui.screens.ChatScreen("")); return true;
        }
        if (InputBindings.matches(minecraft.options.keyCommand,keyCode,scanCode)) {
            minecraft.setScreen(new net.minecraft.client.gui.screens.ChatScreen("/")); return true;
        }
        if (InputBindings.matches(minecraft.options.keySwapOffhand,keyCode,scanCode) && minecraft.player != null && !minecraft.player.isSpectator()) {
            HeroInputController.cancel();
            minecraft.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerActionPacket(
                    net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                    net.minecraft.core.BlockPos.ZERO, net.minecraft.core.Direction.DOWN)); return true;
        }
        if(InputBindings.matches(ClientKeyMappings.ALTERNATIVE_USE,keyCode,scanCode)) {
            var active=context(overUi(cursorX,cursorY));
            if(active!=InputContext.WASD && active!=InputContext.MOUSE && active!=InputContext.ORDERS)return true;
            openActionMenu();
            return true;
        }
        if(InputBindings.matches(minecraft.options.keyAttack,keyCode,scanCode))return worldAction(true);
        if(InputBindings.matches(minecraft.options.keyUse,keyCode,scanCode))return worldAction(false);
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean keyReleased(int keyCode,int scanCode,int modifiers) {
        // Release immediately on the actual event; two quick Alt+number presses may fit within
        // one client tick, so polling alone must not leave the gesture stuck as held.
        for (int i = 0; i < groupGestures.length; i++) {
            var bound = ClientKeyMappings.RECALL_GROUP[i].getKey();
            if (bound.getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM
                    && bound.getValue() == keyCode
                    || bound.getType() == com.mojang.blaze3d.platform.InputConstants.Type.SCANCODE
                    && bound.getValue() == scanCode)
                groupGestures[i].release();
        }
        if (InputBindings.matches(ClientKeyMappings.ORDERS,keyCode,scanCode)) {
            applyOrdersGesture(ordersGesture.release(net.minecraft.Util.getMillis(),
                    dev.stonebanner.config.ClientConfig.ORDERS_HOLD_MS.get(), commands()));
            return true;
        }
        if (InputBindings.matches(ClientKeyMappings.RECENTER_CAMERA,keyCode,scanCode)) {
            homeGesture.release();
            return true;
        }
        if(InputBindings.matches(minecraft.options.keyAttack,keyCode,scanCode)) {
            HeroInputController.setMoveHeld(false); HeroInputController.release(minecraft.options.keyAttack);finishSelection(cursorX,cursorY);return true;
        }
        if(InputBindings.matches(minecraft.options.keyUse,keyCode,scanCode)) { HeroInputController.release(minecraft.options.keyUse);return true; }
        return super.keyReleased(keyCode,scanCode,modifiers);
    }

    private Component controlHint() {
        var o=minecraft.options;
        return Component.translatable(commands()?"hud.stonebanner.commands.bindings":
                dev.stonebanner.config.ClientConfig.controlMode()==dev.stonebanner.control.ControlMode.ACTION
                        ?"hud.stonebanner.wasd.bindings":"hud.stonebanner.mouse.bindings",
                o.keyAttack.getTranslatedKeyMessage(),o.keyUse.getTranslatedKeyMessage(),
                ClientKeyMappings.ORDERS.getTranslatedKeyMessage(),ClientKeyMappings.CYCLE_CONTROL_MODE.getTranslatedKeyMessage(),
                o.keyJump.getTranslatedKeyMessage(),o.keyShift.getTranslatedKeyMessage(),o.keySprint.getTranslatedKeyMessage());
    }

    private InputContext context(boolean ui) {
        return InputContext.resolve(ui,dev.stonebanner.client.control.ConstructionPreviewController.active(),
                DesignationController.isActive() || TunnelExtensionController.isActive(),commands(),
                minecraft.player != null && minecraft.player.isUsingItem(),
                dev.stonebanner.config.ClientConfig.controlMode()==dev.stonebanner.control.ControlMode.HYBRID);
    }

    private void openActionMenu(){
        var hit=WorldCursor.pick(minecraft,cursorX,cursorY,width,height).orElse(null);
        HeroInputController.cancel();PlayerCommandController.cancelPendingActions();
        if(hit!=null)minecraft.setScreen(new ContextActionScreen(this,hit));
    }
    private boolean heroAction(boolean primary) {
        var hit=WorldCursor.pick(minecraft,cursorX,cursorY,width,height).orElse(null);
        ActionResolver.Target target=ActionResolver.Target.NONE;
        if(hit instanceof EntityHitResult e) target=dev.stonebanner.control.HeroActionRules.protectedTarget(minecraft.player,e.getEntity())
                ? ActionResolver.Target.FRIENDLY : e.getEntity() instanceof net.minecraft.world.entity.monster.Monster
                ? ActionResolver.Target.HOSTILE : ActionResolver.Target.NEUTRAL;
        else if(hit instanceof BlockHitResult b) target=PlayerCommandController.isInteractiveBlock(b.getBlockPos())
                ? ActionResolver.Target.INTERACTIVE_BLOCK : ActionResolver.Target.BLOCK;
        var item=minecraft.player.getMainHandItem();
        var action=ActionResolver.resolve(context(overUi(cursorX,cursorY)),
                primary?ActionResolver.Button.PRIMARY:ActionResolver.Button.SECONDARY,target,hasAltDown(),
                item.getUseDuration()>0,item.getItem() instanceof net.minecraft.world.item.BlockItem);
        if(action!=ActionResolver.Action.MOVE)HeroInputController.resetGroundClicks();
        // Only empty-handed ground use cancels a Mouse route; tools/bows/blocks retain their actions.
        if(!primary && !commands() && dev.stonebanner.config.ClientConfig.controlMode()==dev.stonebanner.control.ControlMode.HYBRID
                && item.isEmpty() && target==ActionResolver.Target.BLOCK && hit instanceof BlockHitResult groundHit
                && groundHit.getDirection()==net.minecraft.core.Direction.UP
                && !minecraft.level.getBlockState(groundHit.getBlockPos()).is(net.minecraft.tags.BlockTags.LOGS)
                && !minecraft.level.getBlockState(groundHit.getBlockPos()).is(net.minecraftforge.common.Tags.Blocks.ORES)) {
            HeroInputController.cancel();PlayerCommandController.stop();return true;
        }
        switch(action) {
            case MOVE, QUEUE_MOVE -> {
                if(action==ActionResolver.Action.MOVE && HeroInputController.moveHeld())return true;
                var ground=WorldCursor.pick(minecraft,cursorX,cursorY,width,height,true).orElse(null);
                HeroInputController.cancel();
                if(ground instanceof BlockHitResult b) {
                    if(action==ActionResolver.Action.QUEUE_MOVE)PlayerCommandController.queueMoveTo(b);
                    else {
                        boolean plainGround=hit instanceof BlockHitResult h && !PlayerCommandController.isInteractiveBlock(h.getBlockPos());
                        var pace=HeroInputController.groundClickPace(plainGround,cursorX,cursorY);
                        PlayerCommandController.moveTo(b,pace,false);
                        if(PlayerCommandController.navigationFailed())HeroInputController.resetGroundClicks();
                        HeroInputController.setMoveHeld(true);
                    }
                }
            }
            case INTERACT -> {
                HeroInputController.cancel();
                if(hit instanceof EntityHitResult e)PlayerCommandController.interactEntity(e.getEntity());
                else if(hit instanceof BlockHitResult b)PlayerCommandController.interactBlock(b);
            }
            case ATTACK_OR_MINE, USE_ITEM -> HeroInputController.setActionHeld(
                    primary?minecraft.options.keyAttack:minecraft.options.keyUse,action==ActionResolver.Action.ATTACK_OR_MINE,hit);
            default -> { }
        }
        return true;
    }
}
