package ic2.fabric;

import ic2.core.IC2;
import ic2.core.network.Ic2Payload;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Registers the original IC2 wire format with Fabric's typed payload transport. */
public final class FabricNetwork {
  private FabricNetwork() {}

  public static void registerCommon() {
    PayloadTypeRegistry.playC2S().register(Ic2Payload.TYPE, Ic2Payload.CODEC);
    PayloadTypeRegistry.playS2C().register(Ic2Payload.TYPE, Ic2Payload.CODEC);
    ServerPlayNetworking.registerGlobalReceiver(Ic2Payload.TYPE, (payload, context) -> {
      var buffer = Unpooled.wrappedBuffer(payload.data());
      try {
        IC2.network.get(true).onPacket(buffer, context.player());
      } finally {
        buffer.release();
      }
    });
  }
}
