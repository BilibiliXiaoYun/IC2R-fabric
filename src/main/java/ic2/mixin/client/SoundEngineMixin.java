package ic2.mixin.client;

import ic2.core.event.EventHandlerClient;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Lets IC2 substitute a sound before it is played.
 *
 * <p>NeoForge exposed {@code PlaySoundEvent}, whose handler could replace the {@code SoundInstance}.
 * Fabric has no sound playback callback, so the argument of {@code SoundEngine#play} is rewritten
 * instead. This is what drives IC2's chainsaw hit/break sound override
 * ({@code SoundManagerClient#onSoundPlayed}).
 *
 * <p>{@code @ModifyVariable} is used rather than a cancellable {@code @Inject} so that playing the
 * replacement cannot re-enter {@code play}.
 */
@Mixin(SoundEngine.class)
public class SoundEngineMixin {
  @ModifyVariable(
      method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V",
      at = @At("HEAD"),
      argsOnly = true)
  private SoundInstance ic2$replaceSound(SoundInstance sound) {
    return EventHandlerClient.onSoundPlayed(sound);
  }
}
