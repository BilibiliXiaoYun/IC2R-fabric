package ic2.api.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lets a block decide what happens when a player breaks it, including vetoing the removal.
 *
 * <p>NeoForge provided this through {@code Block#onDestroyedByPlayer(BlockState, Level, BlockPos,
 * Player, boolean, FluidState)}, whose {@code false} return cancelled the vanilla removal. Vanilla
 * 1.21.1 replaced that hook with {@code playerWillDestroy}, which cannot cancel removal, so IC2
 * exposes its own contract and drives it from the Fabric {@code PlayerBlockBreakEvents.BEFORE}
 * callback.
 */
public interface IPlayerBreakInterceptor {
  /**
   * Called before vanilla removes the block.
   *
   * @return {@code true} when this block handled the break itself and the vanilla removal must be
   *     cancelled; {@code false} to let the normal break continue
   */
  boolean onPlayerBreak(BlockState state, Level level, BlockPos pos, Player player);
}
