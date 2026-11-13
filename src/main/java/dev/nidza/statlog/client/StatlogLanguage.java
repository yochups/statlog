package dev.nidza.statlog.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class StatlogLanguage {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path FOLDER = FMLPaths.CONFIGDIR.get().resolve("statlog/lang");
    private static volatile String loadedLocale = "";
    private static volatile Map<String, String> overrides = Map.of();

    public static synchronized void reload() {
        String locale = Minecraft.getInstance().options.languageCode.toLowerCase(Locale.ROOT);
        loadedLocale = locale;
        try {
            Files.createDirectories(FOLDER);
            Path english = FOLDER.resolve("en_us.json");
            if (Files.notExists(english)) try (InputStream source = StatlogLanguage.class
                    .getResourceAsStream("/assets/statlog/lang/en_us.json")) {
                if (source == null) throw new IOException("Bundled English language missing");
                Files.copy(source, english);
            }
            Map<String, String> loaded = new HashMap<>();
            read(english, loaded);
            if (!locale.equals("en_us") && locale.matches("[a-z_]+"))
                read(FOLDER.resolve(locale + ".json"), loaded);
            overrides = Map.copyOf(loaded);
        } catch (Exception error) {
            LOGGER.warn("Could not load Statlog language file for {}", locale, error);
            overrides = Map.of();
        }
    }

    private static void read(Path file, Map<String, String> loaded) throws IOException {
        if (Files.notExists(file)) return;
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject entries = JsonParser.parseReader(reader).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : entries.entrySet())
                if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString())
                    loaded.put(entry.getKey(), entry.getValue().getAsString());
        }
    }

    public static Component key(String key) {
        if (!loadedLocale.equals(Minecraft.getInstance().options.languageCode.toLowerCase(Locale.ROOT))) reload();
        String value = overrides.get(key);
        return value == null ? Component.translatable(key) : Component.literal(value);
    }

    public static Component value(String text) {
        return text != null && text.contains(".") && !text.contains(" ")
                ? key(text) : Component.literal(text == null ? "" : text);
    }

    private StatlogLanguage() {}
}
