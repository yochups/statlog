package dev.nidza.statlog.server;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.mojang.logging.LogUtils;
import dev.nidza.statlog.Statlog;
import dev.nidza.statlog.config.StatlogConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.ToIntFunction;

public final class ItemRules {
    enum Kind { UNUSABLE, EFFECT, PLAYER_ATTRIBUTE, ITEM_DAMAGE, ITEM_MINING_SPEED, LEGACY_DEBUFF }
    record Rule(ResourceLocation item, String stat, int required, boolean unusable,
                        ResourceLocation target, double strength, int index, Kind kind) {}
    private record ModifierRef(Holder<Attribute> attribute, ResourceLocation id) {}

    private static final Logger LOGGER = LogUtils.getLogger();
    private static List<Rule> fileRules = List.of();
    private static List<String> source = List.of();
    private static List<Rule> parsed = List.of();
    private static final Map<ServerPlayer, List<ModifierRef>> MODIFIERS = new WeakHashMap<>();

    private static synchronized List<Rule> rules() {
        List<String> current = List.copyOf(StatlogConfig.ITEM_REQUIREMENTS.get());
        if (current.equals(source)) return parsed;
        source = current;
        List<Rule> merged = new ArrayList<>(fileRules);
        for (Rule old : parse(current)) merged.add(new Rule(old.item(), old.stat(), old.required(),
                old.unusable(), old.target(), old.strength(), fileRules.size() + old.index(), old.kind()));
        parsed = List.copyOf(merged);
        return parsed;
    }

    public static synchronized void load() {
        Path file = FMLPaths.CONFIGDIR.get().resolve("statlog/statlog-tiers.toml");
        try {
            Files.createDirectories(file.getParent());
            if (Files.notExists(file)) try (InputStream template = ItemRules.class.getResourceAsStream("/default-statlog-tiers.toml")) {
                if (template == null) throw new IOException("Default tier config missing");
                Files.copy(template, file);
            }
            fileRules = parseTiers(new TomlParser().parse(Files.readString(file)));
            source = null;
            LOGGER.info("Loaded {} Statlog item tier rules", fileRules.size());
        } catch (Exception error) {
            LOGGER.error("Could not load Statlog item tiers at {}; retaining previous rules", file, error);
        }
    }

    static List<Rule> parseTiers(UnmodifiableConfig config) {
        List<Rule> loaded = new ArrayList<>();
        Object entries = config.get("tiers");
        if (!(entries instanceof List<?> rows)) return List.of();
        for (int i = 0; i < rows.size(); i++) {
            if (!(rows.get(i) instanceof UnmodifiableConfig row)) continue;
            try {
                ResourceLocation item = ResourceLocation.parse((String) row.get("item"));
                String stat = (String) row.get("stat");
                int required = ((Number) row.get("required_level")).intValue();
                Kind kind = Kind.valueOf(((String) row.get("type")).toUpperCase(Locale.ROOT));
                if (stat.isBlank() || required < 0 || kind == Kind.LEGACY_DEBUFF)
                    throw new IllegalArgumentException("invalid stat, level, or type");
                ResourceLocation target = switch (kind) {
                    case EFFECT, PLAYER_ATTRIBUTE -> ResourceLocation.parse((String) row.get("target"));
                    default -> null;
                };
                double strength = switch (kind) {
                    case EFFECT -> ((Number) row.get("amplifier")).doubleValue();
                    case PLAYER_ATTRIBUTE -> ((Number) row.get("amount")).doubleValue();
                    case ITEM_DAMAGE, ITEM_MINING_SPEED -> ((Number) row.get("multiplier")).doubleValue();
                    default -> 0;
                };
                if (!Double.isFinite(strength)
                        || (kind == Kind.EFFECT && (strength < 0 || strength > 255 || strength != Math.floor(strength)))
                        || ((kind == Kind.ITEM_DAMAGE || kind == Kind.ITEM_MINING_SPEED) && (strength < 0 || strength > 1)))
                    throw new IllegalArgumentException("invalid amplifier, amount, or multiplier");
                loaded.add(new Rule(item, stat, required, kind == Kind.UNUSABLE, target, strength, i, kind));
            } catch (RuntimeException error) {
                LOGGER.warn("Ignoring Statlog item tier {}: {}", i + 1, error.getMessage());
            }
        }
        return List.copyOf(loaded);
    }

    static List<Rule> parse(List<String> current) {
        List<Rule> loaded = new ArrayList<>();
        for (int i = 0; i < current.size(); i++) {
            String[] parts = current.get(i).split("\\|", -1);
            if (parts.length != 4 && parts.length != 6) continue;
            ResourceLocation item = ResourceLocation.tryParse(parts[0].trim());
            if (item == null) continue;
            try {
                int required = Integer.parseInt(parts[2].trim());
                boolean unusable = parts[3].trim().equalsIgnoreCase("UNUSABLE");
                if (!unusable && !parts[3].trim().equalsIgnoreCase("DEBUFF")) continue;
                ResourceLocation target = parts.length == 6 ? ResourceLocation.tryParse(parts[4].trim()) : null;
                double strength = parts.length == 6 ? Double.parseDouble(parts[5].trim()) : 0;
                if (parts.length == 6 && (target == null || !Double.isFinite(strength))) continue;
                loaded.add(new Rule(item, parts[1].trim(), required, unusable, target, strength, i,
                        unusable ? Kind.UNUSABLE : Kind.LEGACY_DEBUFF));
            } catch (NumberFormatException ignored) {}
        }
        return List.copyOf(loaded);
    }

    private static List<Rule> active(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        return active(rules(), BuiltInRegistries.ITEM.getKey(stack.getItem()),
                stat -> StatlogApi.attributeLevel(player, stat));
    }

    static List<Rule> active(List<Rule> available, ResourceLocation item, ToIntFunction<String> level) {
        Map<String, Integer> nextTier = new HashMap<>();
        for (Rule rule : available) {
            if (!rule.item().equals(item)) continue;
            try {
                if (level.applyAsInt(rule.stat()) < rule.required())
                    nextTier.merge(rule.stat(), rule.required(), Math::min);
            } catch (IllegalArgumentException ignored) {}
        }
        List<Rule> selected = new ArrayList<>();
        for (Rule rule : available)
            if (rule.item().equals(item) && nextTier.getOrDefault(rule.stat(), -1) == rule.required())
                selected.add(rule);
        return selected;
    }

    public static boolean unusable(ServerPlayer player, ItemStack stack) {
        for (Rule rule : active(player, stack)) if (rule.unusable()) return true;
        return StatlogHooks.itemMissing(player, stack, "UNUSABLE");
    }

    public static float itemMultiplier(ServerPlayer player, ItemStack stack, Kind kind) {
        return multiplier(active(player, stack), kind);
    }
    static float multiplier(List<Rule> active, Kind kind) {
        double value = 1;
        for (Rule rule : active) if (rule.kind() == kind) value *= rule.strength();
        return (float) value;
    }

    public static void applyDebuffs(ServerPlayer player) {
        clear(player);
        List<ModifierRef> applied = new ArrayList<>();
        Map<Holder<MobEffect>, Integer> effects = new HashMap<>();
        int slot = 0;
        for (ItemStack stack : player.getAllSlots()) {
            boolean legacy = !stack.isEmpty() && StatlogHooks.itemMissing(player, stack, "DEBUFF");
            for (Rule rule : active(player, stack)) {
                if (rule.unusable()) continue;
                if (rule.kind() == Kind.LEGACY_DEBUFF && rule.target() == null) { legacy = true; continue; }
                if (rule.kind() != Kind.LEGACY_DEBUFF && rule.kind() != Kind.PLAYER_ATTRIBUTE
                        && rule.kind() != Kind.EFFECT) continue;
                Holder<Attribute> attribute = rule.kind() == Kind.EFFECT ? null
                        : BuiltInRegistries.ATTRIBUTE.getHolder(rule.target()).orElse(null);
                if (attribute != null) {
                    AttributeInstance instance = player.getAttribute(attribute);
                    if (instance == null) continue;
                    ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Statlog.ID,
                            "item_debuff/" + slot + "/" + rule.index());
                    instance.addTransientModifier(new AttributeModifier(id, rule.strength(),
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                    applied.add(new ModifierRef(attribute, id));
                    continue;
                }
                Holder<MobEffect> effect = rule.kind() == Kind.PLAYER_ATTRIBUTE ? null
                        : BuiltInRegistries.MOB_EFFECT.getHolder(rule.target()).orElse(null);
                if (effect != null) effects.merge(effect, Math.max(1, (int) rule.strength() + 1), Integer::sum);
            }
            if (legacy) addLegacyEffects(effects);
            slot++;
        }
        MODIFIERS.put(player, applied);
        effects.forEach((effect, stacks) -> player.addEffect(new MobEffectInstance(effect, 40,
                Math.min(255, stacks - 1), true, false, false)));
    }

    private static void addLegacyEffects(Map<Holder<MobEffect>, Integer> effects) {
        for (String configured : StatlogConfig.DEBUFF_EFFECTS.get()) {
            String[] parts = configured.split("\\|", 2);
            ResourceLocation key = ResourceLocation.tryParse(parts[0].trim());
            if (key == null) continue;
            Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.getHolder(key).orElse(null);
            if (effect == null) continue;
            try {
                int amplifier = parts.length == 2 ? Integer.parseInt(parts[1].trim()) : 0;
                effects.merge(effect, Math.max(1, amplifier + 1), Integer::sum);
            } catch (NumberFormatException ignored) {}
        }
    }

    public static void clear(ServerPlayer player) {
        for (ModifierRef ref : MODIFIERS.getOrDefault(player, List.of())) {
            AttributeInstance instance = player.getAttribute(ref.attribute());
            if (instance != null) instance.removeModifier(ref.id());
        }
        MODIFIERS.remove(player);
    }

    private ItemRules() {}
}
