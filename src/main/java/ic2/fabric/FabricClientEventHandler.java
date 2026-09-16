package ic2.fabric;

import ic2.core.event.EventHandlerClient;
import ic2.core.event.TickHandler;
import ic2.core.proxy.SideProxyClient;
import ic2.core.sound.DeferredSoundOps;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Client-side event wiring, replacing {@code ic2.forge.ClientEventHandlerForge}.
 *
 * <p>NeoForge delivered all of these through its client event bus. Fabric ships callbacks for the
 * tick, HUD, connection and client-world events, which are registered here; the three hooks Fabric
 * has no callback for (fog density, fog colour and sound playback) are driven by the mixins under
 * {@code ic2.mixin.client}.
 *
 * <p>Four NeoForge hooks are deliberately left unwired because their handler bodies are empty in
 * this codebase and Fabric has no equivalent callback:
 * {@code RenderLivingEvent.Pre/Post} ({@code EventHandlerClient.livingEntityPreRender/PostRender}
 * are empty), {@code RenderHighlightEvent.Block} ({@code onDrawBlockHighlight} is a stub and
 * {@code onDrawBlockHighlightLast} always returns {@code false}) and {@code ScreenEvent.Init.Post}
 * ({@code onGuiCreate} is empty, and Fabric exposes no public way to add a screen widget after
 * init).
 */
@Environment(EnvType.CLIENT)
public final class FabricClientEventHandler {
  private FabricClientEventHandler() {}

  /** Registers every client callback Fabric provides for the handlers IC2 needs. */
  public static void register() {
    // NeoForge: ClientTickEvent.Pre / ClientTickEvent.Post. The player hooks are appended because
    // Fabric has no player tick event; on NeoForge PlayerTickEvent fired for the client player too,
    // which is what drives the jetpack's sounds and particles.
    ClientTickEvents.START_CLIENT_TICK.register(
        client -> {
          TickHandler.onClientTick();
          if (client.player != null) {
            ic2.core.event.EventHandler.onPlayerTickStart(client.player);
          }
        });
    ClientTickEvents.END_CLIENT_TICK.register(
        client -> {
          if (client.player != null) {
            ic2.core.event.EventHandler.onPlayerTick(client.player);
          }
          DeferredSoundOps.flush();
        });

    // NeoForge: RenderGuiLayerEvent.Post for VanillaGuiLayers.HOTBAR.
    HudRenderCallback.EVENT.register(
        (guiGraphics, tickCounter) -> EventHandlerClient.onRenderHotBar(guiGraphics));

    // NeoForge: PlayerEvent.PlayerLoggedInEvent, client side only.
    ClientPlayConnectionEvents.JOIN.register(
        (handler, sender, client) -> {
          if (client.player != null) {
            EventHandlerClient.onClientPlayerJoin(client.player);
          }
        });

    // NeoForge had two hooks here (PlayerLoggedOutEvent and ClientPlayerNetworkEvent.LoggingOut);
    // Fabric has a single disconnect callback, which is the reliable one for dedicated servers.
    ClientPlayConnectionEvents.DISCONNECT.register(
        (handler, client) -> EventHandlerClient.onDisconnect());

    // NeoForge: LevelEvent.Load, client levels only. The single-tick deferral is kept so the player
    // is fully attached before the join hook runs.
    ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register(
        (client, world) -> requestClientJoin(client, world));
  }

  private static void requestClientJoin(net.minecraft.client.Minecraft client, ClientLevel world) {
    TickHandler.requestSingleWorldTick(
        world,
        loadedWorld -> {
          var current = client.player;
          if (SideProxyClient.mc.player != null && current != null && current.level() == loadedWorld) {
            EventHandlerClient.onClientPlayerJoin(current);
          }
        });
  }
}
