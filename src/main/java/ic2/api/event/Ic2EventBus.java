package ic2.api.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Dispatcher for IC2's own API events.
 *
 * <p>NeoForge let IC2 publish these on the shared {@code NeoForge.EVENT_BUS}, where listeners were
 * discovered through {@code @SubscribeEvent} methods. Fabric ships no equivalent object bus, so IC2
 * provides its own. Listeners are ordered by {@link Priority} and then registration order, which
 * mirrors the ordering IC2 relied on for its retexture, explosion and laser events.
 *
 * <p>{@link #post(Ic2Event)} returns the event instance, because every ported call site inspects
 * the mutated event (and its cancellation state) after publishing.
 */
public final class Ic2EventBus {
  /** Shared bus used for IC2's API events. */
  public static final Ic2EventBus INSTANCE = new Ic2EventBus();

  /** Listener ordering, mirroring the NeoForge priorities IC2 used. */
  public enum Priority {
    HIGHEST,
    HIGH,
    NORMAL,
    LOW,
    LOWEST
  }

  private record Listener<T extends Ic2Event>(
      Class<T> type, Priority priority, Consumer<? super T> action, long sequence) {}

  private final Map<Class<?>, List<Listener<?>>> listeners = new ConcurrentHashMap<>();
  private final java.util.concurrent.atomic.AtomicLong sequence =
      new java.util.concurrent.atomic.AtomicLong();
  private final Map<Class<?>, List<Listener<?>>> postCache = new ConcurrentHashMap<>();

  private Ic2EventBus() {}

  /**
   * Registers a listener for {@code type} at {@link Priority#NORMAL}.
   *
   * @return a handle that removes the listener again
   */
  public <T extends Ic2Event> Runnable register(Class<T> type, Consumer<? super T> action) {
    return register(type, Priority.NORMAL, action);
  }

  /**
   * Registers a listener for {@code type}, invoked in priority then registration order.
   *
   * @return a handle that removes the listener again
   */
  public <T extends Ic2Event> Runnable register(
      Class<T> type, Priority priority, Consumer<? super T> action) {
    Listener<T> listener = new Listener<>(type, priority, action, this.sequence.getAndIncrement());
    this.listeners.computeIfAbsent(type, ignored -> new CopyOnWriteArrayList<>()).add(listener);
    this.postCache.clear();
    return () -> {
      List<Listener<?>> registered = this.listeners.get(type);
      if (registered != null) {
        registered.remove(listener);
        this.postCache.clear();
      }
    };
  }

  /**
   * Publishes {@code event} to every listener whose registered type is assignable from the event's
   * runtime class.
   *
   * @return the same event instance, for inspection after publication
   */
  @SuppressWarnings("unchecked")
  public <T extends Ic2Event> T post(T event) {
    List<Listener<?>> matching =
        this.postCache.computeIfAbsent(event.getClass(), this::collectListeners);
    for (Listener<?> listener : matching) {
      ((Consumer<T>) listener.action()).accept(event);
    }

    return event;
  }

  /** {@return whether any listener is registered for {@code event}, useful to skip event setup} */
  public boolean hasListeners(Ic2Event event) {
    return !this.postCache.computeIfAbsent(event.getClass(), this::collectListeners).isEmpty();
  }

  private List<Listener<?>> collectListeners(Class<?> eventClass) {
    List<Listener<?>> matching = new ArrayList<>();

    for (Class<?> current = eventClass; current != null; current = current.getSuperclass()) {
      List<Listener<?>> registered = this.listeners.get(current);
      if (registered != null) {
        matching.addAll(registered);
      }
    }

    matching.sort(
        java.util.Comparator.comparingInt((Listener<?> l) -> l.priority().ordinal())
            .thenComparingLong(l -> l.sequence()));
    return Collections.unmodifiableList(matching);
  }
}
