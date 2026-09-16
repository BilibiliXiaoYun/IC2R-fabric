package ic2.fabric;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;

/**
 * Client-side fluid handler.
 *
 * <p>NeoForge resolved sprites and tints through {@code IClientFluidTypeExtensions}; on Fabric the
 * sprite ids and tint are the ones IC2 declared in {@code EnvFluidHandler#createFluid} (see {@link
 * FabricFluidHandler}), because vanilla 1.21.1 has no fluid type to hang them off. Anything IC2 did
 * not declare falls back to the missing-texture sprite and an opaque tint, matching NeoForge's
 * default extensions.
 */
@Environment(EnvType.CLIENT)
public final class FabricClientFluidHandler extends FabricFluidHandler {
  @Override
  public ResourceLocation getStillSpriteId(Fluid fluid) {
    ResourceLocation sprite = FabricFluidHandler.stillSprite(fluid);
    return sprite != null ? sprite : MissingTextureAtlasSprite.getLocation();
  }

  @Override
  public ResourceLocation getFlowingSpriteId(Fluid fluid) {
    ResourceLocation sprite = FabricFluidHandler.flowingSprite(fluid);
    return sprite != null ? sprite : MissingTextureAtlasSprite.getLocation();
  }

  @Override
  public int getColor(Fluid fluid) {
    return FabricFluidHandler.color(fluid);
  }
}
