package ic2.fabric;

import ic2.core.event.EventHandler;
import ic2.core.event.TickHandler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionResult;
import org.jetbrains.annotations.Nullable;

/** World/chunk lifetime and interaction hooks; player-specific mixins are separate. */
public final class FabricLifecycle {
  private static MinecraftServer server;
  private FabricLifecycle() {}
  public static @Nullable MinecraftServer getServer() { return server; }

  public static void register() {
    ServerLifecycleEvents.SERVER_STARTING.register(current -> server = current);
    ServerLifecycleEvents.SERVER_STARTED.register(EventHandler::onServerStart);
    ServerLifecycleEvents.SERVER_STOPPED.register(current -> server = null);
    ServerWorldEvents.LOAD.register((current, world) -> EventHandler.onWorldLoad(world));
    ServerWorldEvents.UNLOAD.register((current, world) -> EventHandler.onWorldUnload(world));
    ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> EventHandler.onChunkLoad(chunk));
    ServerChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> EventHandler.onChunkUnload(chunk));
    // NeoForge fired PlayerTickEvent.Pre/Post from Player#tick. Fabric has no player tick event, so
    // the per-player hooks are driven from the world tick instead: the start hook runs before the
    // level's entities tick and the post hook after, which is where Pre/Post sat. Without this the
    // jetpack never flew and the electric fence never shocked anything.
    ServerTickEvents.START_WORLD_TICK.register(
        world -> {
          TickHandler.onWorldTickStart(world);
          for (net.minecraft.server.level.ServerPlayer player : world.players()) {
            EventHandler.onPlayerTickStart(player);
          }
        });
    ServerTickEvents.END_WORLD_TICK.register(
        world -> {
          for (net.minecraft.server.level.ServerPlayer player : world.players()) {
            EventHandler.onPlayerTick(player);
          }
          TickHandler.onWorldTickEnd(world);
        });
    ServerTickEvents.START_SERVER_TICK.register(current -> TickHandler.onServerTick());
    ServerPlayConnectionEvents.DISCONNECT.register((handler, current) -> EventHandler.onPlayerLogout(handler.player));
    PlayerBlockBreakEvents.BEFORE.register(
        (level, player, pos, state, blockEntity) -> {
          if (state.getBlock() instanceof ic2.api.block.IPlayerBreakInterceptor interceptor
              && interceptor.onPlayerBreak(state, level, pos, player)) {
            return false;
          }

          return EventHandler.beforeBlockBreak(level, player, pos, state, blockEntity);
        });
    PlayerBlockBreakEvents.AFTER.register(EventHandler::afterBlockBreak);
    // NeoForge called Item#onItemUseFirst(stack, context) before the block's own use. Fabric's
    // UseBlockCallback runs at the same point, so PriorityUsableItem items are dispatched here.
    UseBlockCallback.EVENT.register(
        (player, world, hand, hitResult) -> {
          net.minecraft.world.item.ItemStack stack = player.getItemInHand(hand);
          if (stack.getItem() instanceof ic2.core.item.PriorityUsableItem usable) {
            net.minecraft.world.InteractionResult result =
                usable.onItemUseFirst(
                    stack,
                    new net.minecraft.world.item.context.UseOnContext(player, hand, hitResult));
            if (result != net.minecraft.world.InteractionResult.PASS) {
              return result;
            }
          }

          return net.minecraft.world.InteractionResult.PASS;
        });
    AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
      if (player.isSpectator()) return InteractionResult.PASS;
      if (EventHandler.onEntitySwingHand(player, hand)) return InteractionResult.FAIL;
      return EventHandler.onBlockStartBreak(player, world, hand, pos, direction);
    });
    AttackEntityCallback.EVENT.register((player, world, hand, target, hit) ->
        player.isSpectator() || EventHandler.onAttackEntity(player, target)
            ? InteractionResult.PASS : InteractionResult.FAIL);
    UseEntityCallback.EVENT.register((player, world, hand, target, hit) ->
        !player.isSpectator() && EventHandler.onEntityInteract(player, hand, target)
            ? InteractionResult.SUCCESS : InteractionResult.PASS);
  }
}
