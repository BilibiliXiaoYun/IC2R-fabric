package ic2.fabric;

import java.util.Map;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;

/**
 * Injects IC2's chest loot into the vanilla structure chests.
 *
 * <p>NeoForge did this with a global loot modifier ({@code ic2:inject}, registered through
 * {@code data/neoforge/loot_modifiers/global_loot_modifiers.json}), which ran IC2's matching loot
 * table and appended its items. Fabric has no global loot modifiers, so the same twelve
 * substitutions are registered as a {@link LootTableEvents#MODIFY} listener that appends a pool
 * referring to IC2's table.
 *
 * <p>The NeoForge modifier declared no conditions, so neither does this listener.
 */
public final class FabricLootInjections {
  private static final Map<ResourceLocation, ResourceKey<LootTable>> INJECTIONS =
      Map.ofEntries(
          entry(BuiltInLootTables.ABANDONED_MINESHAFT, "chests/abandoned_mineshaft"),
          entry(BuiltInLootTables.DESERT_PYRAMID, "chests/desert_pyramid"),
          entry(BuiltInLootTables.END_CITY_TREASURE, "chests/end_city_treasure"),
          entry(BuiltInLootTables.IGLOO_CHEST, "chests/igloo_chest"),
          entry(BuiltInLootTables.JUNGLE_TEMPLE, "chests/jungle_temple"),
          entry(BuiltInLootTables.NETHER_BRIDGE, "chests/nether_bridge"),
          entry(BuiltInLootTables.SIMPLE_DUNGEON, "chests/simple_dungeon"),
          entry(BuiltInLootTables.SPAWN_BONUS_CHEST, "chests/spawn_bonus_chest"),
          entry(BuiltInLootTables.STRONGHOLD_CORRIDOR, "chests/stronghold_corridor"),
          entry(BuiltInLootTables.STRONGHOLD_CROSSING, "chests/stronghold_crossing"),
          entry(BuiltInLootTables.STRONGHOLD_LIBRARY, "chests/stronghold_library"),
          entry(BuiltInLootTables.VILLAGE_TOOLSMITH, "chests/village_toolsmith"));

  private FabricLootInjections() {}

  private static Map.Entry<ResourceLocation, ResourceKey<LootTable>> entry(
      ResourceKey<LootTable> vanilla, String ic2Path) {
    return Map.entry(
        vanilla.location(),
        ResourceKey.create(
            Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath("ic2", ic2Path)));
  }

  /** Registers the loot table injection listener. */
  public static void register() {
    LootTableEvents.MODIFY.register(
        (key, tableBuilder, source, registries) -> {
          ResourceKey<LootTable> injection = INJECTIONS.get(key.location());
          if (injection == null) {
            return;
          }

          tableBuilder.withPool(LootPool.lootPool().add(NestedLootTable.lootTableReference(injection)));
        });
  }
}
