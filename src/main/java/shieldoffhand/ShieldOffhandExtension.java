package shieldoffhand;

import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.event.bedrock.SessionDisconnectEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;

import java.io.IOException;

/**
 * Two Java Edition controls for Bedrock players on Geyser:
 * - Use button (L2 / LT / ZL / right click) raises and lowers the shield, with Java-style slowdown
 * - Crouch + drop swaps the held item with the offhand
 */
public class ShieldOffhandExtension implements Extension {
    private static volatile Settings settings = Settings.defaults();

    public static Settings settings() {
        return settings;
    }

    @Subscribe
    @SuppressWarnings("unchecked")
    public void onPostInitialize(GeyserPostInitializeEvent event) {
        try {
            settings = Settings.load(dataFolder());
        } catch (IOException e) {
            logger().error("Could not read config.yml, using defaults: " + e.getMessage());
        }

        PacketTranslator<PlayerAuthInputPacket> authInput = (PacketTranslator<PlayerAuthInputPacket>)
                Registries.BEDROCK_PACKET_TRANSLATORS.get(PlayerAuthInputPacket.class);
        PacketTranslator<InventoryTransactionPacket> transaction = (PacketTranslator<InventoryTransactionPacket>)
                Registries.BEDROCK_PACKET_TRANSLATORS.get(InventoryTransactionPacket.class);

        if (authInput == null || transaction == null) {
            logger().error("Could not find Geyser's input handlers; ShieldOffhand is disabled. Is this Geyser version supported?");
            return;
        }

        Registries.BEDROCK_PACKET_TRANSLATORS.register(PlayerAuthInputPacket.class, new AuthInputTranslator(authInput));
        Registries.BEDROCK_PACKET_TRANSLATORS.register(InventoryTransactionPacket.class, new InventoryTransactionTranslator(transaction));

        logger().info("ShieldOffhand enabled (shield-toggle: " + settings.shieldToggle
                + ", blocking-speed-multiplier: " + settings.blockingSpeedMultiplier
                + ", offhand-swap: " + settings.offhandSwap + ")");
    }

    @Subscribe
    public void onDisconnect(SessionDisconnectEvent event) {
        if (event.connection() instanceof GeyserSession session) {
            PlayerState.remove(session);
        }
    }
}
