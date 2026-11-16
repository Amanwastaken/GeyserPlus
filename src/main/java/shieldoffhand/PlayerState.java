package shieldoffhand;

import org.geysermc.geyser.session.GeyserSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player state for the features in this extension. */
public final class PlayerState {
    private static final Map<GeyserSession, PlayerState> STATES = new ConcurrentHashMap<>();

    /** True while the shield is up because of the Use button (not crouching). */
    boolean toggledBlocking;
    /** The normal speed the slowed value was based on, or NaN when not slowed. */
    float slowedFrom = Float.NaN;
    /** Packets since the slowed speed was last sent, to re-send it now and then. */
    int ticksSinceSlowSent;
    /** Time of the last offhand swap, to avoid one button press swapping twice. */
    long lastSwapMillis;

    public static PlayerState get(GeyserSession session) {
        return STATES.computeIfAbsent(session, s -> new PlayerState());
    }

    public static void remove(GeyserSession session) {
        STATES.remove(session);
    }
}
