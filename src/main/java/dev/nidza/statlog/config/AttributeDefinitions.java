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
            UnmodifiableConfig icons = config.get("point_icons");
            UnmodifiableConfig orbTextures = config.get("point_orb_textures");
            UnmodifiableConfig orbColors = config.get("point_orb_colors");
            for (String id : points) {
                String color = colors == null ? "#FFFFFF" : string(colors, id, "#FFFFFF");
                String icon = iconPath(icons == null ? id.toLowerCase(java.util.Locale.ROOT) + ".png"
                        : string(icons, id, id.toLowerCase(java.util.Locale.ROOT) + ".png"), "currencies");
                String orb = orbPath(orbTextures == null ? id.toLowerCase(java.util.Locale.ROOT) + ".png"
                        : string(orbTextures, id, id.toLowerCase(java.util.Locale.ROOT) + ".png"));
                if (icon == null) {
                    LOGGER.warn("Invalid Statlog currency icon for {}; using the default", id);
                    icon = iconPath(id.toLowerCase(java.util.Locale.ROOT) + ".png", "currencies");
                }
                if (orb == null) {
                    LOGGER.warn("Invalid Statlog currency orb texture for {}; using the default", id);
                    orb = orbPath(id.toLowerCase(java.util.Locale.ROOT) + ".png");
                }
                display.put(id, new CurrencyDefinition(id,
                        names == null ? id : string(names, id, id), color, icon, orb,
                        orbColors == null ? color : string(orbColors, id, color)));
            }
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
                        string(row, "item", "minecraft:book"), texture,
                        !Boolean.FALSE.equals(row.get("visible")))) != null)
                    LOGGER.warn("Ignoring invalid or excess Statlog tab {}", id);
            }
            Object entries = config.get("listed_attributes");
            if (entries instanceof List<?> list) for (Object entry : list) {
                if (!(entry instanceof UnmodifiableConfig row)) continue;
                try {
                    List<String> ids = strings(row.get("attributes"));
                    String id = ids.isEmpty() ? string(row, "attribute", "") : ids.getFirst();
                    if (ids.isEmpty()) ids = List.of(id);
                    ResourceLocation parsed = ResourceLocation.tryParse(id);
                    if (parsed == null) throw new IllegalArgumentException("invalid attribute ID");
                    if (ids.stream().anyMatch(s -> ResourceLocation.tryParse(s) == null)
                            || new LinkedHashSet<>(ids).size() != ids.size())
                        throw new IllegalArgumentException("invalid or duplicate attributes");
                    int min = integer(row, "min_level", 0);
                    int max = integer(row, "max_level", 10);
                    int start = integer(row, "starting_level", min);
                    List<Double> increments = doubles(row.get("increments"), ids.size(), "increments");
                    double increment = increments == null ? number(row, "increment_value", 1.0) : increments.getFirst();
                    int cost = integer(row, "cost", 1);
                    if (min < 0 || max < min || start < min || start > max || cost < 0 || !Double.isFinite(increment))
                        throw new IllegalArgumentException("invalid level, cost, or increment");
                    List<String> descriptions = strings(row.get("descriptions"));
                    if (!descriptions.isEmpty() && descriptions.size() != ids.size())
                        throw new IllegalArgumentException("descriptions must align with attributes");
                    List<String> formats = strings(row.get("value_formats"));
                    if (!formats.isEmpty() && formats.size() != ids.size())
                        throw new IllegalArgumentException("value_formats must align with attributes");
                    List<Double> percentageBases = doubles(row.get("percentage_bases"), ids.size(), "percentage_bases");
                    List<Map<Integer, Double>> levelOverrides = alignedLevelValues(row, ids.size(), min, max);
                    String name = string(row, "name", id);
                    String key = string(row, "key", name.toLowerCase(java.util.Locale.ROOT)
                            .replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", ""));
                    if (!key.matches("[a-z][a-z0-9_]*")) throw new IllegalArgumentException("invalid short key " + key);
                    if (!(row.get("currency") instanceof String))
                        throw new IllegalArgumentException("currency is required for " + key);
                    Object pointSetting = row.get("accepted_points");
                    List<String> accepted = (pointSetting == null
                            ? List.of(string(row, "currency", "")) : strings(pointSetting)).stream()
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
                    List<StatEffect> extra = new ArrayList<>();
                    for (int i = 1; i < ids.size(); i++) {
                        String effectId = ids.get(i);
                        double effectIncrement = increments == null ? 0 : increments.get(i);
                        extra.add(new StatEffect(effectId, effectIncrement,
                                formats.isEmpty() ? percent(row, effectId) : percent(formats.get(i)),
                                levelOverrides.get(i), percentageBases == null ? 0 : percentageBases.get(i)));
                    }
                    Object extraEntries = row.get("extra_attributes");
                    if (extraEntries instanceof List<?> extraRows) for (Object extraEntry : extraRows) {
                        if (!(extraEntry instanceof UnmodifiableConfig effect)) continue;
                        String effectId = string(effect, "attribute", "");
                        double effectIncrement = number(effect, "increment_value", 0);
                        if (ResourceLocation.tryParse(effectId) == null || effectId.equals(id)
                                || !Double.isFinite(effectIncrement) || extra.stream().anyMatch(e -> e.attribute().equals(effectId)))
                            throw new IllegalArgumentException("invalid extra attribute " + effectId);
                        extra.add(new StatEffect(effectId, effectIncrement, percent(effect, effectId),
                                levelValues(effect, min, max), percentageBase(effect)));
                    }
                    AttributeDefinition def = new AttributeDefinition(id, name,
                            descriptions.isEmpty() ? string(row, "description", "") : String.join("\n", descriptions), icon,
                            min, max, start, increment, cost, accepted, List.copyOf(assignedTabs),
                            maxIcon, !Boolean.FALSE.equals(row.get("max_icon_sepia")),
                            formats.isEmpty() ? percent(row, id) : percent(formats.getFirst()),
                            alignedText(row, "current_text", ids.size()),
                            alignedText(row, "increment_text", ids.size()),
                            levelOverrides.getFirst(), percentageBases == null ? percentageBase(row) : percentageBases.getFirst(),
                            List.copyOf(extra));
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
    private static boolean percent(UnmodifiableConfig c, String id) {
        String fallback = id.equals("minecraft:generic.max_health") || id.equals("minecraft:generic.armor")
                || id.equals("minecraft:generic.armor_toughness") || id.equals("statlog:target_invulnerability_ticks")
                ? "NUMBER" : "PERCENT";
        return percent(string(c, "value_format", fallback));
    }
    private static boolean percent(String configured) {
        String format = configured.toUpperCase(java.util.Locale.ROOT);
        if (!format.equals("NUMBER") && !format.equals("PERCENT"))
            throw new IllegalArgumentException("value_format must be NUMBER or PERCENT");
        return format.equals("PERCENT");
    }
    private static List<Double> doubles(Object configured, int count, String field) {
        if (configured == null) return null;
        if (!(configured instanceof List<?> values) || values.size() != count)
            throw new IllegalArgumentException(field + " must align with attributes");
        List<Double> result = new ArrayList<>();
        for (Object value : values) {
            if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                    || (field.equals("percentage_bases") && number.doubleValue() < 0))
                throw new IllegalArgumentException("invalid " + field + " entry");
            result.add(number.doubleValue());
        }
        return List.copyOf(result);
    }
    private static String alignedText(UnmodifiableConfig row, String field, int count) {
        Object value = row.get(field);
        if (value instanceof String text) return text;
        if (value == null) return "";
        List<String> entries = strings(value);
        if (entries.size() != count) throw new IllegalArgumentException(field + " must align with attributes");
        return String.join("{value_separator}", entries);
    }
    private static List<Map<Integer, Double>> alignedLevelValues(UnmodifiableConfig row, int count, int min, int max) {
        Object configured = row.get("level_values");
        if (configured instanceof List<?> list && !list.isEmpty() && list.getFirst() instanceof List<?>) {
            if (list.size() != count) throw new IllegalArgumentException("level_values must align with attributes");
            List<Map<Integer, Double>> result = new ArrayList<>();
            for (Object value : list) result.add(levelValues(value, min, max));
            return List.copyOf(result);
        }
        List<Map<Integer, Double>> result = new ArrayList<>();
        result.add(levelValues(configured, min, max));
        for (int i = 1; i < count; i++) result.add(Map.of());
        return List.copyOf(result);
    }
    private static double percentageBase(UnmodifiableConfig c) {
        double base = number(c, "percentage_base", 0);
        if (!Double.isFinite(base) || base < 0) throw new IllegalArgumentException("percentage_base must be nonnegative");
        return base;
    }
    private static Map<Integer, Double> levelValues(UnmodifiableConfig row, int min, int max) {
        Object configured = row.get("level_values");
        return levelValues(configured, min, max);
    }
    private static Map<Integer, Double> levelValues(Object configured, int min, int max) {
        if (configured == null) return Map.of();
        if (!(configured instanceof List<?> entries)) throw new IllegalArgumentException("level_values must be a list");
        Map<Integer, Double> values = new LinkedHashMap<>();
        for (Object entry : entries) {
            if (!(entry instanceof String text)) throw new IllegalArgumentException("invalid level_values entry");
            String[] parts = text.split(":", 2);
            try {
                int level = Integer.parseInt(parts[0].trim());
                double value = Double.parseDouble(parts[1].trim());
                if (level < min || level > max || !Double.isFinite(value) || values.putIfAbsent(level, value) != null)
                    throw new IllegalArgumentException("invalid level_values entry " + text);
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException error) {
                throw new IllegalArgumentException("invalid level_values entry " + text, error);
            }
        }
        return Map.copyOf(values);
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
    private static String orbPath(String name) {
        String prefix = "statlog:textures/entity/currency_orbs/";
        String file = name.startsWith(prefix) ? name.substring(prefix.length()) : name;
        return file.matches("[a-z0-9_.-]+\\.png") ? prefix + file : null;
    }
    private AttributeDefinitions() {}
}
