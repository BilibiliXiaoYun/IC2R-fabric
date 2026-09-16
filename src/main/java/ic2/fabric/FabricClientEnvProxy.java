package ic2.fabric;

import ic2.core.fluid.Ic2FluidStack;
import ic2.core.proxy.ClientEnvProxy;
import java.io.File;
import java.util.Arrays;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Client registrations backed by Fabric API instead of NeoForge lifecycle events. */
@Environment(EnvType.CLIENT)
public final class FabricClientEnvProxy implements ClientEnvProxy {
  @Override public <H extends AbstractContainerMenu> void registerScreen(MenuType<H> type, ScreenFactory<H> factory) {
    MenuScreens.register(type, factory::create);
  }
  @Override public File getMinecraftDir() { return FabricLoader.getInstance().getGameDir().toFile(); }
  @Override public void registerColorProvider(BlockColor provider, Block... blocks) {
    ColorProviderRegistry.BLOCK.register(provider, blocks);
  }
  @Override public void registerColorProvider(ItemColor provider, ItemLike... items) {
    ColorProviderRegistry.ITEM.register(provider, Arrays.stream(items).map(ItemLike::asItem).toArray(Item[]::new));
  }
  @Override public void registerKeyBinding(KeyMapping binding) { KeyBindingHelper.registerKeyBinding(binding); }
  @Override public <E extends BlockEntity> void registerBer(BlockEntityType<E> type, BlockEntityRendererProvider<? super E> factory) {
    BlockEntityRendererRegistry.register(type, factory);
  }
  @Override public void registerModelPredicateProvider(ResourceLocation id, ClampedItemPropertyFunction provider) {
    ItemProperties.registerGeneric(id, provider);
  }
  @Override public void registerModelPredicateProvider(Item item, ResourceLocation id, ClampedItemPropertyFunction provider) {
    ItemProperties.register(item, id, provider);
  }
  @Override public void registerBlockLayer(RenderType layer, Block... blocks) { BlockRenderLayerMap.INSTANCE.putBlocks(layer, blocks); }
  @Override public <E extends Entity> void registerEntityRenderer(EntityType<? extends E> type, EntityRendererProvider<E> factory) {
    EntityRendererRegistry.register(type, factory);
  }
  @Override public <E extends BlockEntity> void registerBlockEntityRenderer(BlockEntityType<? extends E> type, BlockEntityRendererProvider<E> factory) {
    BlockEntityRendererRegistry.register(type, factory);
  }
  @Override public TextureAtlasSprite getFluidStillSprite(Ic2FluidStack stack) {
    var sprite = FluidVariantRendering.getSprite(FluidVariant.of(stack.getFluid()));
    return sprite != null ? sprite : Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
        .apply(MissingTextureAtlasSprite.getLocation());
  }
  @Override public int getFluidColor(Ic2FluidStack stack) { return FluidVariantRendering.getColor(FluidVariant.of(stack.getFluid())); }
  @Override public String getFluidName(Ic2FluidStack stack) { return FluidVariantAttributes.getName(FluidVariant.of(stack.getFluid())).getString(); }
}
