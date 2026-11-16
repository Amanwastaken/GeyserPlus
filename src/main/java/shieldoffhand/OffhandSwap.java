package shieldoffhand;

import org.geysermc.geyser.session.GeyserSession;

/** Crouch + drop = swap the held item with the offhand, like pressing F on Java Edition. */
public final class OffhandSwap {
    private OffhandSwap() {
    }

    /** One button press can arrive as two packets; ignore the second one. */
    private static final long DEBOUNCE_MILLIS = 150;

    public static void trigger(GeyserSession session) {
        PlayerState state = PlayerState.get(session);
        long now = System.currentTimeMillis();
        if (now - state.lastSwapMillis >= DEBOUNCE_MILLIS) {
            state.lastSwapMillis = now;
            session.requestOffhandSwap();
        }
        // The Bedrock client already removed the "dropped" item on its side; put it back
        session.getPlayerInventoryHolder().updateInventory();
    }
}
