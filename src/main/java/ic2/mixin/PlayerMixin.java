package ic2.mixin;

import ic2.api.item.IPlayerDropHandler;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Drives {@link IPlayerDropHandler#onDroppedByPlayer(ItemStack, Player)}.
 *
 * <p>NeoForge patched this call into {@code Player#drop(ItemStack, boolean, boolean)} and cancelled
 * the drop by returning {@code null}. Vanilla 1.21.1 has no equivalent hook and Fabric ships no
 * callback for it, so IC2 injects the same call at the head of that method.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {
  @Inject(
      method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
      at = @At("HEAD"),
      cancellable = true)
  private void ic2$onDroppedByPlayer(
      ItemStack stack,
      boolean dropAround,
      boolean traceItem,
      CallbackInfoReturnable<ItemEntity> callback) {
    if (stack.isEmpty()) {
      return;
    }

    if (stack.getItem() instanceof IPlayerDropHandler handler
        && !handler.onDroppedByPlayer(stack, (Player) (Object) this)) {
      callback.setReturnValue(null);
    }
  }
}
