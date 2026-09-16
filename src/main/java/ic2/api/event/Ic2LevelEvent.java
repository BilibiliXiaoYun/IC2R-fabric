package ic2.api.event;

import java.util.Objects;
import net.minecraft.world.level.Level;

/**
 * An {@link Ic2Event} that carries the {@link Level} it relates to.
 *
 * <p>Replaces the {@code extends net.neoforged.neoforge.event.level.LevelEvent} base that IC2 used
 * on NeoForge; {@link #getLevel()} keeps the same name and semantics so existing call sites are
 * unchanged.
 */
public abstract class Ic2LevelEvent extends Ic2Event {
  private final Level level;

  protected Ic2LevelEvent(Level level) {
    this.level = Objects.requireNonNull(level, "level");
  }

  /** {@return the level this event happened in} */
  public Level getLevel() {
    return this.level;
  }
}
