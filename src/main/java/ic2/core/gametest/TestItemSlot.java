package ic2.core.gametest;

import java.util.Arrays;
import net.minecraft.world.item.ItemStack;

/**
 * Minimal slot array with vanilla insertion semantics, for tests that need to observe stacking.
 *
 * <p>NeoForge's {@code ItemStackHandler} was used as a throwaway fixture in a few game tests. Fabric's
 * Transfer API has no equivalent "insert and hand back the remainder" helper, and these tests are
 * really asserting vanilla stacking rules (including the per-stack {@code
 * minecraft:max_stack_size} component that IC2's crop seeds rely on), so the same rules are applied
 * directly here. The method names mirror {@code ItemStackHandler} so the test bodies stay unchanged.
 */
final class TestItemSlot {
  private final ItemStack[] slots;

  TestItemSlot(int size) {
    this.slots = new ItemStack[size];
    Arrays.fill(this.slots, ItemStack.EMPTY);
  }

  /** Inserts into {@code slot}, returning whatever did not fit. */
  ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
    if (stack.isEmpty()) {
      return ItemStack.EMPTY;
    }

    ItemStack current = this.slots[slot];
    if (current.isEmpty()) {
      if (!simulate) {
        this.slots[slot] = stack.copy();
      }

      return ItemStack.EMPTY;
    }

    if (!ItemStack.isSameItemSameComponents(current, stack)) {
      return stack;
    }

    // getMaxStackSize() honours the per-stack max_stack_size component.
    int room = Math.min(current.getMaxStackSize(), stack.getMaxStackSize()) - current.getCount();
    int moved = Math.min(room, stack.getCount());
    if (moved <= 0) {
      return stack;
    }

    if (!simulate) {
      current.grow(moved);
    }

    ItemStack remainder = stack.copy();
    remainder.shrink(moved);
    return remainder;
  }

  ItemStack getStackInSlot(int slot) {
    return this.slots[slot];
  }
}
