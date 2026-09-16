package ic2.fabric;

import ic2.core.event.EventHandler;
import ic2.core.init.BlocksItems;
import ic2.core.init.IC2Config;
import ic2.core.loot.Ic2LootNbtProviderTypes;
import ic2.core.ref.Ic2Fluids;
import java.io.IOException;
import java.nio.file.Path;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Common entrypoint, replacing {@code ic2.forge.FmlMod}.
 *
 * <p>NeoForge drove initialisation from {@code RegisterEvent} callbacks, one per registry, because
 * the registry pass order (mob effects, then blocks, then items, then sounds) determined when
 * potions, items and game events could be built. Fabric registers into the vanilla registries
 * directly during {@link #onInitialize()}, before they freeze, so that ordering is reproduced
 * explicitly here in the same sequence the NeoForge build relied on.
 */
public final class Ic2Fabric implements ModInitializer {
  private static final Logger LOGGER = LogManager.getLogger("ic2");

  @Override
  public void onInitialize() {
    loadConfigs();

    // NeoForge: EnvFluidHandlerForge registered fluid types, then fluids, in the FLUID_TYPES and
    // FLUID registry passes. FabricFluidHandler registers both immediately.
    Ic2Fluids.init();

    // NeoForge: MOB_EFFECT registers before BLOCK, so potions had to be built in that pass.
    BlocksItems.initPotions();

    // NeoForge: RegisterEvent for BLOCK ran EventHandler.onInitEarly().
    EventHandler.onInitEarly();

    // NeoForge: RegisterEvent for ITEM released the queued item registrations (fluid buckets).
    FabricFluidHandler.registerPendingItems();

    // NeoForge: RegisterEvent for LOOT_NBT_PROVIDER_TYPE.
    Ic2LootNbtProviderTypes.init();

    // NeoForge: RegisterEvent for SOUND_EVENT (game events and sounds).
    EventHandler.onInitGameEvents();

    // NeoForge: FMLCommonSetupEvent registered the game event handlers, then ran EventHandler.onInit().
    FabricLifecycle.register();
    FabricNetwork.registerCommon();
    FabricBiomeModifications.register();
    FabricLootInjections.register();
    EventHandler.onInit();

    // NeoForge: FMLLoadCompleteEvent drained the post-registry queue, then ran onInitLate().
    FabricEnvProxy.completeRegistryInit();
    EventHandler.onInitLate();
  }

  private static void loadConfigs() {
    Path configDir = FabricLoader.getInstance().getConfigDir();

    try {
      Path common = configDir.resolve("ic2.cfg");
      IC2Config.SPEC.load(common);
      IC2Config.SPEC.save();
    } catch (IOException e) {
      LOGGER.error("failed to load the IC2 common config", e);
    }
  }
}
