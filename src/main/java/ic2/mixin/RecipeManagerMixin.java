package ic2.mixin;

import ic2.core.IC2;
import ic2.core.util.LogCategory;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// We use this to check THE FUCKING CREATE MOD is incompatible
//
// NeoForge had a second overload taking an ICondition.IContext for conditional recipes; Fabric
// evaluates resource conditions before the recipe manager sees them, so only the vanilla overload
// remains. Vanilla 1.21.1 declares it as
// fromJson(ResourceLocation, JsonObject, HolderLookup.Provider) returning RecipeHolder<?>.
@Mixin(RecipeManager.class)
public class RecipeManagerMixin {
  @Inject(
      method =
          "fromJson(Lnet/minecraft/resources/ResourceLocation;Lcom/google/gson/JsonObject;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/crafting/RecipeHolder;",
      at = @At("HEAD"))
  private static void logRecipeId(
      ResourceLocation recipeId,
      com.google.gson.JsonObject json,
      HolderLookup.Provider registries,
      CallbackInfoReturnable<RecipeHolder<?>> cir) {
    IC2.log.debug(LogCategory.Recipe, "[IC2 Recipe Debug] Loading recipe: %s", recipeId);
  }
}
