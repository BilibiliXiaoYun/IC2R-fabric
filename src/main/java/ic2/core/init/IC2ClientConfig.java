package ic2.core.init;

import ic2.fabric.config.FabricConfigSpec;

public class IC2ClientConfig {
  public static final FabricConfigSpec SPEC;

  public static final Audio audio;
  public static final Misc misc;

  static {
    FabricConfigSpec.Builder b = new FabricConfigSpec.Builder();

    audio = new Audio(b);
    misc = new Misc(b);

    SPEC = b.build();
  }

  public static class Audio {
    public final FabricConfigSpec.BooleanValue enabled;
    public final FabricConfigSpec.DoubleValue volume;
    public final FabricConfigSpec.IntValue fadeDistance;
    public final FabricConfigSpec.IntValue maxSourceCount;

    Audio(FabricConfigSpec.Builder b) {
      b.push("audio");
      b.comment("Enable IC2's custom sound system.");
      enabled = b.define("enabled", true);
      b.comment("Volume of IC2's sounds, range from 0 (silent) ... 1 (100%).");
      volume = b.defineInRange("volume", 1.0, 0.0, 1.0);
      b.comment("The number of blocks the sounds attenuate over.");
      fadeDistance = b.defineInRange("fadeDistance", 16, 0, Integer.MAX_VALUE);
      b.comment(
          "Maximum number of active audio sources, only change it if you know what you're doing.");
      maxSourceCount = b.defineInRange("maxSourceCount", 32, 0, Integer.MAX_VALUE);
      b.pop();
    }
  }

  public static class Misc {
    public final FabricConfigSpec.BooleanValue hideSecretRecipes;
    public final FabricConfigSpec.BooleanValue quantumSpeedOnSprint;

    Misc(FabricConfigSpec.Builder b) {
      b.push("misc");
      b.comment("Enable hiding of secret recipes in CraftGuide/NEI.");
      hideSecretRecipes = b.define("hideSecretRecipes", true);
      b.comment(
          "Enable activation of the quantum leggings' speed boost when sprinting instead of holding the boost key.");
      quantumSpeedOnSprint = b.define("quantumSpeedOnSprint", true);
      b.pop();
    }
  }
}
