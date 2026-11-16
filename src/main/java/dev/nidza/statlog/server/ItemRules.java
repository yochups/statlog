package dev.nidza.statlog.server;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.mojang.logging.LogUtils;
import dev.nidza.statlog.Statlog;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
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
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.ToIntFunction;

public final class ItemRules {
    enum Kind { UNUSABLE, EFFECT, PLAYER_ATTRIBUTE, ITEM_DAMAGE, ITEM_MINING_SPEED }
    record Rule(ResourceLocation item, String stat, int required, boolean unusable,
                        ResourceLocation target, double strength, int index, Kind kind) {}
    record Restriction(String item, String block, Set<String> actions, String stat, int required, String message) {}
    private record ModifierRef(Holder<Attribute> attribute, ResourceLocation id) {}

    private static final Logger LOGGER = LogUtils.getLogger();
    private static List<Rule> fileRules = List.of();
    private static List<Restriction> restrictions = List.of();
    private static final Map<ServerPlayer, List<ModifierRef>> MODIFIERS = new WeakHashMap<>();

    private static List<Rule> rules() { return fileRules; }

    public static synchronized void load() {
        Path file = FMLPaths.CONFIGDIR.get().resolve("statlog/statlog-tiers.toml");
        try {
            Files.createDirectories(file.getParent());
            if (Files.notExists(file)) try (InputStream template = ItemRules.class.getResourceAsStream("/default-statlog-tiers.toml")) {
                if (template == null) throw new IOException("Default tier config missing");
                Files.copy(template, file);
            }
            UnmodifiableConfig config = new TomlParser().parse(Files.readString(file));
            fileRules = parseTiers(config);
            restrictions = parseRestrictions(config);
            LOGGER.info("Loaded {} Statlog tier rules and {} restrictions", fileRules.size(), restrictions.size());
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
                Object rawType = row.get("type");
                if ("RESTRICT".equalsIgnoreCase(String.valueOf(rawType))) continue;
                ResourceLocation item = ResourceLocation.parse((String) row.get("item"));
                String stat = (String) row.get("stat");
                int required = ((Number) row.get("required_level")).intValue();
                Kind kind = Kind.valueOf(((String) row.get("type")).toUpperCase(Locale.ROOT));
                if (stat.isBlank() || required < 0)
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

    static List<Restriction> parseRestrictions(UnmodifiableConfig config) {
        List<Restriction> loaded = new ArrayList<>();
        Object entries = config.get("tiers");
        if (!(entries instanceof List<?> rows)) return List.of();
        for (int i = 0; i < rows.size(); i++) {
            if (!(rows.get(i) instanceof UnmodifiableConfig row)) continue;
            Object rawType = row.get("type");
            if (!"RESTRICT".equalsIgnoreCase(String.valueOf(rawType))) continue;
            try {
                String item = row.get("item") instanceof String s ? s : "";
                String block = row.get("block") instanceof String s ? s : "";
                if (item.isBlank() && block.isBlank()) throw new IllegalArgumentException("item or block is required");
                for (String selector : List.of(item, block)) if (!selector.isBlank()
                        && ResourceLocation.tryParse(selector.startsWith("#") ? selector.substring(1) : selector) == null)
                    throw new IllegalArgumentException("invalid item or block selector " + selector);
                Object rawActions = row.get("actions");
                if (!(rawActions instanceof List<?> values) || values.isEmpty())
                    throw new IllegalArgumentException("actions must be a nonempty list");
                Set<String> actions = new java.util.LinkedHashSet<>();
                for (Object value : values) {
                    String action = String.valueOf(value).toUpperCase(Locale.ROOT);
                    if (!Set.of("USE_ITEM", "EAT", "USE_BLOCK", "PLACE_BLOCK", "BREAK_BLOCK",
                            "ATTACK_ENTITY", "INTERACT_ENTITY").contains(action))
                        throw new IllegalArgumentException("unknown action " + action);
                    actions.add(action);
                }
                String stat = (String) row.get("stat");
                int required = ((Number) row.get("required_level")).intValue();
                if (stat == null || stat.isBlank() || required < 0) throw new IllegalArgumentException("invalid stat or level");
                loaded.add(new Restriction(item, block, Set.copyOf(actions), stat, required,
                        row.get("message") instanceof String s ? s : ""));
            } catch (RuntimeException error) {
                LOGGER.warn("Ignoring Statlog restriction {}: {}", i + 1, error.getMessage());
            }
        }
        return List.copyOf(loaded);
    }

    public static boolean restricted(ServerPlayer player, ItemStack stack, BlockState block, String action) {
        for (Restriction rule : restrictions) {
            if (!rule.actions().contains(action) || !matchesItem(rule.item(), stack)
                    || !matchesBlock(rule.block(), block)) continue;
            int level;
            try { level = StatlogApi.attributeLevel(player, rule.stat()); }
            catch (IllegalArgumentException ignored) { continue; }
            if (level >= rule.required()) continue;
            if (!rule.message().isBlank()) player.displayClientMessage(Component.literal(rule.message()
                    .replace("{stat}", rule.stat()).replace("{level}", Integer.toString(level))
                    .replace("{required}", Integer.toString(rule.required()))), true);
            return true;
        }
        return false;
    }
    private static boolean matchesItem(String selector, ItemStack stack) {
        if (selector.isBlank()) return true;
        if (stack.isEmpty()) return false;
        ResourceLocation id = ResourceLocation.parse(selector.startsWith("#") ? selector.substring(1) : selector);
        return selector.startsWith("#") ? stack.is(TagKey.create(Registries.ITEM, id))
                : BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id);
    }
    private static boolean matchesBlock(String selector, BlockState state) {
        if (selector.isBlank()) return true;
        if (state == null) return false;
        ResourceLocation id = ResourceLocation.parse(selector.startsWith("#") ? selector.substring(1) : selector);
        return selector.startsWith("#") ? state.is(TagKey.create(Registries.BLOCK, id))
                : BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(id);
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
            for (Rule rule : active(player, stack)) {
                if (rule.unusable()) continue;
                if (rule.kind() != Kind.PLAYER_ATTRIBUTE && rule.kind() != Kind.EFFECT) continue;
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
            slot++;
        }
        MODIFIERS.put(player, applied);
        effects.forEach((effect, stacks) -> player.addEffect(new MobEffectInstance(effect, 40,
                Math.min(255, stacks - 1), true, false, false)));
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
