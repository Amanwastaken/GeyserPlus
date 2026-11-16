package shieldoffhand;

import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;

/**
 * Wraps Geyser's own PlayerAuthInput translator. The client sends this every tick,
 * so the extra work here is kept to a few field reads per packet.
 */
public final class AuthInputTranslator extends PacketTranslator<PlayerAuthInputPacket> {
    private final PacketTranslator<PlayerAuthInputPacket> original;

    public AuthInputTranslator(PacketTranslator<PlayerAuthInputPacket> original) {
        this.original = original;
    }

    @Override
    public void translate(GeyserSession session, PlayerAuthInputPacket packet) {
        Settings settings = ShieldOffhandExtension.settings();
        boolean wasSneaking = session.isSneaking();

        // Crouch + drop mode: cancel the drop and swap hands instead
        boolean crouchDropSwap = false;
        if (settings.offhandSwap == Settings.SwapTrigger.CROUCH_DROP && wasSneaking
                && !packet.getPlayerActions().isEmpty()) {
            try {
                crouchDropSwap = packet.getPlayerActions().removeIf(action -> action.getAction() == PlayerActionType.DROP_ITEM);
            } catch (UnsupportedOperationException ignored) {
                // List can't be changed: let the drop happen normally rather than drop and swap
            }
        }

        original.translate(session, packet);

        if (crouchDropSwap) {
            OffhandSwap.onCrouchDrop(session);
        }

        // Only look up per-player state when something relevant happened or the shield is up
        boolean startedCrouching = !wasSneaking && session.isSneaking();
        boolean doubleCrouch = startedCrouching && settings.offhandSwap == Settings.SwapTrigger.DOUBLE_CROUCH;
        if (!doubleCrouch && !settings.shieldToggle) {
            return;
        }

        PlayerState state = PlayerState.get(session);
        if (doubleCrouch) {
            OffhandSwap.onCrouchStart(session, state, settings.doubleCrouchWindowMillis);
        }

        if (settings.shieldToggle) {
            // Java players can't sprint while blocking
            if (state.toggledBlocking && !session.isSneaking()
                    && packet.getInputData().contains(PlayerAuthInputData.START_SPRINTING)) {
                ShieldToggle.lower(session);
            }
            ShieldToggle.tick(session, state);
        }
    }

    @Override
    public boolean shouldExecuteInEventLoop() {
        return original.shouldExecuteInEventLoop();
    }
}
