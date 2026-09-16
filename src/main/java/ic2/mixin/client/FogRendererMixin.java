package ic2.mixin.client;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import ic2.core.event.EventHandlerClient;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies IC2's fluid fog.
 *
 * <p>NeoForge exposed {@code ViewportEvent.RenderFog} and {@code ViewportEvent.ComputeFogColor};
 * Fabric has no fog callback, so the same values are written into the render system right after
 * vanilla finishes computing them. The lookups go through {@code Camera#getBlockPosition()} because
 * vanilla's {@code Camera} has no NeoForge's {@code getBlockAtCamera()} helper.
 */
@Mixin(FogRenderer.class)
public class FogRendererMixin {
  @Inject(
      method = "setupColor(Lnet/minecraft/client/Camera;FLnet/minecraft/client/multiplayer/ClientLevel;IF)V",
      at = @At("TAIL"))
  private static void ic2$renderFogColor(
      Camera camera,
      float partialTick,
      ClientLevel level,
      int renderDistance,
      float darkenWorldAmount,
      CallbackInfo callback) {
    BlockState state = level.getBlockState(camera.getBlockPosition());
    int color = EventHandlerClient.onRenderFogColor(state);
    if (color >= 0) {
      RenderSystem.setShaderFogColor(
          (color >>> 16 & 0xFF) / 255.0F, (color >>> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F);
    }
  }

  @Inject(
      method = "setupFog(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/FogRenderer$FogMode;FZF)V",
      at = @At("TAIL"))
  private static void ic2$setupFogDensity(
      Camera camera,
      FogRenderer.FogMode mode,
      float renderDistance,
      boolean thickFog,
      float partialTick,
      CallbackInfo callback) {
    ClientLevel level = Minecraft.getInstance().level;
    if (level == null) {
      return;
    }

    float density = EventHandlerClient.onSetupFogDensity(level.getBlockState(camera.getBlockPosition()));
    if (density >= 0.0F) {
      RenderSystem.setShaderFogStart(-8.0F);
      RenderSystem.setShaderFogEnd(density * 0.5F);
      RenderSystem.setShaderFogShape(FogShape.SPHERE);
    }
  }
}
