package ic2.fabric.client.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ic2.core.block.wiring.CableFoam;
import ic2.core.block.wiring.CableType;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.client.resources.model.UnbakedModel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Supplies IC2's custom block and item models.
 *
 * <p>On NeoForge these were geometry loaders registered through {@code
 * ModelEvent.RegisterGeometryLoaders} and driven by the {@code "loader"} key in each model JSON.
 * Fabric removed custom JSON model loaders, so the equivalent is a {@link ModelLoadingPlugin} that
 * reads the model JSONs up front and hands back an {@link UnbakedModel} for every id whose JSON asks
 * for one of IC2's loaders.
 *
 * <p>The model JSONs themselves are unchanged, which keeps the 388 existing files (288 cables, 98
 * block entities, one wall and one obscurator overlay) valid.
 */
@Environment(EnvType.CLIENT)
public final class Ic2ModelLoadingPlugin
    implements PreparableModelLoadingPlugin<Map<ResourceLocation, JsonObject>> {
  private static final Logger LOGGER = LogManager.getLogger("ic2");
  private static final String LOADER_PREFIX = "ic2:";
  private static final String MODELS_DIR = "models";
  private static final String JSON_SUFFIX = ".json";

  /** Registers the plugin with Fabric's model loading API. */
  public static void register() {
    PreparableModelLoadingPlugin.register(Ic2ModelLoadingPlugin::load, new Ic2ModelLoadingPlugin());
  }

  private static CompletableFuture<Map<ResourceLocation, JsonObject>> load(
      ResourceManager manager, Executor executor) {
    return CompletableFuture.supplyAsync(() -> scan(manager), executor);
  }

  private static Map<ResourceLocation, JsonObject> scan(ResourceManager manager) {
    Map<ResourceLocation, JsonObject> found = new HashMap<>();

    for (Map.Entry<ResourceLocation, Resource> entry :
        manager.listResources(MODELS_DIR, path -> path.getPath().endsWith(JSON_SUFFIX)).entrySet()) {
      ResourceLocation file = entry.getKey();
      if (!file.getNamespace().equals("ic2")) {
        continue;
      }

      try (var reader = entry.getValue().openAsReader()) {
        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
        JsonElement loader = json.get("loader");
        if (loader == null || !loader.getAsString().startsWith(LOADER_PREFIX)) {
          continue;
        }

        found.put(toModelId(file), json);
      } catch (Exception e) {
        LOGGER.error("failed to read IC2 model {}", file, e);
      }
    }

    return found;
  }

  private static ResourceLocation toModelId(ResourceLocation file) {
    String path = file.getPath();
    return ResourceLocation.fromNamespaceAndPath(
        file.getNamespace(),
        path.substring(MODELS_DIR.length() + 1, path.length() - JSON_SUFFIX.length()));
  }

  @Override
  public void onInitializeModelLoader(
      Map<ResourceLocation, JsonObject> data, ModelLoadingPlugin.Context pluginContext) {
    LOGGER.info("IC2 model loading: {} custom model definitions found", data.size());
    // Force-load every IC2 custom model so a broken one is reported during resource loading instead
    // of silently rendering as a missing model the first time it is used.
    pluginContext.addModels(new java.util.ArrayList<>(data.keySet()));
    pluginContext
        .resolveModel()
        .register(
            context -> {
              JsonObject json = data.get(context.id());
              return json == null ? null : create(json);
            });
  }

  /**
   * {@return the foam named by a cable model, or {@code null} for the JSON's {@code "none"}}
   *
   * <p>{@link CableFoam} only models the soft and hard (coloured) foams, and {@link
   * CableFoam#get(String)} returns {@code null} for {@code "none"}. That {@code null} is exactly
   * what {@code DynamicCableModel#hasFoam()} expects for a bare cable, so it is passed through
   * rather than being turned into an exception.
   */
  private static CableFoam parseFoam(String name) {
    return "none".equals(name) ? null : CableFoam.get(name);
  }

  private static UnbakedModel create(JsonObject json) {
    String loader = json.get("loader").getAsString();

    return switch (loader) {
      case "ic2:be" -> new DynamicBeModelFabric(ResourceLocation.parse(json.get("id").getAsString()));
      case "ic2:cable" ->
          new DynamicCableModelFabric(
              CableType.valueOf(json.get("type").getAsString()),
              json.get("insulation").getAsInt(),
              parseFoam(json.get("foam").getAsString()),
              json.get("active").getAsBoolean());
      case "ic2:wall" -> new WallModelFabric();
      case "ic2:mask_overlay" ->
          new MaskOverlayItemModelFabric(
              ResourceLocation.parse(json.get("base").getAsString()),
              ResourceLocation.parse(json.get("mask").getAsString()),
              json.get("scale_overlay").getAsBoolean(),
              json.get("offset").getAsFloat());
      default -> {
        LOGGER.error("unknown IC2 model loader {}", loader);
        yield null;
      }
    };
  }
}
