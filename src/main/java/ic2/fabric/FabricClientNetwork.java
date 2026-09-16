package ic2.fabric;

import ic2.core.IC2;
import ic2.core.network.Ic2Payload;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

@Environment(EnvType.CLIENT)
public final class FabricClientNetwork {
  private FabricClientNetwork() {}

  public static void register() {
    ClientPlayNetworking.registerGlobalReceiver(Ic2Payload.TYPE, (payload, context) -> {
      var buffer = Unpooled.wrappedBuffer(payload.data());
      try {
        IC2.network.get(false).onPacket(buffer, context.player());
      } finally {
        buffer.release();
      }
    });
  }
}
