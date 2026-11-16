package shieldoffhand;

import org.geysermc.geyser.session.GeyserSession;

/** Swap the held item with the offhand, like pressing F on Java Edition. */
public final class OffhandSwap {
    private OffhandSwap() {
    }

    /** One button press can arrive as two packets; ignore the second one. */
    private static final long DEBOUNCE_MILLIS = 150;

    /** Called when the player starts crouching; swaps on the second crouch within the window. */
    public static void onCrouchStart(GeyserSession session, PlayerState state, long windowMillis) {
        long now = System.currentTimeMillis();
        if (state.lastCrouchStartMillis != 0 && now - state.lastCrouchStartMillis <= windowMillis) {
            // Second tap: swap, and require two fresh taps for the next swap
            state.lastCrouchStartMillis = 0;
            swap(session, state, now);
        } else {
            state.lastCrouchStartMillis = now;
        }
    }

    /** Crouch + drop: the client already removed the "dropped" item on its side, so resend the inventory. */
    public static void onCrouchDrop(GeyserSession session) {
        PlayerState state = PlayerState.get(session);
        swap(session, state, System.currentTimeMillis());
        session.getPlayerInventoryHolder().updateInventory();
    }

    private static void swap(GeyserSession session, PlayerState state, long now) {
        if (now - state.lastSwapMillis >= DEBOUNCE_MILLIS) {
            state.lastSwapMillis = now;
            session.requestOffhandSwap();
        }
    }
}
