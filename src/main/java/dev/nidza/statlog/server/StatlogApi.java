package dev.nidza.statlog.server;

import dev.nidza.statlog.Statlog;
import dev.nidza.statlog.config.AttributeDefinition;
import dev.nidza.statlog.config.AttributeDefinitions;
import dev.nidza.statlog.config.StatlogConfig;
import dev.nidza.statlog.data.PlayerProgress;
import dev.nidza.statlog.network.StatlogNetwork;
import net.minecraft.server.level.ServerPlayer;

/** Server-side API for addons and scripting bridges. All mutations synchronize the player. */
public final class StatlogApi {
    public static PlayerProgress data(ServerPlayer player) { return player.getData(Statlog.PROGRESS); }
    public static int attributeLevel(ServerPlayer player, String attribute) {
        AttributeDefinition def = requireAttribute(attribute);
        return data(player).level(def);
    }
    /** Short-key friendly read for KubeJS: Statlog.statLevel(player, "strength"). */
    public static int statLevel(ServerPlayer player, String key) { return attributeLevel(player, key); }
    public static void setAttributeLevel(ServerPlayer player, String attribute, int level) {
        AttributeDefinition def = requireAttribute(attribute);
        if (level < def.minLevel() || level > def.maxLevel()) throw new IllegalArgumentException("Level outside configured bounds");
        int previous = data(player).level(def);
        var receipts = data(player).purchases(def.attribute());
        if (level > previous) for (int i = previous; i < level; i++)
            receipts.add(new PlayerProgress.Purchase("", 0));
        if (level < previous) for (int i = level; i < previous && !receipts.isEmpty(); i++)
            receipts.removeLast();
        data(player).setLevel(def.attribute(), level);
        AttributeManager.apply(player, def);
        if (previous != level) StatlogHooks.changed(player, def, previous, level);
        StatlogNetwork.sync(player);
    }
    public static void addAttributeLevel(ServerPlayer player, String attribute, int delta) {
        setAttributeLevel(player, attribute, Math.addExact(attributeLevel(player, attribute), delta));
    }
    /** Reset Statlog levels, progression XP, points, and receipts without touching vanilla XP or access permissions. */
    public static void resetStats(ServerPlayer player) {
        PlayerProgress data = data(player);
        int previousXp = data.xp();
        int previousLevel = data.progressionLevel();
        java.util.Map<String, Integer> previousAttributes = new java.util.HashMap<>();
        for (AttributeDefinition def : AttributeDefinitions.all().values())
            previousAttributes.put(def.attribute(), data.level(def));
        data.resetStats(StatlogConfig.STARTING_LEVEL.get());
        AttributeManager.apply(player);
        for (AttributeDefinition def : AttributeDefinitions.all().values()) {
            int old = previousAttributes.get(def.attribute());
            if (old != def.startingLevel()) StatlogHooks.changed(player, def, old, def.startingLevel());
        }
        StatlogHooks.xpChanged(player, previousXp, 0);
        StatlogHooks.progressionChanged(player, previousLevel, data.progressionLevel());
        StatlogNetwork.sync(player);
    }
    public static int xp(ServerPlayer player) { return data(player).xp(); }
    public static void addXp(ServerPlayer player, int delta) {
        setXp(player, Math.addExact(xp(player), delta));
    }
    public static void setXp(ServerPlayer player, int value) {
        if (value < 0) throw new IllegalArgumentException("XP must be nonnegative");
        int previous = data(player).xp();
        data(player).xp(value);
        StatlogHooks.xpChanged(player, previous, value);
        progressLevels(player);
        StatlogNetwork.sync(player);
    }
    public static int level(ServerPlayer player) { return data(player).progressionLevel(); }
    public static void setLevel(ServerPlayer player, int level) {
        if (level < StatlogConfig.STARTING_LEVEL.get() || level > StatlogConfig.MAX_LEVEL.get())
            throw new IllegalArgumentException("Progression level outside configured bounds");
        int previous = data(player).progressionLevel();
        data(player).progressionLevel(level);
        StatlogHooks.progressionChanged(player, previous, level);
        StatlogNetwork.sync(player);
    }
    public static int xpToNextLevel(int level) {
        for (String entry : StatlogConfig.MANUAL_XP_REQUIREMENTS.get()) {
            String[] fields = entry.split(":", 2);
            if (fields.length == 2) try {
                if (Integer.parseInt(fields[0].trim()) == level) return Math.max(1, Integer.parseInt(fields[1].trim()));
            } catch (NumberFormatException ignored) {}
        }
        long cost = (long) StatlogConfig.XP_REQUIREMENT_BASE.get()
                + (long) Math.max(0, level - StatlogConfig.STARTING_LEVEL.get()) * StatlogConfig.XP_REQUIREMENT_INCREMENT.get();
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, cost));
    }
    public static void progressLevels(ServerPlayer player) {
        PlayerProgress data = data(player);
        while (data.progressionLevel() < StatlogConfig.MAX_LEVEL.get() && data.xp() >= xpToNextLevel(data.progressionLevel())) {
            int cost = xpToNextLevel(data.progressionLevel());
            int previous = data.progressionLevel();
            data.xp(data.xp() - cost);
            data.progressionLevel(previous + 1);
            StatlogHooks.progressionChanged(player, previous, previous + 1);
            String currency = StatlogConfig.POINTS_AWARD_CURRENCY.get().trim().toUpperCase(java.util.Locale.ROOT);
            int award = StatlogConfig.POINTS_PER_LEVEL.get();
            if (award > 0 && AttributeDefinitions.customPoints().contains(currency))
                data.balance(currency, (int) Math.min(Integer.MAX_VALUE, (long) data.balance(currency) + award));
        }
    }
    public static int points(ServerPlayer player, String currency) {
        return data(player).balance(requireCurrency(currency));
    }
    public static void addPoints(ServerPlayer player, String currency, int delta) {
        setPoints(player, currency, Math.addExact(points(player, currency), delta));
    }
    public static boolean canAfford(ServerPlayer player, String attribute, String currency) {
        AttributeDefinition def = requireAttribute(attribute);
        String normalized = currency.toUpperCase(java.util.Locale.ROOT);
        if (!UpgradeTransaction.accepts(def, normalized) || !canUpgrade(player, attribute)) return false;
        int cost = UpgradeTransaction.cost(def, data(player).level(def), normalized);
        return normalized.equals("XP")
                ? UpgradeTransaction.xpBalance(player) >= cost
                : data(player).balance(normalized) >= cost;
    }
    public static void setPoints(ServerPlayer player, String currency, int amount) {
        String id = requireCurrency(currency);
        if (amount < 0) throw new IllegalArgumentException("Points must be nonnegative");
        data(player).balance(id, amount);
        StatlogNetwork.sync(player);
    }
    public static void allowEdit(ServerPlayer player, boolean allowed) {
        data(player).editAllowed(allowed);
        StatlogNetwork.sync(player);
    }
    public static boolean canEdit(ServerPlayer player) {
        return switch (StatlogConfig.EDIT_ACCESS.get().toUpperCase(java.util.Locale.ROOT)) {
            case "ALWAYS" -> true;
            case "SCRIPT", "COMMAND" -> data(player).editAllowed();
            case "POINTS" -> hasSpendablePoints(player);
            default -> false;
        };
    }
    private static boolean hasSpendablePoints(ServerPlayer player) {
        for (AttributeDefinition def : AttributeDefinitions.all().values()) {
            if (!AttributeDefinitions.registered(def.attribute()) || !data(player).upgradeAllowed(def.attribute())
                    || data(player).level(def) >= def.maxLevel()) continue;
            for (String currency : def.acceptedPoints().contains("ALL")
                    ? java.util.stream.Stream.concat(java.util.stream.Stream.of("XP"),
                            AttributeDefinitions.customPoints().stream()).toList() : def.acceptedPoints()) {
                int cost = UpgradeTransaction.cost(def, data(player).level(def), currency);
                int balance = currency.equals("XP")
                        ? UpgradeTransaction.xpBalance(player)
                        : data(player).balance(currency);
                if (balance >= cost) return true;
            }
        }
        return false;
    }
    public static void allowRespec(ServerPlayer player, boolean allowed) {
        data(player).respecAllowed(allowed);
        StatlogNetwork.sync(player);
    }
    public static boolean canRespec(ServerPlayer player) {
        return StatlogConfig.ENABLE_RESPECCING.get()
                && (!StatlogConfig.RESPEC_REQUIRES_PERMISSION.get() || data(player).respecAllowed());
    }
    public static void allowUpgrade(ServerPlayer player, String attribute, boolean allowed) {
        AttributeDefinition def = requireAttribute(attribute);
        data(player).upgradeAllowed(def.attribute(), allowed);
        StatlogNetwork.sync(player);
    }
    public static boolean canUpgrade(ServerPlayer player, String attribute) {
        AttributeDefinition def = requireAttribute(attribute);
        return canEdit(player) && data(player).upgradeAllowed(def.attribute()) && data(player).level(def) < def.maxLevel();
    }
    public static void open(ServerPlayer player, String mode) {
        StatlogNetwork.open(player, mode);
    }
    private static AttributeDefinition requireAttribute(String id) {
        AttributeDefinition def = AttributeDefinitions.resolve(id);
        if (def == null || !AttributeDefinitions.registered(def.attribute()))
            throw new IllegalArgumentException("Unknown configured stat: " + id + ". Use /statlog stat list");
        return def;
    }
    private static String requireCurrency(String id) {
        String normalized = id == null ? "" : id.trim().toUpperCase(java.util.Locale.ROOT);
        if (!AttributeDefinitions.customPoints().contains(normalized))
            throw new IllegalArgumentException("Unknown custom currency: " + id);
        return normalized;
    }
    private StatlogApi() {}
}
