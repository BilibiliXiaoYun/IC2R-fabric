package ic2.mixin;

import ic2.core.item.armor.jetpack.JetpackHandler;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Drives the two jetpack hooks NeoForge delivered as events.
 *
 * <ul>
 *   <li>{@code LivingEquipmentChangeEvent} — the chest slot losing (or gaining) a jetpack has to stop
 *       the looping jetpack sound. Vanilla calls {@code LivingEntity#onEquipItem} with both the old
 *       and the new stack, so it is hooked here.
 *   <li>{@code LivingIncomingDamageEvent} — an attached jetpack is remembered before a fatal hit so
 *       it can be restored on the following tick.
 * </ul>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
  @Inject(method = "onEquipItem", at = @At("TAIL"))
  private void ic2$onEquipItem(
      EquipmentSlot slot, ItemStack oldStack, ItemStack newStack, CallbackInfo callback) {
    JetpackHandler handler = JetpackHandler.instance;
    if (handler != null) {
      handler.onEquipmentChange((LivingEntity) (Object) this, slot, oldStack, newStack);
    }
  }

  @Inject(method = "hurt", at = @At("HEAD"))
  private void ic2$onIncomingDamage(
      DamageSource source, float amount, CallbackInfoReturnable<Boolean> callback) {
    JetpackHandler handler = JetpackHandler.instance;
    if (handler != null) {
      handler.onIncomingDamage((LivingEntity) (Object) this, source);
    }
  }
}
