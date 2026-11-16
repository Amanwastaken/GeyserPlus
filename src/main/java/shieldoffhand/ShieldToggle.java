package shieldoffhand;

import org.cloudburstmc.protocol.bedrock.data.AttributeData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket;
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

import java.util.List;
import java.util.Set;

/**
 * Java-style shield: Use raises/lowers it (Bedrock only raises shields while crouching,
 * and Geyser ignores Use when the shield is in the offhand), and blocking slows you down.
 */
public final class ShieldToggle {
    private ShieldToggle() {
    }

    /** Re-send the slowed speed this often (in client ticks), in case Geyser overwrote it. */
    private static final int RESEND_TICKS = 10;

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
     * @return true if the press raised or lowered the shield and should not be processed further
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

        PlayerState state = PlayerState.get(session);
        state.toggledBlocking = true;
        applySlowdown(session, state);
        return true;
    }

    /** Lowers a shield and restores normal speed. */
    public static void lower(GeyserSession session) {
        SessionPlayerEntity entity = session.getPlayerEntity();
        if (entity.getFlag(EntityFlag.BLOCKING)) {
            session.releaseItem();
            entity.setFlag(EntityFlag.BLOCKING, false);
            entity.updateBedrockMetadata();
        }
        PlayerState state = PlayerState.get(session);
        state.toggledBlocking = false;
        restoreSpeed(session, state);
    }

    /** Runs every client tick (PlayerAuthInput) to keep the slowdown in sync. */
    public static void tick(GeyserSession session) {
        PlayerState state = PlayerState.get(session);
        if (!state.toggledBlocking) {
            if (!Float.isNaN(state.slowedFrom)) {
                restoreSpeed(session, state);
            }
            return;
        }

        // Geyser drops the shield on attacks, item use and shield cooldowns; crouching takes over blocking
        if (!session.getPlayerEntity().getFlag(EntityFlag.BLOCKING) || session.isSneaking()) {
            state.toggledBlocking = false;
            restoreSpeed(session, state);
            return;
        }

        // Re-apply if the server changed our speed (effects, attributes) or every so often in case it was overwritten
        if (currentSpeed(session) != state.slowedFrom || ++state.ticksSinceSlowSent >= RESEND_TICKS) {
            applySlowdown(session, state);
        }
    }

    private static void applySlowdown(GeyserSession session, PlayerState state) {
        Settings settings = ShieldOffhandExtension.settings();
        if (settings.blockingSpeedMultiplier >= 1f) {
            return;
        }
        float normal = currentSpeed(session);
        sendSpeed(session, normal * settings.blockingSpeedMultiplier);
        state.slowedFrom = normal;
        state.ticksSinceSlowSent = 0;
    }

    private static void restoreSpeed(GeyserSession session, PlayerState state) {
        if (Float.isNaN(state.slowedFrom)) {
            return;
        }
        sendSpeed(session, currentSpeed(session));
        state.slowedFrom = Float.NaN;
    }

    /** The speed Geyser last received from the Java server. */
    private static float currentSpeed(GeyserSession session) {
        AttributeData speed = session.getPlayerEntity().getAttributes().get(GeyserAttributeType.MOVEMENT_SPEED);
        return speed != null ? speed.getValue() : GeyserAttributeType.MOVEMENT_SPEED.getDefaultValue();
    }

    private static void sendSpeed(GeyserSession session, float value) {
        AttributeData data = GeyserAttributeType.MOVEMENT_SPEED.getAttribute(value);
        if (data == null) {
            return;
        }
        UpdateAttributesPacket packet = new UpdateAttributesPacket();
        packet.setRuntimeEntityId(session.getPlayerEntity().geyserId());
        packet.setAttributes(List.of(data));
        session.sendUpstreamPacket(packet);
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
