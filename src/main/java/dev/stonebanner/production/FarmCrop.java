package dev.stonebanner.production;

import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;

public enum FarmCrop {
    WHEAT, CARROTS, POTATOES, BEETROOTS;
    public CropBlock block(){return (CropBlock)switch(this){case WHEAT->Blocks.WHEAT;case CARROTS->Blocks.CARROTS;case POTATOES->Blocks.POTATOES;case BEETROOTS->Blocks.BEETROOTS;};}
    public Item seed(){return switch(this){case WHEAT->Items.WHEAT_SEEDS;case CARROTS->Items.CARROT;case POTATOES->Items.POTATO;case BEETROOTS->Items.BEETROOT_SEEDS;};}
}
