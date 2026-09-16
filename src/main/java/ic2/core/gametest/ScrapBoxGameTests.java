package ic2.core.gametest;

import ic2.api.recipe.Recipes;
import ic2.core.ref.Ic2Items;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

public final class ScrapBoxGameTests {
  private static final String EMPTY = "ic2:gametest/empty3x3x3";

  // Public because Fabric Loader instantiates every "fabric-gametest" entrypoint.
  public ScrapBoxGameTests() {}

  @GameTest(template = EMPTY)
  public static void rightClickConsumesScrapBoxAndDropsReward(GameTestHelper helper) {
    helper.assertTrue(Recipes.scrapboxDrops != null, "scrap box drops should be initialized");

    // The in-level mock player is required here: the reward is spawned as an ItemEntity, so the
    // player has to be in the level for the drop to land inside this test's structure. It also has
    // to be switched to survival — mock players are created in creative mode, and creative players
    // do not consume the scrap box, so nothing would be dropped.
    ServerPlayer player = helper.makeMockServerPlayerInLevel();
    player.setGameMode(GameType.SURVIVAL);
    ItemStack scrapBoxes = new ItemStack(Ic2Items.SCRAP_BOX, 2);
    player.setItemInHand(InteractionHand.MAIN_HAND, scrapBoxes);

    InteractionResultHolder<ItemStack> result =
        scrapBoxes.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

    helper.assertTrue(
        result.getResult().consumesAction(), "right-clicking a scrap box should succeed");
    helper.assertValueEqual(scrapBoxes.getCount(), 1, "remaining scrap box count");

    helper.succeedWhen(
        () ->
            helper.assertTrue(
                !helper
                    .getLevel()
                    .getEntitiesOfClass(
                        ItemEntity.class,
                        player.getBoundingBox().inflate(2.0),
                        entity -> !entity.getItem().isEmpty())
                    .isEmpty(),
                "right-clicking a scrap box should spawn a reward"));
  }
}
