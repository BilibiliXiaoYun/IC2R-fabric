package ic2.core;

import com.google.common.base.Charsets;
import com.mojang.authlib.GameProfile;
import ic2.core.util.Util;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class Ic2Player {
  public static Player get(Level world) {
    return world instanceof ServerLevel
        ? IC2.envProxy.createFakePlayer((ServerLevel) world, getGameProfile(Util.getDimId(world)))
        : null;
  }

  private static GameProfile getGameProfile(ResourceLocation dim) {
    // The name has to be a legal filename. Fabric's FakePlayer is a real ServerPlayer, and building
    // one asks PlayerList for a stats counter whose file is named after Player#getStringUUID(),
    // which returns the profile name. A raw resource location contains ':' (and possibly '/'), so a
    // name like "[IC2 minecraft:overworld]" made that lookup throw InvalidPathException and took the
    // server down. Every caller of Ic2Player.get was affected: the miner, the laser, StackUtil and
    // Util.
    String name = "[IC2 " + dim.toString().replace(':', '_').replace('/', '_') + "]";
    UUID uuid = UUID.nameUUIDFromBytes(name.getBytes(Charsets.UTF_8));
    return new GameProfile(uuid, name);
  }
}
