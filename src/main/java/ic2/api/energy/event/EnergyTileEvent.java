package ic2.api.energy.event;

import ic2.api.energy.EnergyNet;
import ic2.api.energy.tile.IEnergyTile;
import ic2.api.event.Ic2LevelEvent;

public class EnergyTileEvent extends Ic2LevelEvent {
  public final IEnergyTile tile;

  public EnergyTileEvent(IEnergyTile tile) {
    super(EnergyNet.instance.getWorld(tile));
    if (this.getLevel() == null) {
      throw new NullPointerException("world is null");
    }

    this.tile = tile;
  }
}
