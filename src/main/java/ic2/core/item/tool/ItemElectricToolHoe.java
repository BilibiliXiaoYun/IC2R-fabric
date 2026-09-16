package ic2.core.item.tool;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class ItemElectricToolHoe extends ItemElectricTool {
  private static final float MINING_SPEED = 16.0F;

  public ItemElectricToolHoe(Properties settings) {
    super(settings, 50, Tiers.IRON, List.of(BlockTags.MINEABLE_WITH_HOE));
    this.maxCharge = 10000;
    this.transferLimit = 100;
    this.tier = 1;
  }

  @Override
  public InteractionResult useOn(UseOnContext context) {
    ItemStack stack = context.getItemInHand();
    if (!this.canUse(stack) || context.getClickedFace() == Direction.DOWN) {
      return InteractionResult.PASS;
    }

    Level level = context.getLevel();
    BlockPos pos = context.getClickedPos();
    // NeoForge exposed BlockState#getToolModifiedState(context, ItemAbilities.HOE_TILL, false).
    // Fabric has no such hook, so the vanilla tilling table (which Fabric's TillableBlockRegistry
    // also feeds) is consulted directly; hoeItem.TILLABLES is widened by ic2.accesswidener.
    Pair<Predicate<UseOnContext>, Consumer<UseOnContext>> tillable =
        HoeItem.TILLABLES.get(level.getBlockState(pos).getBlock());
    if (tillable == null || !tillable.getFirst().test(context)) {
      return InteractionResult.PASS;
    }

    Player player = context.getPlayer();
    level.playSound(player, pos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
    if (!level.isClientSide) {
      // The vanilla consumer performs the block change and the game event itself.
      tillable.getSecond().accept(context);
      this.consumeEnergy(stack, this.operationEnergyCost, player);
    }

    return InteractionResult.SUCCESS;
  }

  @Override
  public float getDestroySpeed(ItemStack stack, BlockState state) {
    float speed = super.getDestroySpeed(stack, state);
    return speed == 1.0F ? speed : speed * MINING_SPEED / Tiers.IRON.getSpeed();
  }
}
