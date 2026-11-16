package shieldoffhand;

import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.command.Command;
import org.geysermc.geyser.api.command.CommandSource;
import org.geysermc.geyser.api.event.bedrock.SessionDisconnectEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCommandsEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostReloadEvent;
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
        reloadSettings();

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

        logger().info("ShieldOffhand enabled. " + describe(settings));
    }

    /** "geyser reload" keeps our packet handlers registered; just re-read the config. */
    @Subscribe
    public void onPostReload(GeyserPostReloadEvent event) {
        reloadSettings();
        logger().info("ShieldOffhand config reloaded. " + describe(settings));
    }

    /** "shieldoffhand reload": re-read config.yml without restarting Geyser or kicking anyone. */
    @Subscribe
    public void onDefineCommands(GeyserDefineCommandsEvent event) {
        event.register(Command.<CommandSource>builder(this)
                .source(CommandSource.class)
                .name("reload")
                .description("Reload the ShieldOffhand config")
                .permission("shieldoffhand.reload")
                .executor((source, command, args) -> {
                    boolean ok = reloadSettings();
                    source.sendMessage(ok
                            ? "ShieldOffhand config reloaded. " + describe(settings)
                            : "Could not read config.yml; kept the previous settings. Check the console.");
                })
                .build());
    }

    /** Reads config.yml. On failure keeps the current settings and returns false. */
    private boolean reloadSettings() {
        try {
            settings = Settings.load(dataFolder());
            return true;
        } catch (IOException e) {
            logger().error("Could not read config.yml: " + e.getMessage());
            return false;
        }
    }

    private static String describe(Settings s) {
        return "(shield-toggle: " + s.shieldToggle
                + ", blocking-speed-multiplier: " + s.blockingSpeedMultiplier
                + ", offhand-swap: " + s.offhandSwap.name().toLowerCase().replace('_', '-')
                + ", double-crouch-window-ms: " + s.doubleCrouchWindowMillis + ")";
    }

    @Subscribe
    public void onDisconnect(SessionDisconnectEvent event) {
        if (event.connection() instanceof GeyserSession session) {
            PlayerState.remove(session);
        }
    }
}
