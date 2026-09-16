package ic2.fabric.config;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Loader-independent configuration with validated values and atomic JSON persistence. */
public final class FabricConfigSpec {
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private final Map<String, ConfigValue<?>> values;
  private Path file;

  private FabricConfigSpec(Map<String, ConfigValue<?>> values) {
    this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }

  public synchronized void load(Path file) throws IOException {
    JsonObject root;
    if (Files.exists(file)) {
      try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
        var parsed = JsonParser.parseReader(reader);
        if (!parsed.isJsonObject()) throw new IOException("Expected JSON object in " + file);
        root = parsed.getAsJsonObject();
      } catch (JsonParseException | IllegalStateException e) {
        throw new IOException("Invalid IC2 configuration: " + file, e);
      }
    } else root = new JsonObject();
    // Validate all entries before applying any, so a bad file cannot leave partial state.
    Map<ConfigValue<?>, Object> decoded = new LinkedHashMap<>();
    for (var entry : values.entrySet()) {
      var value = entry.getValue();
      var json = root.get(entry.getKey());
      try { decoded.put(value, json == null ? value.defaultValue : value.decode(json)); }
      catch (RuntimeException e) { throw new IOException("Invalid IC2 option: " + entry.getKey(), e); }
    }
    decoded.forEach(ConfigValue::assign);
    this.file = file;
    if (!Files.exists(file)) save();
  }

  public synchronized void save() throws IOException {
    if (file == null) throw new IllegalStateException("Configuration has not been loaded");
    var root = new JsonObject();
    values.forEach((key, value) -> root.add(key, GSON.toJsonTree(value.get())));
    Files.createDirectories(file.toAbsolutePath().getParent());
    Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), "ic2-config-", ".tmp");
    try {
      Files.writeString(temporary, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
      try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
      catch (AtomicMoveNotSupportedException e) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
    } finally { Files.deleteIfExists(temporary); }
  }

  public static class ConfigValue<T> implements Supplier<T> {
    private final T defaultValue;
    private final Predicate<Object> validator;
    private T value;
    ConfigValue(T value, Predicate<Object> validator) {
      this.defaultValue = value;
      this.value = value;
      this.validator = validator;
    }
    public T get() { return value; }
    public T getDefault() { return defaultValue; }
    public void set(T value) { assign(value); }
    @SuppressWarnings("unchecked")
    private void assign(Object value) {
      if (!validator.test(value)) throw new IllegalArgumentException("Invalid configuration value: " + value);
      this.value = (T) (value instanceof List<?> list ? List.copyOf(list) : value);
    }
    private Object decode(JsonElement json) {
      Object result;
      if (defaultValue instanceof List<?>) {
        if (!json.isJsonArray()) throw new IllegalArgumentException("Expected list");
        var list = new ArrayList<Object>();
        for (var element : json.getAsJsonArray()) {
          if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Expected string list");
          list.add(element.getAsString());
        }
        result = list;
      } else {
        if (!json.isJsonPrimitive()) throw new IllegalArgumentException("Expected primitive");
        var primitive = json.getAsJsonPrimitive();
        if (defaultValue instanceof Boolean && primitive.isBoolean()) result = primitive.getAsBoolean();
        else if (defaultValue instanceof Integer && primitive.isNumber()) result = primitive.getAsBigDecimal().intValueExact();
        else if (defaultValue instanceof Double && primitive.isNumber()) result = primitive.getAsDouble();
        else if (defaultValue instanceof String && primitive.isString()) result = primitive.getAsString();
        else throw new IllegalArgumentException("Wrong value type");
      }
      if (!validator.test(result)) throw new IllegalArgumentException("Value outside allowed range");
      return result;
    }
  }
  public static final class BooleanValue extends ConfigValue<Boolean> {
    BooleanValue(boolean value) { super(value, v -> v instanceof Boolean); }
  }
  public static final class IntValue extends ConfigValue<Integer> {
    IntValue(int value, int min, int max) { super(value, v -> v instanceof Integer n && n >= min && n <= max); }
  }
  public static final class DoubleValue extends ConfigValue<Double> {
    DoubleValue(double value, double min, double max) {
      super(value, v -> v instanceof Double n && Double.isFinite(n) && n >= min && n <= max);
    }
  }
  public static final class Builder {
    private final Deque<String> path = new ArrayDeque<>();
    private final Map<String, ConfigValue<?>> values = new LinkedHashMap<>();
    public Builder push(String name) { path.addLast(name); return this; }
    public Builder pop() { path.removeLast(); return this; }
    public Builder comment(String... comments) { return this; }
    private <V extends ConfigValue<?>> V add(String name, V value) {
      String key = path.isEmpty() ? name : String.join(".", path) + "." + name;
      if (values.putIfAbsent(key, value) != null) throw new IllegalArgumentException("Duplicate key: " + key);
      return value;
    }
    public BooleanValue define(String name, boolean value) { return add(name, new BooleanValue(value)); }
    public ConfigValue<String> define(String name, String value) { return add(name, new ConfigValue<>(value, v -> v instanceof String)); }
    public IntValue defineInRange(String name, int value, int min, int max) { return add(name, new IntValue(value, min, max)); }
    public DoubleValue defineInRange(String name, double value, double min, double max) { return add(name, new DoubleValue(value, min, max)); }
    public <T> ConfigValue<List<? extends T>> defineListAllowEmpty(String name, Supplier<List<T>> defaults, Predicate<Object> validator) {
      return add(name, new ConfigValue<>(List.copyOf(defaults.get()), v -> v instanceof List<?> list && list.stream().allMatch(validator)));
    }
    public FabricConfigSpec build() {
      if (!path.isEmpty()) throw new IllegalStateException("Unclosed configuration section");
      return new FabricConfigSpec(values);
    }
  }
}
