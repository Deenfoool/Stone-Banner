package dev.stonebanner.production;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CraftingPlanTest {
    private static ShapelessRecipe recipe(Ingredient... ingredients){return new ShapelessRecipe(ResourceLocation.fromNamespaceAndPath("stonebanner","test"),"",CraftingBookCategory.MISC,new ItemStack(Items.STICK),NonNullList.of(Ingredient.EMPTY,ingredients));}
    @Test void overlappingIngredientsFindMaximumPartialMatch(){
        var recipe=recipe(Ingredient.of(Items.OAK_PLANKS,Items.BIRCH_PLANKS),Ingredient.of(Items.OAK_PLANKS));
        assertTrue(CraftingPlan.missing(recipe,List.of(new ItemStack(Items.OAK_PLANKS),new ItemStack(Items.BIRCH_PLANKS))).isEmpty());
    }
    @Test void repeatedIngredientsRequireRealCount(){var recipe=recipe(Ingredient.of(Items.WHEAT),Ingredient.of(Items.WHEAT),Ingredient.of(Items.WHEAT));assertFalse(CraftingPlan.missing(recipe,List.of(new ItemStack(Items.WHEAT,2))).isEmpty());assertTrue(CraftingPlan.missing(recipe,List.of(new ItemStack(Items.WHEAT,3))).isEmpty());}
    @Test void emptyGridPositionsDoNotRequireItems(){var recipe=recipe(Ingredient.EMPTY,Ingredient.of(Items.WHEAT),Ingredient.EMPTY);assertTrue(CraftingPlan.missing(recipe,List.of(new ItemStack(Items.WHEAT))).isEmpty());}
    @Test void unrelatedItemsDoNotFillDeficit(){assertFalse(CraftingPlan.missing(recipe(Ingredient.of(Items.WHEAT)),List.of(new ItemStack(Items.WHEAT_SEEDS,64))).isEmpty());}
    @Test void emptyRecipesAreNotSupported(){assertFalse(CraftingPlan.supported(recipe(Ingredient.EMPTY)));assertTrue(CraftingPlan.supported(recipe(Ingredient.of(Items.WHEAT))));}
}
