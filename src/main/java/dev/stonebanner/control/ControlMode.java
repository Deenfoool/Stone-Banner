package dev.stonebanner.control;

import net.minecraft.network.chat.Component;

public enum ControlMode {
    ACTION("action"),
    TACTICAL("tactical"),
    HYBRID("hybrid");

    private final String translationKeyPart;

    ControlMode(String translationKeyPart) {
        this.translationKeyPart = translationKeyPart;
    }

    public ControlMode next() {
        ControlMode[] modes = values();
        return modes[(ordinal() + 1) % modes.length];
    }

    public Component displayName() {
        return Component.translatable("control_mode.stonebanner." + translationKeyPart);
    }
}
