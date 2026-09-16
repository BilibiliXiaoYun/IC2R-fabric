package ic2.mixin;

import ic2.core.item.ElectricItemAttributes;
import java.util.function.BiConsumer;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets IC2's electric equipment vary its attribute modifiers per stack.
 *
 * <p>NeoForge exposed {@code ItemAttributeModifierEvent}; Fabric has no equivalent, and 1.21 bakes
 * the {@code minecraft:attribute_modifiers} component from {@code
 * Item#getDefaultAttributeModifiers()} so it cannot depend on the stack. Injecting at the head of
 * {@code ItemStack#forEachModifier} replaces the emission for IC2's own items, which is the same
 * point NeoForge's event ran from.
 *
 * <p>Both overloads must be covered: 1.21.1 implements {@code forEachModifier(EquipmentSlot)} and
 * {@code forEachModifier(EquipmentSlotGroup)} as two independent bodies (the group variant reads the
 * component itself instead of delegating to the single-slot one), and the entity attribute code —
 * hence charged nano/quantum armour — goes through the group variant. Hooking only the single-slot
 * method left every per-stack modifier silently unapplied in game.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
  @Inject(
      method = "forEachModifier(Lnet/minecraft/world/entity/EquipmentSlot;Ljava/util/function/BiConsumer;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void ic2$electricItemModifiers(
      EquipmentSlot slot,
      BiConsumer<Holder<Attribute>, AttributeModifier> consumer,
      CallbackInfo callback) {
    if (ElectricItemAttributes.apply((ItemStack) (Object) this, slot, consumer)) {
      callback.cancel();
    }
  }

  @Inject(
      method = "forEachModifier(Lnet/minecraft/world/entity/EquipmentSlotGroup;Ljava/util/function/BiConsumer;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void ic2$electricItemModifiersForGroup(
      EquipmentSlotGroup group,
      BiConsumer<Holder<Attribute>, AttributeModifier> consumer,
      CallbackInfo callback) {
    if (ElectricItemAttributes.apply((ItemStack) (Object) this, group, consumer)) {
      callback.cancel();
    }
  }
}
