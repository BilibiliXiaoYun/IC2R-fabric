package ic2.fabric;

import ic2.core.block.misc.AirBlock;
import ic2.core.block.misc.ConstructionFoamBlock;
import ic2.core.block.misc.HotCoolantBlock;
import ic2.core.block.misc.HotWaterBlock;
import ic2.core.block.misc.HydrogenBlock;
import ic2.core.block.misc.PahoehoeLavaBlock;
import ic2.core.block.misc.SteamBlock;
import ic2.core.block.misc.UUMatterBlock;
import ic2.core.fluid.EnvFluidHandler;
import ic2.core.fluid.FluidBeBridge;
import ic2.core.fluid.Ic2FluidBlock;
import ic2.core.fluid.Ic2FluidItem;
import ic2.core.fluid.Ic2FluidStack;
import ic2.core.util.StackUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributeHandler;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.storage.base.ResourceAmount;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.apache.commons.lang3.mutable.Mutable;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric implementation of {@link EnvFluidHandler}.
 *
 * <p>NeoForge expressed fluids as a {@code FluidType} plus {@code BaseFlowingFluid.Properties}, and
 * exposed block and item fluid access through capabilities. Vanilla 1.21.1 has neither, and Fabric
 * uses the Transfer API instead, so this class:
 *
 * <ul>
 *   <li>defines plain vanilla {@link FlowingFluid} source/flowing pairs for each IC2 fluid,
 *   <li>records density/viscosity/temperature/light through {@link FluidVariantAttributes},
 *   <li>reads and writes block and item fluid stores through {@link FluidStorage#SIDED} and {@link
 *       FluidStorage#ITEM} inside rollback-capable transactions.
 * </ul>
 *
 * <p>Amounts cross the boundary in IC2's millibuckets and Fabric's droplets. {@link
 * FluidConstants#BUCKET} is 81000 droplets for 1000 mB, so the conversion is the exact integer
 * {@link #DROPLETS_PER_MB}; no rounding is introduced in either direction.
 */
public class FabricFluidHandler implements EnvFluidHandler {
  /** Fabric expresses fluid amounts in droplets; IC2 in millibuckets. 81000 / 1000 = 81 exactly. */
  public static final long DROPLETS_PER_MB = FluidConstants.BUCKET / 1000L;

  private static final Map<Fluid, Integer> DENSITY = new HashMap<>();
  private static final Map<Fluid, Integer> TEMPERATURE = new HashMap<>();
  private static final Map<Fluid, Integer> VISCOSITY = new HashMap<>();
  private static final Map<Fluid, Integer> LUMINOSITY = new HashMap<>();
  private static final Map<Fluid, Boolean> GASEOUS = new HashMap<>();
  private static final Map<Fluid, ResourceLocation> STILL_SPRITES = new HashMap<>();
  private static final Map<Fluid, ResourceLocation> FLOWING_SPRITES = new HashMap<>();
  private static final Map<Fluid, Integer> COLORS = new HashMap<>();

  private static final List<Runnable> PENDING_ITEM_REGISTRATIONS = new ArrayList<>();

  /** Runs item registrations that had to wait for the fluid/block registration pass. */
  public static void registerPendingItems() {
    for (Runnable runnable : PENDING_ITEM_REGISTRATIONS) {
      runnable.run();
    }

    PENDING_ITEM_REGISTRATIONS.clear();
  }

  /** {@return the still sprite declared for {@code fluid}, or {@code null}} */
  public static @Nullable ResourceLocation stillSprite(Fluid fluid) {
    return STILL_SPRITES.get(fluid);
  }

  /** {@return the flowing sprite declared for {@code fluid}, falling back to the still sprite} */
  public static @Nullable ResourceLocation flowingSprite(Fluid fluid) {
    ResourceLocation flowing = FLOWING_SPRITES.get(fluid);
    return flowing != null ? flowing : STILL_SPRITES.get(fluid);
  }

  /** {@return the tint declared for {@code fluid}, or {@code -1} when unspecified} */
  public static int color(Fluid fluid) {
    Integer color = COLORS.get(fluid);
    return color != null ? color : -1;
  }

  static FluidVariant variantOf(Ic2FluidStack stack) {
    if (stack == null || stack.isEmpty()) {
      return FluidVariant.blank();
    }

    return stack instanceof FabricFluidStack fabric
        ? fabric.variant()
        : FluidVariant.of(stack.getFluid());
  }

  static long toDroplets(int amountMb) {
    return (long) amountMb * DROPLETS_PER_MB;
  }

  static int toMb(long droplets) {
    return (int) (droplets / DROPLETS_PER_MB);
  }

  private static LiquidBlock createFluidBlock(
      String fluidName, FlowingFluid fluid, BlockBehaviour.Properties properties) {
    return switch (fluidName) {
      case "hot_coolant" -> new HotCoolantBlock(fluid, properties);
      case "air" -> new AirBlock(fluid, properties);
      case "hydrogen" -> new HydrogenBlock(fluid, properties);
      case "hot_water" -> new HotWaterBlock(fluid, properties);
      case "uu_matter" -> new UUMatterBlock(fluid, properties);
      case "construction_foam" -> new ConstructionFoamBlock(fluid, properties);
      case "steam", "superheated_steam" -> new SteamBlock(fluid, properties);
      case "pahoehoe_lava" -> new PahoehoeLavaBlock(fluid, properties);
      default -> new LiquidBlock(fluid, properties);
    };
  }

  @Override
  public EnvFluidHandler.FluidRefs createFluid(
      ResourceLocation id,
      int density,
      int viscosity,
      int luminosity,
      int temperature,
      ResourceLocation stillSpriteId,
      ResourceLocation flowingSpriteId,
      int color) {
    EnvFluidHandler.FluidRefs refs = new EnvFluidHandler.FluidRefs(null, null, null, null);

    Fluid still = new SourceFluid(refs);
    Fluid flowing = new FlowingFluidImpl(refs);
    refs.still(still);
    refs.flowing(flowing);

    Registry.register(BuiltInRegistries.FLUID, id, still);
    Registry.register(
        BuiltInRegistries.FLUID,
        ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "flowing_" + id.getPath()),
        flowing);

    DENSITY.put(still, density);
    VISCOSITY.put(still, viscosity);
    LUMINOSITY.put(still, luminosity);
    TEMPERATURE.put(still, temperature);
    GASEOUS.put(still, density < 0);
    if (stillSpriteId != null) {
      STILL_SPRITES.put(still, stillSpriteId);
    }
    if (flowingSpriteId != null) {
      FLOWING_SPRITES.put(still, flowingSpriteId);
    }
    COLORS.put(still, color);
    // Fabric normalises flowing variants onto their still fluid, so attributes only need the source.
    attachAttributes(still, viscosity, luminosity, temperature, density);

    BlockBehaviour.Properties fluidBlockProperties =
        BlockBehaviour.Properties.ofFullCopy(Blocks.WATER)
            .noLootTable()
            .noCollission()
            .randomTicks()
            .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY);
    LiquidBlock fluidBlock = createFluidBlock(id.getPath(), (FlowingFluid) still, fluidBlockProperties);
    Registry.register(
        BuiltInRegistries.BLOCK,
        ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "fluid_block_" + id.getPath()),
        fluidBlock);
    refs.block(fluidBlock);

    ResourceLocation bucketId =
        ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_bucket");
    // The bucket is an item, so it is registered in the item pass that runs after blocks.
    PENDING_ITEM_REGISTRATIONS.add(
        () -> {
          BucketItem bucket =
              new BucketItem(still, new Properties().craftRemainder(Items.BUCKET).stacksTo(1));
          Registry.register(BuiltInRegistries.ITEM, bucketId, bucket);
          refs.bucket(bucket);
        });

    return refs;
  }

  private static void attachAttributes(
      Fluid fluid, int viscosity, int luminosity, int temperature, int density) {
    FluidVariantAttributes.register(
        fluid,
        new FluidVariantAttributeHandler() {
          @Override
          public int getLuminance(FluidVariant variant) {
            return luminosity;
          }

          @Override
          public int getTemperature(FluidVariant variant) {
            return temperature;
          }

          @Override
          public int getViscosity(FluidVariant variant, @Nullable Level world) {
            return viscosity;
          }

          @Override
          public boolean isLighterThanAir(FluidVariant variant) {
            return density < 0;
          }
        });
  }

  @Override
  public Collection<Fluid> getAllFluids() {
    return BuiltInRegistries.FLUID.stream().toList();
  }

  @Override
  public int getDensity(Fluid fluid) {
    // Vanilla water is 1000 kg/m3; Fabric has no density attribute, so only IC2's own fluids are
    // recorded and everything else keeps the water default.
    return DENSITY.getOrDefault(fluid, 1000);
  }

  @Override
  public int getTemperature(Fluid fluid) {
    Integer recorded = TEMPERATURE.get(fluid);
    return recorded != null ? recorded : FluidVariantAttributes.getTemperature(FluidVariant.of(fluid));
  }

  @Override
  public boolean isGaseous(Fluid fluid) {
    Boolean recorded = GASEOUS.get(fluid);
    return recorded != null
        ? recorded
        : FluidVariantAttributes.isLighterThanAir(FluidVariant.of(fluid));
  }

  @Override
  public ResourceLocation getStillSpriteId(Fluid fluid) {
    throw new UnsupportedOperationException("client only");
  }

  @Override
  public ResourceLocation getFlowingSpriteId(Fluid fluid) {
    throw new UnsupportedOperationException("client only");
  }

  @Override
  public int getColor(Fluid fluid) {
    throw new UnsupportedOperationException("client only");
  }

  @Override
  public Ic2FluidStack createFluidStackMb(Fluid fluid, int amount, @Nullable CompoundTag nbt) {
    if (nbt != null && !nbt.isEmpty()) {
      Ic2FluidStack parsed = FabricFluidStack.fromNbt(nbt);
      if (parsed != null && !parsed.isEmpty()) {
        return parsed.copyWithAmountMb(amount);
      }
    }

    return new FabricFluidStack(FluidVariant.of(fluid), amount);
  }

  @Override
  public Ic2FluidStack getFluidStack(ItemStack stack) {
    if (StackUtil.isEmpty(stack)) {
      return null;
    }

    Ic2FluidItem fluidItem = fluidItem(stack);
    if (fluidItem != null) {
      // IC2's own containers answer directly, exactly as the NeoForge item capability wrapper did.
      // A stacked container deliberately exposes nothing, because IC2 only supports fluid
      // operations on a single cell at a time.
      return stack.getCount() == 1 ? fluidItem.getFluidStack(stack) : Ic2FluidStack.EMPTY;
    }

    Ic2FluidStack[] all = readItemTanks(stack);
    if (all == null) {
      return null;
    }

    for (Ic2FluidStack candidate : all) {
      if (!candidate.isEmpty()) {
        return candidate;
      }
    }

    return Ic2FluidStack.EMPTY;
  }

  @Override
  public Ic2FluidStack[] getFluidStacks(ItemStack stack) {
    if (StackUtil.isEmpty(stack)) {
      return null;
    }

    Ic2FluidItem fluidItem = fluidItem(stack);
    if (fluidItem != null) {
      return new Ic2FluidStack[] {
        stack.getCount() == 1 ? fluidItem.getFluidStack(stack) : Ic2FluidStack.EMPTY
      };
    }

    return readItemTanks(stack);
  }

  private static Ic2FluidStack @Nullable [] readItemTanks(ItemStack stack) {
    try (ItemSlot slot = ItemSlot.constant(stack);
        Transaction transaction = Transaction.openOuter()) {
      Storage<FluidVariant> storage = slot.storage();
      if (storage == null) {
        return null;
      }

      // Read every variant the storage exposes, mirroring the per-tank read of the capability API.
      List<Ic2FluidStack> tanks = new ArrayList<>();
      for (var view : storage.nonEmptyViews()) {
        FluidVariant variant = view.getResource();
        tanks.add(new FabricFluidStack(variant, toMb(view.getAmount())));
      }

      if (tanks.isEmpty()) {
        ResourceAmount<FluidVariant> extractable =
            StorageUtil.findExtractableContent(storage, transaction);
        tanks.add(
            extractable == null
                ? Ic2FluidStack.EMPTY
                : new FabricFluidStack(extractable.resource(), toMb(extractable.amount())));
      }

      return tanks.toArray(new Ic2FluidStack[0]);
    }
  }

  @Override
  public Ic2FluidStack readFluidStack(CompoundTag nbt) {
    return FabricFluidStack.fromNbt(nbt);
  }

  @Override
  public CompoundTag getFluidStackNbt(Ic2FluidStack fs) {
    if (fs instanceof FabricFluidStack fabric && !fabric.variant().getComponents().isEmpty()) {
      CompoundTag ret = new CompoundTag();
      fabric.toNbt(ret);
      return ret;
    }

    return null;
  }

  @Override
  public Ic2FluidStack drainMb(
      ItemStack stack, int amount, boolean simulate, @Nullable Mutable<ItemStack> newStack) {
    if (newStack != null) {
      newStack.setValue(stack);
    }

    if (amount < 0) {
      throw new IllegalArgumentException("negative amount");
    }

    if (amount == 0) {
      return Ic2FluidStack.EMPTY;
    }

    Ic2FluidItem fluidItem = fluidItem(stack);
    if (fluidItem != null) {
      return stack.getCount() == 1
          ? fluidItem.drainMb(stack, amount, simulate, newStack)
          : Ic2FluidStack.EMPTY;
    }

    try (ItemSlot slot = ItemSlot.mutable(stack);
        Transaction transaction = Transaction.openOuter()) {
      Storage<FluidVariant> storage = slot.storage();
      if (storage == null) {
        return Ic2FluidStack.EMPTY;
      }

      FluidVariant variant = StorageUtil.findExtractableResource(storage, transaction);
      if (variant == null) {
        return Ic2FluidStack.EMPTY;
      }

      long drained = storage.extract(variant, toDroplets(amount), transaction);
      if (drained <= 0) {
        return Ic2FluidStack.EMPTY;
      }

      if (!simulate) {
        transaction.commit();
      }

      if (newStack != null) {
        newStack.setValue(slot.stack());
      }

      return new FabricFluidStack(variant, toMb(drained));
    }
  }

  @Override
  public int drainMb(
      ItemStack stack, Ic2FluidStack drainFs, boolean simulate, @Nullable Mutable<ItemStack> newStack) {
    if (newStack != null) {
      newStack.setValue(stack);
    }

    if (drainFs == null) {
      throw new IllegalArgumentException("invalid drain medium");
    }

    if (drainFs.isEmpty()) {
      return 0;
    }

    Ic2FluidItem fluidItem = fluidItem(stack);
    if (fluidItem != null) {
      return stack.getCount() == 1 ? fluidItem.drainMb(stack, drainFs, simulate, newStack) : 0;
    }

    try (ItemSlot slot = ItemSlot.mutable(stack);
        Transaction transaction = Transaction.openOuter()) {
      Storage<FluidVariant> storage = slot.storage();
      if (storage == null) {
        return 0;
      }

      long drained =
          storage.extract(variantOf(drainFs), toDroplets(drainFs.getAmountMb()), transaction);
      if (drained <= 0) {
        return 0;
      }

      if (!simulate) {
        transaction.commit();
      }

      if (newStack != null) {
        newStack.setValue(slot.stack());
      }

      return toMb(drained);
    }
  }

  @Override
  public int fillMb(
      ItemStack stack, Ic2FluidStack fillFs, boolean simulate, @Nullable Mutable<ItemStack> newStack) {
    if (newStack != null) {
      newStack.setValue(stack);
    }

    if (fillFs == null) {
      throw new IllegalArgumentException("invalid fill medium");
    }

    if (fillFs.isEmpty()) {
      return 0;
    }

    Ic2FluidItem fluidItem = fluidItem(stack);
    if (fluidItem != null) {
      return stack.getCount() == 1 ? fluidItem.fillMb(stack, fillFs, simulate, newStack) : 0;
    }

    try (ItemSlot slot = ItemSlot.mutable(stack);
        Transaction transaction = Transaction.openOuter()) {
      Storage<FluidVariant> storage = slot.storage();
      if (storage == null) {
        return 0;
      }

      long filled =
          storage.insert(variantOf(fillFs), toDroplets(fillFs.getAmountMb()), transaction);
      if (filled <= 0) {
        return 0;
      }

      if (!simulate) {
        transaction.commit();
      }

      if (newStack != null) {
        newStack.setValue(slot.stack());
      }

      return toMb(filled);
    }
  }

  private static @Nullable Ic2FluidItem fluidItem(ItemStack stack) {
    return stack.getItem() instanceof Ic2FluidItem item ? item : null;
  }

  /**
   * {@return IC2's own fluid block behind {@code be}, or {@code null}}
   *
   * <p>NeoForge exposed the block side through {@code Capabilities.FluidHandler.BLOCK} and a
   * {@code BlockFluidCapImpl} wrapper around {@link Ic2FluidBlock}. IC2's own routing only needs that
   * contract, and calling it directly preserves the explicit {@code simulate} flag that the Transfer API
   * would instead express simulation as a transaction rollback and require the storage to implement
   * snapshots. Foreign block entities still go through {@link FluidStorage#SIDED}.
   */
  private static @Nullable Ic2FluidBlock blockFluidBlock(
      BlockState state, Level world, BlockPos pos, @Nullable BlockEntity be) {
    if (be instanceof FluidBeBridge bridge) {
      Ic2FluidBlock fluidBlock = bridge.getFluidBlock();
      return fluidBlock != null && fluidBlock.isFluidBlock(state, world, pos, be)
          ? fluidBlock
          : null;
    }

    if (be instanceof ic2.core.block.tileentity.Ic2TileEntity tile) {
      return tile.getComponent(ic2.core.block.comp.Fluids.class);
    }

    return null;
  }

  private static @Nullable Storage<FluidVariant> blockStorage(
      BlockState state, Level world, BlockPos pos, @Nullable BlockEntity be, @Nullable Direction side) {
    if (be == null) {
      if (state.hasBlockEntity() && world != null && pos != null) {
        be = world.getBlockEntity(pos);
      }

      if (be == null) {
        return null;
      }
    }

    // FluidHandler#isFluidBlock(state, be, side) inspects a block entity without a world context, so
    // both world and pos may be null here. Fall back to the block entity's own position; without one
    // there is nothing to look up and Fabric's lookup would throw on the null position.
    if (pos == null) {
      pos = be.getBlockPos();
      if (pos == null) {
        return null;
      }
    }

    Level level = world != null ? world : be.getLevel();
    return level == null ? null : FluidStorage.SIDED.find(level, pos, side);
  }

  @Override
  public boolean isFluidBlock(
      BlockState state, Level world, BlockPos pos, BlockEntity be, Direction side) {
    return blockFluidBlock(state, world, pos, be) != null
        || blockStorage(state, world, pos, be, side) != null;
  }

  @Override
  public Ic2FluidStack drainMb(
      BlockState state,
      Level world,
      BlockPos pos,
      BlockEntity be,
      Direction side,
      int amount,
      boolean simulate) {
    if (amount < 0) {
      throw new IllegalArgumentException("negative amount");
    }

    if (amount == 0) {
      return Ic2FluidStack.EMPTY;
    }

    Ic2FluidBlock fluidBlock = blockFluidBlock(state, world, pos, be);
    if (fluidBlock != null) {
      return fluidBlock.drainMb(state, world, pos, be, side, amount, simulate);
    }

    Storage<FluidVariant> storage = blockStorage(state, world, pos, be, side);
    if (storage == null) {
      return null;
    }

    try (Transaction transaction = Transaction.openOuter()) {
      FluidVariant variant = StorageUtil.findExtractableResource(storage, transaction);
      if (variant == null) {
        return Ic2FluidStack.EMPTY;
      }

      long drained = storage.extract(variant, toDroplets(amount), transaction);
      if (drained <= 0) {
        return Ic2FluidStack.EMPTY;
      }

      if (!simulate) {
        transaction.commit();
      }

      return new FabricFluidStack(variant, toMb(drained));
    }
  }

  @Override
  public int drainMb(
      BlockState state,
      Level world,
      BlockPos pos,
      BlockEntity be,
      Direction side,
      Ic2FluidStack drainFs,
      boolean simulate) {
    if (drainFs == null) {
      throw new IllegalArgumentException("invalid drain medium");
    }

    if (drainFs.isEmpty() || !state.hasBlockEntity()) {
      return 0;
    }

    Ic2FluidBlock fluidBlock = blockFluidBlock(state, world, pos, be);
    if (fluidBlock != null) {
      return fluidBlock.drainMb(state, world, pos, be, side, drainFs, simulate);
    }

    Storage<FluidVariant> storage = blockStorage(state, world, pos, be, side);
    if (storage == null) {
      return 0;
    }

    try (Transaction transaction = Transaction.openOuter()) {
      long drained =
          storage.extract(variantOf(drainFs), toDroplets(drainFs.getAmountMb()), transaction);
      if (drained <= 0) {
        return 0;
      }

      if (!simulate) {
        transaction.commit();
      }

      return toMb(drained);
    }
  }

  @Override
  public int fillMb(
      BlockState state,
      Level world,
      BlockPos pos,
      BlockEntity be,
      Direction side,
      Ic2FluidStack fillFs,
      boolean simulate) {
    if (fillFs == null) {
      throw new IllegalArgumentException("invalid fill medium");
    }

    if (fillFs.isEmpty() || !state.hasBlockEntity()) {
      return 0;
    }

    Ic2FluidBlock fluidBlock = blockFluidBlock(state, world, pos, be);
    if (fluidBlock != null) {
      return Math.max(fluidBlock.fillMb(state, world, pos, be, side, fillFs, simulate), 0);
    }

    Storage<FluidVariant> storage = blockStorage(state, world, pos, be, side);
    if (storage == null) {
      return 0;
    }

    try (Transaction transaction = Transaction.openOuter()) {
      long filled =
          storage.insert(variantOf(fillFs), toDroplets(fillFs.getAmountMb()), transaction);
      if (filled <= 0) {
        return 0;
      }

      if (!simulate) {
        transaction.commit();
      }

      return toMb(filled);
    }
  }

  @Override
  public Fluid getWorldFluid(BlockState state, Level world, BlockPos pos) {
    return state.getBlock() instanceof LiquidBlock ? state.getFluidState().getType() : null;
  }

  @Override
  public int getWorldFluidLevel(BlockState state, Level world, BlockPos pos) {
    return state.getBlock() instanceof LiquidBlock ? state.getValue(LiquidBlock.LEVEL) : -1;
  }

  @Override
  public Ic2FluidStack drainWorldFluid(
      BlockState state, Level world, BlockPos pos, boolean simulate) {
    if (!(state.getBlock() instanceof LiquidBlock)) {
      return null;
    }

    FluidState fluidState = state.getFluidState();
    if (!fluidState.isSource()) {
      return null;
    }

    Fluid fluid = fluidState.getType();
    if (!simulate) {
      world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    return Ic2FluidStack.create(fluid, 1000);
  }

  /**
   * Source-side vanilla fluid. Vanilla 1.21.1 has no {@code FlowingFluid.Properties}, so the
   * still/flowing/block/bucket cross-references are resolved through the {@link FluidRefs} holder
   * that {@link #createFluid} fills in.
   */
  private abstract static class Ic2FlowingFluid extends FlowingFluid {
    private final EnvFluidHandler.FluidRefs refs;

    Ic2FlowingFluid(EnvFluidHandler.FluidRefs refs) {
      this.refs = refs;
    }

    @Override
    public Fluid getFlowing() {
      return this.refs.flowing();
    }

    @Override
    public Fluid getSource() {
      return this.refs.still();
    }

    @Override
    protected boolean canConvertToSource(Level level) {
      return false;
    }

    @Override
    protected void beforeDestroyingBlock(LevelAccessor level, BlockPos pos, BlockState state) {
      BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
      Block.dropResources(state, level, pos, blockEntity);
    }

    @Override
    protected int getSlopeFindDistance(LevelReader level) {
      return 4;
    }

    @Override
    protected int getDropOff(LevelReader level) {
      return 1;
    }

    @Override
    public int getTickDelay(LevelReader level) {
      return 5;
    }

    @Override
    protected float getExplosionResistance() {
      return 100.0F;
    }

    @Override
    protected boolean canBeReplacedWith(
        FluidState state, BlockGetter level, BlockPos pos, Fluid fluid, Direction direction) {
      return direction == Direction.DOWN;
    }

    @Override
    public net.minecraft.world.item.Item getBucket() {
      BucketItem bucket = this.refs.bucket();
      return bucket != null ? bucket : Items.AIR;
    }

    @Override
    protected BlockState createLegacyBlock(FluidState state) {
      Block block = this.refs.block();
      return block == null
          ? Blocks.AIR.defaultBlockState()
          : block.defaultBlockState().setValue(LiquidBlock.LEVEL, getLegacyLevel(state));
    }

    @Override
    public boolean isSame(Fluid fluid) {
      return fluid == this.refs.still() || fluid == this.refs.flowing();
    }
  }

  private static final class SourceFluid extends Ic2FlowingFluid {
    SourceFluid(EnvFluidHandler.FluidRefs refs) {
      super(refs);
    }

    @Override
    public boolean isSource(FluidState state) {
      return true;
    }

    @Override
    public int getAmount(FluidState state) {
      return 8;
    }
  }

  private static final class FlowingFluidImpl extends Ic2FlowingFluid {
    FlowingFluidImpl(EnvFluidHandler.FluidRefs refs) {
      super(refs);
    }

    /**
     * Vanilla puts the {@code level} property only on the flowing variant (compare {@code
     * WaterFluid.Flowing}); {@code FlowingFluid#getFlowing(int, boolean)} writes it into the flowing
     * fluid's default state, so it has to be declared here.
     */
    @Override
    protected void createFluidStateDefinition(
        net.minecraft.world.level.block.state.StateDefinition.Builder<Fluid, FluidState> builder) {
      super.createFluidStateDefinition(builder);
      builder.add(LEVEL);
    }

    @Override
    public boolean isSource(FluidState state) {
      return false;
    }

    @Override
    public int getAmount(FluidState state) {
      return state.getValue(LEVEL);
    }
  }

  /**
   * A mutable one-slot item container used to run Transfer API operations on a single {@link
   * ItemStack} and read the resulting container back, replacing the container-returning behaviour
   * of NeoForge's {@code IFluidHandlerItem}.
   */
  private static final class ItemSlot extends SingleStackStorage implements AutoCloseable {
    private ItemStack stack;
    private final boolean constant;

    private ItemSlot(ItemStack stack, boolean constant) {
      this.stack = stack;
      this.constant = constant;
    }

    static ItemSlot constant(ItemStack stack) {
      return new ItemSlot(StackUtil.copyWithSize(stack, 1), true);
    }

    static ItemSlot mutable(ItemStack stack) {
      return new ItemSlot(stack.getCount() == 1 ? stack.copy() : StackUtil.copyWithSize(stack, 1), false);
    }

    @Nullable Storage<FluidVariant> storage() {
      return ContainerItemContext.ofSingleSlot(this).find(FluidStorage.ITEM);
    }

    ItemStack stack() {
      return this.stack;
    }

    @Override
    protected ItemStack getStack() {
      return this.stack;
    }

    @Override
    protected void setStack(ItemStack stack) {
      if (!this.constant) {
        this.stack = stack;
      }
    }

    @Override
    public void close() {
      // Nothing to release; the slot only owns an item stack copy.
    }
  }
}
