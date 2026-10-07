package dev.stonebanner.production;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import java.util.*;

/** Matches actual item variants to all ingredient occurrences, including overlapping alternatives. */
public final class CraftingPlan {
    public record Match(int[] slots, CraftingContainer grid) {}
    public static boolean supported(CraftingRecipe recipe){
        return !recipe.isSpecial()&&recipe.getIngredients().size()<=9&&recipe.getIngredients().stream().anyMatch(i->!i.isEmpty())
            &&(!(recipe instanceof ShapedRecipe shaped)||shaped.getWidth()<=3&&shaped.getHeight()<=3);
    }
    public static Optional<Match> match(CraftingRecipe recipe,List<ItemStack> stock,ServerLevel level){
        if(!supported(recipe))return Optional.empty();int[] assignment=new int[recipe.getIngredients().size()];Arrays.fill(assignment,-1);
        int[] counts=stock.stream().mapToInt(ItemStack::getCount).toArray();
        if(!assign(recipe.getIngredients(),stock,counts,assignment,0))return Optional.empty();
        CraftingContainer grid=grid();
        for(int i=0;i<assignment.length;i++)if(assignment[i]>=0){int at=recipe instanceof ShapedRecipe shaped?(i/shaped.getWidth())*3+i%shaped.getWidth():i;var stack=stock.get(assignment[i]).copy();stack.setCount(1);grid.setItem(at,stack);}
        return recipe.matches(grid,level)?Optional.of(new Match(assignment,grid)):Optional.empty();
    }
    private static boolean assign(List<Ingredient> ingredients,List<ItemStack> stock,int[] counts,int[] result,int index){
        if(index==ingredients.size())return true;if(ingredients.get(index).isEmpty())return assign(ingredients,stock,counts,result,index+1);
        for(int slot=0;slot<stock.size();slot++)if(counts[slot]>0&&ingredients.get(index).test(stock.get(slot))){counts[slot]--;result[index]=slot;if(assign(ingredients,stock,counts,result,index+1))return true;counts[slot]++;result[index]=-1;}
        return false;
    }
    /** Maximum partial ingredient matching avoids asking for a false deficit with overlapping tags. */
    public static Ingredient missing(CraftingRecipe recipe,List<ItemStack> stock){
        var ingredients=recipe.getIngredients();var units=new ArrayList<ItemStack>();
        for(var stack:stock)for(int i=0;i<Math.min(stack.getCount(),ingredients.size());i++)units.add(stack);
        int[] assigned=new int[units.size()];Arrays.fill(assigned,-1);boolean[] matched=new boolean[ingredients.size()];
        for(int i=0;i<ingredients.size();i++)matched[i]=ingredients.get(i).isEmpty()||augment(i,ingredients,units,assigned,new boolean[units.size()]);
        for(int i=0;i<matched.length;i++)if(!matched[i])return ingredients.get(i);
        return Ingredient.EMPTY;
    }
    private static boolean augment(int ingredient,List<Ingredient> ingredients,List<ItemStack> units,int[] assigned,boolean[] visited){
        for(int i=0;i<units.size();i++)if(!visited[i]&&ingredients.get(ingredient).test(units.get(i))){
            visited[i]=true;if(assigned[i]<0||augment(assigned[i],ingredients,units,assigned,visited)){assigned[i]=ingredient;return true;}
        }return false;
    }
    public static CraftingContainer grid(){return new TransientCraftingContainer(new AbstractContainerMenu(null,0){
        @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
        @Override public boolean stillValid(Player player){return true;}
    },3,3);}
}
