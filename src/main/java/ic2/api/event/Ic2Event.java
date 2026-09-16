package ic2.api.event;

/**
 * Base type for every event IC2 itself publishes through {@link Ic2EventBus}.
 *
 * <p>On NeoForge these classes extended {@code net.neoforged.bus.api.Event} (optionally adding
 * {@code ICancellableEvent}) so that other mods could subscribe to the shared {@code
 * NeoForge.EVENT_BUS}. Fabric has no shared object bus, so IC2 carries its own equivalent. The
 * cancellation contract is unchanged: {@link #setCanceled(boolean)} records a veto that the
 * publishing call site honours, and {@link #isCanceled()} reports it.
 */
public abstract class Ic2Event {
  private boolean canceled;

  /** {@return whether a listener vetoed this event} */
  public boolean isCanceled() {
    return this.canceled;
  }

  /** Records a veto. The publishing call site is responsible for acting on it. */
  public void setCanceled(boolean canceled) {
    this.canceled = canceled;
  }
}
