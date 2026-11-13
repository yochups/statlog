package dev.nidza.statlog.config;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AttributeDefinitions {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile Map<String, AttributeDefinition> definitions = Map.of();
    private static volatile Map<String, String> keys = Map.of();
    private static volatile Set<String> customPoints = Set.of();
    private static volatile Map<String, CurrencyDefinition> currencies = Map.of();
    private static volatile Map<String, AttributeTab> tabs = Map.of();

    public static synchronized void load() {
        load(FMLPaths.CONFIGDIR.get().resolve("statlog/statlog-attributes.toml"));
    }

    static synchronized void load(Path file) {
        try {
            Files.createDirectories(file.getParent());
            if (Files.notExists(file)) {
                try (InputStream source = AttributeDefinitions.class.getResourceAsStream("/default-statlog-attributes.toml")) {
                    if (source == null) throw new IOException("Default attribute config missing");
                    Files.copy(source, file);
                }
            }
            UnmodifiableConfig config = new TomlParser().parse(Files.readString(file));
            Set<String> points = new LinkedHashSet<>();
            for (String point : strings(config.get("custom_points"))) {
                String id = point.trim().toUpperCase(java.util.Locale.ROOT);
                if (id.matches("[A-Z][A-Z0-9_]*") && !id.equals("XP") && !id.equals("ALL")) points.add(id);
                else LOGGER.warn("Ignoring invalid Statlog currency {}", point);
            }
            Map<String, CurrencyDefinition> display = new LinkedHashMap<>();
            UnmodifiableConfig names = config.get("point_names");
            UnmodifiableConfig colors = config.get("point_colors");
            for (String id : points) display.put(id, new CurrencyDefinition(id,
                    names == null ? id : string(names, id, id),
                    colors == null ? "#FFFFFF" : string(colors, id, "#FFFFFF")));
            Map<String, AttributeDefinition> loaded = new LinkedHashMap<>();
            Map<String, String> loadedKeys = new LinkedHashMap<>();
            Map<String, AttributeTab> loadedTabs = new LinkedHashMap<>();
            Object tabEntries = config.get("attribute_tabs");
            if (tabEntries instanceof List<?> list) for (Object entry : list) {
                if (!(entry instanceof UnmodifiableConfig row)) continue;
                String id = string(row, "id", "");
                String texture = iconPath(string(row, "texture", id + ".png"), "tabs");
                if (!id.matches("[a-z0-9_]+") || texture == null || loadedTabs.size() >= 11
                        || loadedTabs.putIfAbsent(id, new AttributeTab(id, string(row, "name", id),
                        string(row, "item", "minecraft:book"), texture)) != null)
                    LOGGER.warn("Ignoring invalid or excess Statlog tab {}", id);
            }
            Object entries = config.get("listed_attributes");
            if (entries instanceof List<?> list) for (Object entry : list) {
                if (!(entry instanceof UnmodifiableConfig row)) continue;
                try {
                    String id = string(row, "attribute", "");
                    ResourceLocation parsed = ResourceLocation.tryParse(id);
                    if (parsed == null) throw new IllegalArgumentException("invalid attribute ID");
                    int min = integer(row, "min_level", 1);
                    int max = integer(row, "max_level", 10);
                    int start = integer(row, "starting_level", min);
                    double increment = number(row, "increment_value", 1.0);
                    int cost = integer(row, "cost", 1);
                    if (min < 0 || max < min || start < min || start > max || cost < 0 || !Double.isFinite(increment))
                        throw new IllegalArgumentException("invalid level, cost, or increment");
                    String name = string(row, "name", id);
                    String key = string(row, "key", name.toLowerCase(java.util.Locale.ROOT)
                            .replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", ""));
                    if (!key.matches("[a-z][a-z0-9_]*")) throw new IllegalArgumentException("invalid short key " + key);
                    Object pointSetting = row.get("accepted_points");
                    List<String> accepted = (pointSetting == null
                            ? List.of(string(row, "currency", "XP")) : strings(pointSetting)).stream()
                            .map(s -> s.trim().toUpperCase(java.util.Locale.ROOT)).toList();
                    if (accepted.isEmpty() || accepted.stream().anyMatch(s -> !s.equals("XP") && !s.equals("ALL") && !points.contains(s)))
                        throw new IllegalArgumentException("unknown or empty accepted_points");
                    String rawIcon = string(row, "icon", "minecraft:paper");
                    String icon = rawIcon.endsWith(".png") && (!rawIcon.contains(":") || rawIcon.startsWith("statlog:"))
                            ? iconPath(rawIcon, "attributes") : rawIcon;
                    if (icon == null || ResourceLocation.tryParse(icon) == null) throw new IllegalArgumentException("invalid icon");
                    boolean generatedIcon = icon.startsWith("statlog:textures/gui/attributes/");
                    boolean maxIcon = generatedIcon && !Boolean.FALSE.equals(row.get("max_icon"));
                    if (Boolean.TRUE.equals(row.get("max_icon")) && !generatedIcon)
                        throw new IllegalArgumentException("max_icon requires a generated Statlog PNG icon");
                    List<String> assignedTabs = row.get("tabs") == null
                            ? stringsOrSingle(row.get("tab")) : strings(row.get("tabs"));
                    if (assignedTabs.stream().anyMatch(tab -> !loadedTabs.containsKey(tab)))
                        throw new IllegalArgumentException("unknown tab assignment");
                    AttributeDefinition def = new AttributeDefinition(id, name,
                            string(row, "description", ""), icon,
                            min, max, start, increment, cost, accepted, List.copyOf(assignedTabs),
                            maxIcon, !Boolean.FALSE.equals(row.get("max_icon_sepia")));
                    if (loaded.containsKey(id)) throw new IllegalArgumentException("duplicate attribute " + id);
                    if (loadedKeys.containsKey(key)) throw new IllegalArgumentException("duplicate short key " + key);
                    loaded.put(id, def);
                    loadedKeys.put(key, id);
                } catch (RuntimeException ex) {
                    LOGGER.warn("Ignoring invalid Statlog attribute entry: {}", ex.getMessage());
                }
            }
            customPoints = Collections.unmodifiableSet(points);
            currencies = Collections.unmodifiableMap(display);
            definitions = Collections.unmodifiableMap(loaded);
            keys = Collections.unmodifiableMap(loadedKeys);
            tabs = Collections.unmodifiableMap(loadedTabs);
            LOGGER.info("Loaded {} Statlog attributes and {} custom currencies", loaded.size(), points.size());
        } catch (Exception ex) {
            LOGGER.error("Could not load Statlog attributes config at {}; retaining previous definitions", file, ex);
        }
    }

    public static Map<String, AttributeDefinition> all() { return definitions; }
    public static Map<String, String> keys() { return keys; }
    public static AttributeDefinition resolve(String idOrKey) {
        if (idOrKey == null) return null;
        AttributeDefinition exact = definitions.get(idOrKey);
        if (exact != null) return exact;
        String key = idOrKey.toLowerCase(java.util.Locale.ROOT);
        if (key.startsWith("minecraft:")) key = key.substring("minecraft:".length());
        return definitions.get(keys.get(key));
    }
    public static Map<String, AttributeTab> tabs() { return tabs; }
    public static AttributeDefinition get(String id) { return definitions.get(id); }
    public static Set<String> customPoints() { return customPoints; }
    public static Map<String, CurrencyDefinition> currencies() { return currencies; }
    public static boolean registered(String id) {
        if (id.equals("statlog:empty") || id.startsWith("statlog:empty/")) return true;
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key != null && BuiltInRegistries.ATTRIBUTE.containsKey(key);
    }
    public static List<String> registeredIds() {
        return BuiltInRegistries.ATTRIBUTE.keySet().stream().map(ResourceLocation::toString).sorted().toList();
    }
    private static String string(UnmodifiableConfig c, String key, String fallback) {
        Object value = c.get(key);
        return value instanceof String s ? s : fallback;
    }
    private static int integer(UnmodifiableConfig c, String key, int fallback) {
        Object value = c.get(key);
        return value instanceof Number n ? n.intValue() : fallback;
    }
    private static double number(UnmodifiableConfig c, String key, double fallback) {
        Object value = c.get(key);
        return value instanceof Number n ? n.doubleValue() : fallback;
    }
    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object item : list) if (item instanceof String s) result.add(s);
        return result;
    }
    private static List<String> stringsOrSingle(Object value) {
        return value instanceof String s ? List.of(s) : strings(value);
    }
    private static String iconPath(String name, String folder) {
        String prefix = "statlog:textures/gui/" + folder + "/";
        String file = name.startsWith(prefix) ? name.substring(prefix.length()) : name;
        return file.matches("[a-z0-9_./-]+\\.png") && !file.contains("..") && !file.contains("/")
                ? prefix + file : null;
    }
    private AttributeDefinitions() {}
}
