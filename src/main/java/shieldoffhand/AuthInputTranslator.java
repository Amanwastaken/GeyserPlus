package shieldoffhand;

import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;

/** Wraps Geyser's own PlayerAuthInput translator (sent by the client every tick). */
public final class AuthInputTranslator extends PacketTranslator<PlayerAuthInputPacket> {
    private final PacketTranslator<PlayerAuthInputPacket> original;

    public AuthInputTranslator(PacketTranslator<PlayerAuthInputPacket> original) {
        this.original = original;
    }

    @Override
    public void translate(GeyserSession session, PlayerAuthInputPacket packet) {
        Settings settings = ShieldOffhandExtension.settings();

        // Crouch + drop: cancel the drop and swap hands instead
        boolean swap = false;
        if (settings.offhandSwap && session.isSneaking()) {
            try {
                swap = packet.getPlayerActions().removeIf(action -> action.getAction() == PlayerActionType.DROP_ITEM);
            } catch (UnsupportedOperationException ignored) {
                // List can't be changed: let the drop happen normally rather than drop and swap
            }
        }

        original.translate(session, packet);

        if (swap) {
            OffhandSwap.trigger(session);
        }

        if (settings.shieldToggle) {
            // Java players can't sprint while blocking
            if (packet.getInputData().contains(PlayerAuthInputData.START_SPRINTING) && !session.isSneaking()) {
                if (PlayerState.get(session).toggledBlocking) {
                    ShieldToggle.lower(session);
                }
            }
            ShieldToggle.tick(session);
        }
    }

    @Override
    public boolean shouldExecuteInEventLoop() {
        return original.shouldExecuteInEventLoop();
    }
}
