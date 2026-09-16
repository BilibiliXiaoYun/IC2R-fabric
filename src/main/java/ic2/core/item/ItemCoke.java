package ic2.core.item;

import net.minecraft.world.item.Item;

public class ItemCoke extends Item {
  public ItemCoke() {
    super(new Properties().stacksTo(64));
  }

  // NeoForge let items answer Item#getBurnTime themselves; vanilla 1.21.1 has no such hook, so the
  // 3200-tick fuel value is registered with Fabric's FuelRegistry from Ic2Items instead.
}
