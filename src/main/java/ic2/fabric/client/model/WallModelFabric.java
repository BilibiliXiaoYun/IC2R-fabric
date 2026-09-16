package ic2.fabric.client.model;

import ic2.core.block.comp.Obscuration;
import ic2.core.block.misc.WallBlock;
import ic2.core.block.tileentity.TileEntityWall;
import ic2.core.item.tool.ItemObscurator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric port of {@code ic2.forge.model.WallModelForge}.
 *
 * <p>The construction walls are the one IC2 block whose model is assembled from another block's
 * model plus the obscuration overlays sprayed onto it, so the mesh depends on the block entity at
 * each position. On NeoForge that meant {@code getModelData}; on Fabric the same work happens in
 * {@link #emitBlockQuads}, which Indigo already calls per position.
 */
@Environment(EnvType.CLIENT)
public final class WallModelFabric implements UnbakedModel, BakedModel, FabricBakedModel {
  private static final float OVERLAY_OFFSET = 0.001F;

  @Override
  public Collection<ResourceLocation> getDependencies() {
    return Collections.emptyList();
  }

  @Override
  public void resolveParents(Function<ResourceLocation, UnbakedModel> resolver) {}

  @Override
  public BakedModel bake(
      ModelBaker bakery,
      Function<Material, TextureAtlasSprite> spriteGetter,
      ModelState modelTransform) {
    return this;
  }

  private static List<BakedQuad>[] buildDefaultMesh(DyeColor color) {
    Block wallBlock = WallBlock.get(color);
    if (wallBlock == null) {
      wallBlock = WallBlock.get(WallBlock.DEFAULT_COLOR);
    }

    return buildMeshForState(wallBlock.defaultBlockState(), null);
  }

  private static List<BakedQuad>[] buildMesh(BlockState state, TileEntityWall wall) {
    DyeColor color = wall.getColor();
    Block wallBlock = WallBlock.get(color);
    if (wallBlock == null) {
      wallBlock = WallBlock.get(WallBlock.DEFAULT_COLOR);
    }

    BlockState baseState = wallBlock.defaultBlockState();
    Obscuration obscuration = wall.getComponent(Obscuration.class);
    Obscuration.ObscurationData[] obscurations =
        obscuration != null ? obscuration.getRenderState() : null;
    return buildMeshForState(baseState, obscurations);
  }

  private static List<BakedQuad>[] buildMeshForState(
      BlockState baseState, Obscuration.ObscurationData[] obscurations) {
    BakedModel baseModel =
        Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getBlockModel(baseState);
    RandomSource rand = RandomSource.create(42L);
    List<BakedQuad>[] mesh = new List[6];

    for (Direction side : Direction.values()) {
      List<BakedQuad> faceQuads = new ArrayList<>(baseModel.getQuads(baseState, side, rand));
      Obscuration.ObscurationData data = obscurations != null ? obscurations[side.ordinal()] : null;
      if (data != null) {
        ItemObscurator.ObscuredRenderInfo renderInfo =
            ItemObscurator.getRenderInfo(data.state(), data.side());
        if (renderInfo != null && renderInfo.uvs.length == data.colorMultipliers().length * 4) {
          for (int texture = 0; texture < data.colorMultipliers().length; texture++) {
            BakedQuad overlay =
                OverlayRenderUtil.applyBlockOverlay(
                    OverlayRenderUtil.createBlockOverlayQuad(side, OVERLAY_OFFSET),
                    renderInfo,
                    texture,
                    data.colorMultipliers()[texture],
                    side);
            faceQuads.add(overlay);
          }
        }
      }

      mesh[side.ordinal()] = faceQuads.isEmpty() ? Collections.emptyList() : faceQuads;
    }

    return mesh;
  }

  private static TextureAtlasSprite resolveParticleIcon(TileEntityWall wall) {
    Obscuration obscuration = wall.getComponent(Obscuration.class);
    Obscuration.ObscurationData[] obscurations =
        obscuration != null ? obscuration.getRenderState() : null;
    Obscuration.ObscurationData topData =
        obscurations != null ? obscurations[Direction.UP.ordinal()] : null;
    if (topData != null) {
      ItemObscurator.ObscuredRenderInfo renderInfo =
          ItemObscurator.getRenderInfo(topData.state(), topData.side());
      if (renderInfo != null && renderInfo.sprites.length > 0) {
        return renderInfo.sprites[0];
      }
    }

    Block wallBlock = WallBlock.get(wall.getColor());
    return Minecraft.getInstance()
        .getBlockRenderer()
        .getBlockModelShaper()
        .getBlockModel(wallBlock.defaultBlockState())
        .getParticleIcon();
  }

  @Override
  public void emitBlockQuads(
      BlockAndTintGetter world,
      BlockState state,
      BlockPos pos,
      Supplier<RandomSource> randomSupplier,
      RenderContext context) {
    List<BakedQuad>[] mesh =
        world.getBlockEntity(pos) instanceof TileEntityWall wall
            ? buildMesh(state, wall)
            : buildDefaultMesh(WallBlock.DEFAULT_COLOR);

    var emitter = context.getEmitter();
    for (List<BakedQuad> quads : mesh) {
      QuadEmit.emitAll(emitter, quads);
    }
  }

  @Override
  public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
    return Collections.emptyList();
  }

  @Override
  public boolean useAmbientOcclusion() {
    return true;
  }

  @Override
  public boolean isGui3d() {
    return true;
  }

  @Override
  public boolean usesBlockLight() {
    return true;
  }

  @Override
  public boolean isCustomRenderer() {
    return false;
  }

  @Override
  public TextureAtlasSprite getParticleIcon() {
    return Minecraft.getInstance()
        .getBlockRenderer()
        .getBlockModelShaper()
        .getBlockModel(WallBlock.get(WallBlock.DEFAULT_COLOR).defaultBlockState())
        .getParticleIcon();
  }

  @Override
  public ItemTransforms getTransforms() {
    return ItemTransforms.NO_TRANSFORMS;
  }

  @Override
  public ItemOverrides getOverrides() {
    return ItemOverrides.EMPTY;
  }
}
