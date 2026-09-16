package ic2.api.item;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Lets an item react to (and veto) the player dropping it.
 *
 * <p>NeoForge provided this as {@code Item#onDroppedByPlayer(ItemStack, Player)}. Vanilla 1.21.1 has
 * no such hook and Fabric has no callback for it, so IC2 declares the contract itself and injects
 * the call into {@code Player#drop(ItemStack, boolean, boolean)} from {@code ic2.mixin.PlayerMixin}.
 */
public interface IPlayerDropHandler {
  /**
   * Called when {@code player} drops {@code stack}.
   *
   * @return {@code false} to cancel the drop
   */
  boolean onDroppedByPlayer(ItemStack stack, Player player);
}
