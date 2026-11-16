package shieldoffhand;

import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryActionData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventorySource;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;

/** Wraps Geyser's own InventoryTransaction translator (item use, drops, attacks). */
public final class InventoryTransactionTranslator extends PacketTranslator<InventoryTransactionPacket> {
    private final PacketTranslator<InventoryTransactionPacket> original;

    public InventoryTransactionTranslator(PacketTranslator<InventoryTransactionPacket> original) {
        this.original = original;
    }

    @Override
    public void translate(GeyserSession session, InventoryTransactionPacket packet) {
        Settings settings = ShieldOffhandExtension.settings();
        InventoryTransactionType type = packet.getTransactionType();

        // Use pressed while looking at nothing (action 1): raise/lower the shield like Java Edition
        if (settings.shieldToggle
                && type == InventoryTransactionType.ITEM_USE
                && packet.getActionType() == 1
                && ShieldToggle.handleUsePress(session)) {
            return;
        }

        // Crouch + drop (when the client reports the drop this way): swap hands instead
        if (settings.offhandSwap == Settings.SwapTrigger.CROUCH_DROP
                && type == InventoryTransactionType.NORMAL
                && session.isSneaking()
                && isDrop(packet)) {
            OffhandSwap.onCrouchDrop(session);
            return;
        }

        original.translate(session, packet);
    }

    private static boolean isDrop(InventoryTransactionPacket packet) {
        if (packet.getActions().size() != 2) {
            return false;
        }
        InventoryActionData worldAction = packet.getActions().get(1);
        return worldAction.getSource().getType() == InventorySource.Type.WORLD_INTERACTION
                && worldAction.getSource().getFlag() == InventorySource.Flag.DROP_ITEM;
    }

    @Override
    public boolean shouldExecuteInEventLoop() {
        return original.shouldExecuteInEventLoop();
    }
}
