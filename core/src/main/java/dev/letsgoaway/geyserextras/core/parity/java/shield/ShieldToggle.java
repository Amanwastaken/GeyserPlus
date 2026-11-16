package dev.letsgoaway.geyserextras.core.parity.java.shield;

import org.cloudburstmc.protocol.bedrock.data.AttributeData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.geysermc.geyser.entity.attribute.GeyserAttributeType;
import org.geysermc.geyser.entity.type.player.SessionPlayerEntity;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.inventory.PlayerInventory;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.item.type.BoatItem;
import org.geysermc.geyser.item.type.Item;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.FoodProperties;

import java.util.Set;

/**
 * Lets Bedrock players raise/lower a shield with the Use button, like Java Edition.
 * Bedrock itself only raises shields while crouching, and Geyser ignores a Use press
 * when the shield is in the offhand, so this fills that gap.
 */
public final class ShieldToggle {
    private ShieldToggle() {
    }

    /** Items whose own right-click action should win over raising an offhand shield. */
    private static final Set<Item> HAS_OWN_USE = Set.of(
            Items.BOW,
            Items.CROSSBOW,
            Items.TRIDENT,
            Items.ENDER_PEARL,
            Items.ENDER_EYE,
            Items.SNOWBALL,
            Items.EGG,
            Items.BLUE_EGG,
            Items.BROWN_EGG,
            Items.SPLASH_POTION,
            Items.LINGERING_POTION,
            Items.EXPERIENCE_BOTTLE,
            Items.WIND_CHARGE,
            Items.FISHING_ROD,
            Items.SPYGLASS,
            Items.GOAT_HORN,
            Items.FIREWORK_ROCKET,
            Items.BUNDLE,
            Items.WRITABLE_BOOK,
            Items.WRITTEN_BOOK,
            Items.BUCKET
    );

    /**
     * Called when a Bedrock player presses Use while not looking at a block or entity.
     *
     * @return true if the press was used to raise or lower the shield and should not be processed further
     */
    public static boolean handleUsePress(GeyserSession session) {
        SessionPlayerEntity entity = session.getPlayerEntity();

        if (entity.getFlag(EntityFlag.BLOCKING)) {
            // Crouch-blocking is Geyser's own behaviour; leave it alone
            if (session.isSneaking()) {
                return false;
            }
            lower(session);
            return true;
        }

        if (entity.getFlag(EntityFlag.USING_ITEM) || session.isSprinting()) {
            return false;
        }

        PlayerInventory inventory = session.getPlayerInventory();
        GeyserItemStack mainHand = inventory.getItemInHand();
        GeyserItemStack offHand = inventory.getOffhand();

        Hand hand;
        GeyserItemStack shield;
        if (isShield(mainHand)) {
            hand = Hand.MAIN_HAND;
            shield = mainHand;
        } else if (isShield(offHand) && !hasOwnUse(session, mainHand)) {
            hand = Hand.OFF_HAND;
            shield = offHand;
        } else {
            return false;
        }

        if (session.getWorldCache().hasCooldown(shield)) {
            return false;
        }

        session.useItem(hand);
        entity.setFlag(EntityFlag.BLOCKING, true);
        entity.updateBedrockMetadata();
        return true;
    }

    /** Lowers a shield that was raised with the Use button. */
    public static void lower(GeyserSession session) {
        SessionPlayerEntity entity = session.getPlayerEntity();
        if (!entity.getFlag(EntityFlag.BLOCKING)) {
            return;
        }
        session.releaseItem();
        entity.setFlag(EntityFlag.BLOCKING, false);
        entity.updateBedrockMetadata();
    }

    /** Vanilla shields, plus any item with the blocks_attacks component (e.g. custom shields from Nexo). */
    private static boolean isShield(GeyserItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return stack.is(Items.SHIELD) || stack.getComponent(DataComponentTypes.BLOCKS_ATTACKS) != null;
    }

    private static boolean hasOwnUse(GeyserSession session, GeyserItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.asItem();
        if (HAS_OWN_USE.contains(item) || item instanceof BoatItem) {
            return true;
        }
        // Armour, elytra, etc. get equipped on right click
        if (stack.getComponent(DataComponentTypes.EQUIPPABLE) != null) {
            return true;
        }
        // Crossbows, spears and items with a use cooldown
        if (stack.getComponent(DataComponentTypes.CHARGED_PROJECTILES) != null
                || stack.getComponent(DataComponentTypes.KINETIC_WEAPON) != null
                || stack.getComponent(DataComponentTypes.USE_COOLDOWN) != null) {
            return true;
        }
        // Food, potions, milk: only "used" if the player can actually consume it right now
        if (stack.getComponent(DataComponentTypes.CONSUMABLE) != null) {
            FoodProperties food = stack.getComponent(DataComponentTypes.FOOD);
            return food == null || food.isCanAlwaysEat() || canEat(session);
        }
        return false;
    }

    private static boolean canEat(GeyserSession session) {
        if (session.getGameMode() == GameMode.CREATIVE) {
            return true;
        }
        AttributeData hunger = session.getPlayerEntity().getAttributes().get(GeyserAttributeType.HUNGER);
        return hunger == null || hunger.getValue() < hunger.getMaximum();
    }
}
