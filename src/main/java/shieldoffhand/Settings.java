package shieldoffhand;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Tiny config: plain "key: value" lines in config.yml. */
public final class Settings {
    private static final String DEFAULT_CONFIG = """
            # ShieldOffhand - Java-style shield and offhand controls for Bedrock players on Geyser.

            # Raise/lower the shield with the Use button (L2 / LT / ZL / right click),
            # when not looking at a block. Works with the shield in either hand, unless the
            # main-hand item has its own use (food, bows, potions, pearls, armor...).
            # Crouching still raises the shield too.
            shield-toggle: true

            # How fast Bedrock players move while blocking with the Use button,
            # as a fraction of normal speed. Java Edition is 0.2. Set to 1.0 to disable slowing.
            blocking-speed-multiplier: 0.2

            # Crouch + drop item swaps the held item with the offhand (like F on Java).
            # While crouching, the drop button no longer drops items.
            offhand-swap: true
            """;

    public final boolean shieldToggle;
    public final float blockingSpeedMultiplier;
    public final boolean offhandSwap;

    private Settings(Map<String, String> values) {
        this.shieldToggle = Boolean.parseBoolean(values.getOrDefault("shield-toggle", "true"));
        this.offhandSwap = Boolean.parseBoolean(values.getOrDefault("offhand-swap", "true"));
        float multiplier;
        try {
            multiplier = Float.parseFloat(values.getOrDefault("blocking-speed-multiplier", "0.2"));
        } catch (NumberFormatException e) {
            multiplier = 0.2f;
        }
        this.blockingSpeedMultiplier = Math.max(0f, Math.min(1f, multiplier));
    }

    public static Settings load(Path folder) throws IOException {
        Files.createDirectories(folder);
        Path file = folder.resolve("config.yml");
        if (!Files.exists(file)) {
            Files.writeString(file, DEFAULT_CONFIG, StandardCharsets.UTF_8);
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        Map<String, String> values = new HashMap<>();
        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = trimmed.substring(0, colon).strip().toLowerCase();
            String value = trimmed.substring(colon + 1).strip();
            int comment = value.indexOf(" #");
            if (comment >= 0) {
                value = value.substring(0, comment).strip();
            }
            values.put(key, value);
        }
        return new Settings(values);
    }

    public static Settings defaults() {
        return new Settings(Map.of());
    }
}
