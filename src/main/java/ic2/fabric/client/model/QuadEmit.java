package ic2.fabric.client.model;

import java.util.List;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.model.BakedQuad;

/**
 * Bridges IC2's existing {@link BakedQuad} meshes onto Fabric's Indigo renderer.
 *
 * <p>The NeoForge models built {@code List<BakedQuad>[]} meshes and returned them from {@code
 * getQuads(..., ModelData, RenderType)}. Fabric's Indigo renderer asks models for geometry through
 * {@code FabricBakedModel#emitBlockQuads}, so the same meshes are replayed into a {@link
 * QuadEmitter}. {@code QuadEmitter#fromVanilla(BakedQuad, RenderMaterial, Direction)} performs the
 * vertex-format and sprite conversion, which keeps IC2's quad-building code unchanged.
 */
final class QuadEmit {
  private static RenderMaterial defaultMaterial;

  private QuadEmit() {}

  static void emit(QuadEmitter emitter, BakedQuad quad) {
    RenderMaterial material = defaultMaterial();
    if (material == null) {
      // No renderer plug-in is registered; Indigo is always present in practice, but copying the
      // raw vertices and sprite keeps the model usable instead of silently dropping geometry.
      emitter.fromVanilla(quad.getVertices(), 0);
      emitter
          .spriteBake(quad.getSprite(), net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView.BAKE_NORMALIZED)
          .nominalFace(quad.getDirection())
          .emit();
      return;
    }

    emitter.fromVanilla(quad, material, quad.getDirection()).emit();
  }

  static void emitAll(QuadEmitter emitter, List<BakedQuad> quads) {
    for (BakedQuad quad : quads) {
      emit(emitter, quad);
    }
  }

  private static RenderMaterial defaultMaterial() {
    RenderMaterial cached = defaultMaterial;
    if (cached != null) {
      return cached;
    }

    Renderer renderer = RendererAccess.INSTANCE.getRenderer();
    if (renderer == null) {
      return null;
    }

    defaultMaterial = renderer.materialFinder().find();
    return defaultMaterial;
  }
}
