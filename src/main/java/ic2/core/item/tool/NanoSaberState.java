package ic2.core.item.tool;

import ic2.api.item.INanoSaberState;
import ic2.core.util.StackUtil;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Per-stack nano saber state.
 *
 * <p>NeoForge exposed this through an item capability: {@code ItemStack.getCapability} cached one
 * {@link INanoSaberState} instance per stack object, so {@code energyTick} lived for as long as the
 * stack instance did while {@code active} was persisted in NBT. Fabric has no item capability
 * registry for custom APIs, so the same lifetime is kept here with an identity map keyed by the
 * stack instance, and {@code active} keeps using NBT exactly as before.
 */
public final class NanoSaberState {
  private static final String NBT_ACTIVE = "active";
  private static final Map<ItemStack, State> STATES = new WeakHashMap<>();

  private NanoSaberState() {}

  public static INanoSaberState getState(ItemStack stack) {
    if (StackUtil.isEmpty(stack) || !(stack.getItem() instanceof AbstractItemNanoSaber)) {
      return InactiveState.INSTANCE;
    }

    synchronized (STATES) {
      return STATES.computeIfAbsent(stack, State::new);
    }
  }

  public static boolean isActive(ItemStack stack) {
    return getState(stack).isActive();
  }

  public static void setActive(ItemStack stack, boolean active) {
    getState(stack).setActive(active);
  }

  public static int getEnergyTick(ItemStack stack) {
    return getState(stack).getEnergyTick();
  }

  public static void setEnergyTick(ItemStack stack, int energyTick) {
    getState(stack).setEnergyTick(energyTick);
  }

  private static final class State implements INanoSaberState {
    private final ItemStack stack;
    private int energyTick;

    State(ItemStack stack) {
      this.stack = stack;
    }

    @Override
    public boolean isActive() {
      CompoundTag nbt = StackUtil.getTag(this.stack);
      return nbt != null && nbt.getBoolean(NBT_ACTIVE);
    }

    @Override
    public void setActive(boolean active) {
      StackUtil.getOrCreateNbtData(this.stack).putBoolean(NBT_ACTIVE, active);
    }

    @Override
    public int getEnergyTick() {
      return this.energyTick;
    }

    @Override
    public void setEnergyTick(int energyTick) {
      this.energyTick = energyTick;
    }
  }

  private enum InactiveState implements INanoSaberState {
    INSTANCE;

    @Override
    public boolean isActive() {
      return false;
    }

    @Override
    public void setActive(boolean active) {}

    @Override
    public int getEnergyTick() {
      return 0;
    }

    @Override
    public void setEnergyTick(int energyTick) {}
  }
}
