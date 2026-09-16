package ic2.fabric;

import com.mojang.authlib.GameProfile;
import ic2.core.block.personal.IPersonalBlock;
import ic2.core.item.EnvItemHandler;
import java.util.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** Fabric storage bridge. Simulation and transfers use rollback-capable transactions. */
public final class FabricItemHandler implements EnvItemHandler {
  private record Inventory(Storage<ItemVariant> storage, Direction side) implements AdjacentInventory {
    @Override public Direction getSide() { return side; }
  }

  private static @Nullable Inventory lookup(BlockEntity target, Direction side, GameProfile accessor) {
    if (target == null) return null;
    // Do not fall through to public storage if a personal inventory rejects its accessor.
    if (target instanceof IPersonalBlock personal && accessor != null) {
      if (!personal.permitsAccess(accessor)) return null;
      Container inventory = personal.getPrivilegedInventory(accessor);
      return inventory == null ? null : new Inventory(InventoryStorage.of(inventory, side), side);
    }
    if (target.getLevel() != null) {
      var storage = ItemStorage.SIDED.find(target.getLevel(), target.getBlockPos(), side);
      if (storage != null) return new Inventory(storage, side);
    }
    return target instanceof Container container
        ? new Inventory(InventoryStorage.of(container, side), side) : null;
  }

  private static @Nullable Inventory unwrap(AdjacentInventory inventory) {
    if (inventory == null) return null;
    if (inventory instanceof Inventory result) return result;
    throw new IllegalArgumentException("Inventory belongs to a different platform");
  }

  @Override public int fetch(BlockEntity source, ItemStack stack, boolean simulate) {
    if (stack.isEmpty()) return 0;
    long extracted = 0;
    try (var tx = Transaction.openOuter()) {
      for (var adjacent : getAdjacentInventories(source)) {
        extracted += unwrap(adjacent).storage.extract(ItemVariant.of(stack), stack.getCount() - extracted, tx);
        if (extracted == stack.getCount()) break;
      }
      if (!simulate) tx.commit();
    }
    return (int) extracted;
  }

  @Override public int deposit(BlockEntity target, Direction side, ItemStack stack, GameProfile accessor, boolean simulate) {
    return deposit(lookup(target, side, accessor), stack, simulate);
  }

  @Override public int deposit(AdjacentInventory adjacent, ItemStack stack, boolean simulate) {
    var inventory = unwrap(adjacent);
    if (inventory == null || stack.isEmpty()) return 0;
    try (var tx = Transaction.openOuter()) {
      long inserted = inventory.storage.insert(ItemVariant.of(stack), stack.getCount(), tx);
      if (!simulate) tx.commit();
      return (int) inserted;
    }
  }

  @Override public int distribute(BlockEntity source, ItemStack stack, boolean simulate) {
    if (stack.isEmpty()) return 0;
    long inserted = 0;
    try (var tx = Transaction.openOuter()) {
      for (var adjacent : getAdjacentInventories(source)) {
        inserted += unwrap(adjacent).storage.insert(ItemVariant.of(stack), stack.getCount() - inserted, tx);
        if (inserted == stack.getCount()) break;
      }
      if (!simulate) tx.commit();
    }
    return (int) inserted;
  }

  @Override public @Nullable AdjacentInventory getAdjacentInventory(BlockEntity source, Direction side) {
    if (source.getLevel() == null) return null;
    var target = source.getLevel().getBlockEntity(source.getBlockPos().relative(side));
    GameProfile accessor = source instanceof IPersonalBlock personal ? personal.getOwner() : null;
    var adjacent = lookup(target, side.getOpposite(), accessor);
    return adjacent == null ? null : new Inventory(adjacent.storage, side);
  }

  @Override public List<? extends AdjacentInventory> getAdjacentInventories(BlockEntity source) {
    var inventories = new ArrayList<Inventory>();
    var seen = Collections.newSetFromMap(new IdentityHashMap<Storage<ItemVariant>, Boolean>());
    for (var direction : Direction.values()) {
      var inventory = unwrap(getAdjacentInventory(source, direction));
      if (inventory != null && seen.add(inventory.storage)) inventories.add(inventory);
    }
    return inventories;
  }

  @Override public AdjacentInventory wrapInventory(BlockEntity target, Direction side) {
    return lookup(target, side, null);
  }

  @Override public int transfer(AdjacentInventory from, AdjacentInventory to, int maxAmount) {
    return transfer(from, to, maxAmount, stack -> true);
  }

  @Override public int transfer(AdjacentInventory from, AdjacentInventory to, int maxAmount, Predicate<ItemStack> filter) {
    var source = unwrap(from);
    var target = unwrap(to);
    if (source == null || target == null || source.storage == target.storage || maxAmount <= 0) return 0;
    try (var tx = Transaction.openOuter()) {
      long moved = StorageUtil.move(source.storage, target.storage, v -> filter.test(v.toStack()), maxAmount, tx);
      tx.commit();
      return (int) moved;
    }
  }
}
