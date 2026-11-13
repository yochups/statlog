package dev.nidza.statlog.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.nidza.statlog.Statlog;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

public final class StatlogTextColors {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation FILE = ResourceLocation.fromNamespaceAndPath(Statlog.ID, "ui_colors.json");
    private static volatile Map<String, Integer> overrides = Map.of();

    public static void reload(ResourceManager resources) {
        Map<String, Integer> loaded = new HashMap<>();
        try (Reader reader = resources.openAsReader(FILE)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            for (var entry : json.entrySet()) {
                if (entry.getValue().isJsonNull()) continue;
                String hex = entry.getValue().getAsString().replaceFirst("^#", "");
                if (!hex.matches("[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"))
                    throw new IllegalArgumentException("Invalid color for " + entry.getKey());
                loaded.put(entry.getKey(), hex.length() == 6
                        ? 0xFF000000 | Integer.parseUnsignedInt(hex, 16)
                        : (int) Long.parseLong(hex, 16));
            }
        } catch (Exception error) {
            LOGGER.warn("Could not load Statlog UI text colors; using built-in colors", error);
            loaded.clear();
        }
        overrides = Map.copyOf(loaded);
        LOGGER.debug("Loaded {} Statlog UI text color roles", loaded.size());
    }

    public static int get(String role, int fallback) { return overrides.getOrDefault(role, fallback); }

    public static Component styled(Component text, String role, int fallback) {
        return text.copy().withStyle(style -> style.withColor(get(role, fallback)));
    }

    private StatlogTextColors() {}
}
