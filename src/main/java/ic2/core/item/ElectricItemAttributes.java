package ic2.core.item;

import ic2.api.item.ElectricItem;
import ic2.core.item.armor.ItemArmorElectric;
import ic2.core.item.armor.ItemArmorNanoSuit;
import ic2.core.item.armor.ItemArmorQuantumSuit;
import ic2.core.item.tool.AbstractItemNanoSaber;
import java.util.function.BiConsumer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Per-stack attribute modifiers for IC2's electric equipment.
 *
 * <p>NeoForge provided this through {@code ItemAttributeModifierEvent}, which was fired while a
 * stack's modifiers were being collected and could remove and re-add entries. Fabric has no such
 * callback — and 1.21 stores attribute modifiers in the {@code minecraft:attribute_modifiers}
 * component, which is baked from {@code Item#getDefaultAttributeModifiers()} and therefore cannot
 * vary per stack — so {@code ic2.mixin.ItemStackMixin} calls into this class from
 * {@code ItemStack#forEachModifier(EquipmentSlot, BiConsumer)}.
 *
 * <p>The nano saber's damage and speed follow its charge and activation state, and charged electric
 * armour replaces the item's base armour value.
 */
public final class ElectricItemAttributes {
  private static final ResourceLocation NANO_SABER_DAMAGE =
      ResourceLocation.fromNamespaceAndPath("ic2", "nano_saber_damage");
  private static final ResourceLocation NANO_SABER_SPEED =
      ResourceLocation.fromNamespaceAndPath("ic2", "nano_saber_speed");
  private static final ResourceLocation CHARGED_ARMOR =
      ResourceLocation.fromNamespaceAndPath("ic2", "charged_armor");
  private static final EquipmentSlot[] VANILLA_SLOTS = {
    EquipmentSlot.MAINHAND,
    EquipmentSlot.OFFHAND,
    EquipmentSlot.FEET,
    EquipmentSlot.LEGS,
    EquipmentSlot.CHEST,
    EquipmentSlot.HEAD
  };

  private ElectricItemAttributes() {}

  /**
   * Group counterpart of {@link #apply(ItemStack, EquipmentSlot, BiConsumer)}.
   *
   * <p>1.21.1 asks for attribute modifiers per {@link EquipmentSlotGroup}, and that overload of
   * {@code ItemStack#forEachModifier} does not delegate to the single-slot one — so the group form has
   * to be handled separately or per-stack modifiers never reach entity attributes.
   */
  public static boolean apply(
      ItemStack stack,
      EquipmentSlotGroup group,
      BiConsumer<Holder<Attribute>, AttributeModifier> consumer) {
    for (EquipmentSlot slot : VANILLA_SLOTS) {
      if (group.test(slot) && apply(stack, slot, consumer)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Emits the modifiers for {@code stack} in {@code slot}.
   *
   * @return {@code true} when this class supplied the modifiers and the vanilla component list must
   *     be skipped, {@code false} to let vanilla emission proceed unchanged
   */
  public static boolean apply(
      ItemStack stack,
      EquipmentSlot slot,
      BiConsumer<Holder<Attribute>, AttributeModifier> consumer) {
    Item item = stack.getItem();

    if (item instanceof AbstractItemNanoSaber) {
      if (slot != EquipmentSlot.MAINHAND) {
        return false;
      }

      int damage = 4;
      float speed = -3.0F;
      if (ElectricItem.manager.canUse(stack, 400.0) && AbstractItemNanoSaber.isActive(stack)) {
        damage = 20;
        speed = 0.0F;
      }

      // Mirrors the NeoForge handler, which removed all existing attack modifiers first.
      consumer.accept(
          Attributes.ATTACK_DAMAGE,
          new AttributeModifier(NANO_SABER_DAMAGE, damage, AttributeModifier.Operation.ADD_VALUE));
      consumer.accept(
          Attributes.ATTACK_SPEED,
          new AttributeModifier(NANO_SABER_SPEED, speed, AttributeModifier.Operation.ADD_VALUE));
      return true;
    }

    if (item instanceof ItemArmorElectric electric) {
      int[] protection =
          item instanceof ItemArmorNanoSuit
              ? ItemArmorNanoSuit.CHARGED_PROTECTION
              : item instanceof ItemArmorQuantumSuit ? ItemArmorQuantumSuit.CHARGED_PROTECTION : null;
      if (protection == null || slot != electric.getEquipmentSlot()) {
        return false;
      }

      if (ElectricItem.manager.getCharge(stack) < electric.getEnergyPerDamage()) {
        // Uncharged armour keeps the base value from the item's component.
        return false;
      }

      consumer.accept(
          Attributes.ARMOR,
          new AttributeModifier(
              CHARGED_ARMOR, protection[slot.getIndex()], AttributeModifier.Operation.ADD_VALUE));
      return true;
    }

    return false;
  }
}
