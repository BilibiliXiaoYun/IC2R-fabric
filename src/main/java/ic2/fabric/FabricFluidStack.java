package ic2.fabric;

import ic2.core.fluid.Ic2FluidStack;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;

/**
 * Fabric implementation of {@link Ic2FluidStack}.
 *
 * <p>NeoForge represented a fluid stack as {@code FluidStack} (fluid + mB + data components). Fabric
 * has no such class, but {@link FluidVariant} is exactly "a fluid plus a component patch", so IC2
 * stores one alongside its own mutable mB amount. That keeps {@code hasExactFluid} semantics
 * (fluid identity <em>and</em> components) intact while the amount stays independently mutable, as
 * {@link Ic2FluidStack} requires.
 */
public final class FabricFluidStack implements Ic2FluidStack {
  private static final String NBT_FLUID = "FluidName";
  private static final String NBT_AMOUNT = "Amount";
  private static final String NBT_VARIANT = "Variant";

  private final FluidVariant variant;
  private int amountMb;

  FabricFluidStack(FluidVariant variant, int amountMb) {
    if (variant == null) {
      throw new NullPointerException("variant");
    }

    this.variant = variant;
    this.setAmountMb(amountMb);
  }

  /** {@return the Fabric variant this stack wraps} */
  public FluidVariant variant() {
    return this.variant;
  }

  @Override
  public Ic2FluidStack copy() {
    return new FabricFluidStack(this.variant, this.amountMb);
  }

  @Override
  public Fluid getFluid() {
    return this.variant.getFluid();
  }

  @Override
  public boolean hasExactFluid(Fluid fluid) {
    return this.variant.getComponents().isEmpty() && fluid == this.variant.getFluid();
  }

  @Override
  public boolean hasExactFluid(Ic2FluidStack other) {
    if (other instanceof FabricFluidStack fabric) {
      return this.variant.equals(fabric.variant);
    }

    return this.variant.getComponents().isEmpty() && other.getFluid() == this.variant.getFluid();
  }

  @Override
  public int getAmountMb() {
    return this.amountMb;
  }

  @Override
  public void setAmountMb(int amountMb) {
    if (amountMb < 0) {
      throw new IllegalArgumentException("negative amount: " + amountMb);
    }

    this.amountMb = amountMb;
  }

  @Override
  public void toNbt(CompoundTag nbt) {
    // Legacy FluidName/Amount layout, matching what FluidHandler.readFluidStack expects.
    nbt.putString(NBT_FLUID, String.valueOf(BuiltInRegistries.FLUID.getKey(this.variant.getFluid())));
    nbt.putInt(NBT_AMOUNT, this.amountMb);

    if (!this.variant.getComponents().isEmpty()) {
      FluidVariant.CODEC
          .encodeStart(NbtOps.INSTANCE, this.variant)
          .result()
          .ifPresent(encoded -> nbt.put(NBT_VARIANT, encoded));
    }
  }

  /** Decodes the layout written by {@link #toNbt(CompoundTag)}, or {@code null} when unreadable. */
  static Ic2FluidStack fromNbt(CompoundTag nbt) {
    if (nbt.contains(NBT_VARIANT)) {
      Tag encoded = nbt.get(NBT_VARIANT);
      if (encoded != null) {
        var parsed = FluidVariant.CODEC.parse(NbtOps.INSTANCE, encoded).result();
        if (parsed.isPresent()) {
          return new FabricFluidStack(parsed.get(), nbt.getInt(NBT_AMOUNT));
        }
      }
    }

    String id = nbt.getString(NBT_FLUID);
    int amount = nbt.getInt(NBT_AMOUNT);
    if (id.isEmpty() || amount < 0) {
      return null;
    }

    ResourceLocation key = ResourceLocation.tryParse(id);
    if (key == null || !BuiltInRegistries.FLUID.containsKey(key)) {
      return null;
    }

    return new FabricFluidStack(
        FluidVariant.of(BuiltInRegistries.FLUID.get(key), DataComponentPatch.EMPTY), amount);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (!(obj instanceof Ic2FluidStack other)) {
      return false;
    }

    if (this.amountMb != other.getAmountMb() || this.getFluid() != other.getFluid()) {
      return false;
    }

    return other instanceof FabricFluidStack fabric
        ? this.variant.equals(fabric.variant)
        : this.variant.getComponents().isEmpty();
  }

  @Override
  public int hashCode() {
    return this.variant.hashCode() * 31 + this.amountMb;
  }

  @Override
  public String toString() {
    return "%dx%s@%s"
        .formatted(
            this.amountMb,
            BuiltInRegistries.FLUID.getKey(this.variant.getFluid()),
            this.variant.getComponents().isEmpty() ? "(-)" : this.variant.getComponents());
  }
}
