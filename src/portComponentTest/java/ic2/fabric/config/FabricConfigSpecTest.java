package ic2.fabric.config;

import static org.junit.jupiter.api.Assertions.*;
import ic2.core.init.IC2Config;
import ic2.core.init.IC2ClientConfig;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FabricConfigSpecTest {
  @TempDir Path directory;

  @Test void realConfigurationsLoadAndPersist() throws IOException {
    var common = directory.resolve("common.json");
    var client = directory.resolve("client.json");
    IC2Config.SPEC.load(common);
    IC2ClientConfig.SPEC.load(client);
    assertTrue(Files.size(common) > 0);
    assertEquals(1.0, IC2ClientConfig.audio.volume.get());
    assertEquals(2, IC2Config.balance.uuValues.predefined.get().size());
  }

  @Test void valuesRoundTripWithoutChangingDefaults() throws IOException {
    var b = new FabricConfigSpec.Builder();
    var amount = b.defineInRange("amount", 32, 0, 128);
    var list = b.defineListAllowEmpty("list", () -> List.of("first"), e -> e instanceof String);
    var spec = b.build();
    var file = directory.resolve("options.json");
    spec.load(file);
    amount.set(48);
    list.set(List.of("second", "third"));
    spec.save();
    amount.set(0);
    list.set(List.of());
    spec.load(file);
    assertEquals(48, amount.get());
    assertEquals(32, amount.getDefault());
    assertEquals(List.of("second", "third"), list.get());
  }

  @Test void invalidFileDoesNotPartiallyApplyOrOverwriteUserFile() throws IOException {
    var b = new FabricConfigSpec.Builder();
    var enabled = b.define("enabled", true);
    var amount = b.defineInRange("amount", 32, 0, 128);
    var spec = b.build();
    var file = directory.resolve("invalid.json");
    String invalid = "{\"enabled\":false,\"amount\":999}";
    Files.writeString(file, invalid);
    assertThrows(IOException.class, () -> spec.load(file));
    assertTrue(enabled.get());
    assertEquals(32, amount.get());
    assertEquals(invalid, Files.readString(file));
  }

  @Test void rejectsFractionalIntegersAndWrongBooleanTypes() throws IOException {
    var b = new FabricConfigSpec.Builder();
    b.defineInRange("amount", 32, 0, 128);
    b.define("enabled", true);
    var spec = b.build();
    var file = directory.resolve("invalid.json");
    for (String json : List.of("{\"amount\":1.5}", "{\"enabled\":\"false\"}")) {
      Files.writeString(file, json);
      assertThrows(IOException.class, () -> spec.load(file));
    }
  }
}
