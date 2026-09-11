package com.bubbleanyway.client.mixin;

import java.util.List;
import net.minecraft.client.toast.RecipeToast;
import net.minecraft.recipe.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RecipeToast.class)
public interface RecipeToastAccessor {
    @Accessor("recipes")
    List<Recipe<?>> bubbleAnyway$getRecipes();
}
