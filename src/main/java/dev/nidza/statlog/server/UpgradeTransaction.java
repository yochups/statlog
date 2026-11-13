package dev.nidza.statlog.server;

import dev.nidza.statlog.config.AttributeDefinition;
import dev.nidza.statlog.config.AttributeDefinitions;
import dev.nidza.statlog.config.StatlogConfig;
import dev.nidza.statlog.data.PlayerProgress;
import dev.nidza.statlog.network.StatlogNetwork;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class UpgradeTransaction {
    public static String commit(ServerPlayer player, Map<String, Integer> requested,
                                Map<String, List<String>> choices, boolean operator) {
        return commit(player, requested, choices, operator, false);
    }

    private static String commit(ServerPlayer player, Map<String, Integer> requested,
                                 Map<String, List<String>> choices, boolean operator, boolean fullRefund) {
        if (!operator && !StatlogApi.canEdit(player)) return "Editing is no longer available";
        PlayerProgress data = StatlogApi.data(player);
        Map<String, Integer> balances = new HashMap<>(data.balances());
        Map<String, List<PlayerProgress.Purchase>> history = new HashMap<>();
        Map<String, Integer> newLevels = new HashMap<>();
        List<Spent> spent = new ArrayList<>();
        int vanillaXp = player.totalExperience;
        int vanillaLevels = player.experienceLevel;
        int statlogXp = data.xp();

        for (String id : requested.keySet())
            if (AttributeDefinitions.get(id) == null || !AttributeDefinitions.registered(id)) return "Unknown attribute " + id;
        List<Map.Entry<String, Integer>> ordered = new ArrayList<>(requested.entrySet());
        // Refund committed decreases before checking purchases in the same transaction.
        ordered.sort((a, b) -> Long.compare(
                (long) a.getValue() - data.level(AttributeDefinitions.get(a.getKey())),
                (long) b.getValue() - data.level(AttributeDefinitions.get(b.getKey()))));
        for (var change : ordered) {
            AttributeDefinition def = AttributeDefinitions.get(change.getKey());
            int oldLevel = data.level(def);
            int newLevel = change.getValue();
            if (newLevel < def.minLevel() || newLevel > def.maxLevel()) return "Level out of bounds for " + def.name();
            if (newLevel > oldLevel && !operator && !data.upgradeAllowed(def.attribute()))
                return "Upgrading " + def.name() + " is locked";
            if (newLevel < oldLevel && !operator && !StatlogApi.canRespec(player))
                return "Respeccing is unavailable";
            List<PlayerProgress.Purchase> receipts = new ArrayList<>(data.purchases(def.attribute()));
            if (newLevel < oldLevel) {
                for (int i = oldLevel; i > newLevel; i--) {
                    if (receipts.isEmpty()) continue; // Admin-granted levels carry no payment.
                    PlayerProgress.Purchase paid = receipts.removeLast();
                    int refund = fullRefund ? paid.amount()
                            : RespecMath.refund(paid.amount(), StatlogConfig.RESPEC_REFUND.get(),
                                    StatlogConfig.RESPEC_REFUND_PERCENTAGE.get());
                    if (refund == 0) continue;
                    switch (paid.currency()) {
                        case "XP_LEVELS" -> vanillaLevels = saturatingAdd(vanillaLevels, refund);
                        case "STATLOG_XP" -> statlogXp = saturatingAdd(statlogXp, refund);
                        case "XP" -> {
                            // Legacy receipts used XP for both vanilla points and independent XP.
                            if (StatlogConfig.CURRENCY_MODE.get().equalsIgnoreCase("STATLOG_XP"))
                                statlogXp = saturatingAdd(statlogXp, refund);
                            else vanillaXp = saturatingAdd(vanillaXp, refund);
                        }
                        default -> balances.merge(paid.currency(), refund, UpgradeTransaction::saturatingAdd);
                    }
                }
            } else if (newLevel > oldLevel) {
                List<String> currencies = choices.getOrDefault(def.attribute(), List.of());
                if (currencies.size() != newLevel - oldLevel) return "Choose a currency for each " + def.name() + " level";
                for (int level = oldLevel; level < newLevel; level++) {
                    String currency = currencies.get(level - oldLevel).toUpperCase(java.util.Locale.ROOT);
                    if (!accepts(def, currency)) return "Currency " + currency + " is not accepted by " + def.name();
                    if (!StatlogHooks.allow(player, def, level + 1, currency))
                        return "A scripted requirement blocks " + def.name();
                    int cost = cost(def, level, currency);
                    if (currency.equals("XP")) {
                        if (StatlogConfig.CURRENCY_MODE.get().equalsIgnoreCase("STATLOG_XP")) {
                            if (statlogXp < cost) return "Insufficient Statlog XP";
                            statlogXp -= cost;
                        } else if (StatlogConfig.CURRENCY_MODE.get().equalsIgnoreCase("XP_LEVELS")) {
                            if (vanillaLevels < cost) return "Insufficient XP levels";
                            vanillaLevels -= cost;
                        } else {
                            if (vanillaXp < cost) return "Insufficient Minecraft XP";
                            vanillaXp -= cost;
                        }
                    } else {
                        int balance = balances.getOrDefault(currency, 0);
                        if (balance < cost) return "Insufficient " + currency;
                        balances.put(currency, balance - cost);
                    }
                    String paidCurrency = currency.equals("XP")
                            ? StatlogConfig.CURRENCY_MODE.get().toUpperCase(java.util.Locale.ROOT) : currency;
                    receipts.add(new PlayerProgress.Purchase(paidCurrency, cost));
                    spent.add(new Spent(def, currency, cost));
                }
            }
            history.put(def.attribute(), receipts);
            newLevels.put(def.attribute(), newLevel);
        }

        int vanillaDelta = vanillaXp - player.totalExperience;
        int levelDelta = vanillaLevels - player.experienceLevel;
        if (levelDelta != 0) player.giveExperienceLevels(levelDelta);
        if (vanillaDelta != 0) player.giveExperiencePoints(vanillaDelta);
        int previousStatlogXp = data.xp();
        data.xp(statlogXp);
        balances.forEach(data::balance);
        newLevels.forEach((attribute, level) -> {
            AttributeDefinition def = AttributeDefinitions.get(attribute);
            int oldLevel = data.level(def);
            data.setLevel(attribute, level);
            List<PlayerProgress.Purchase> purchases = data.purchases(attribute);
            purchases.clear();
            purchases.addAll(history.get(attribute));
            AttributeManager.apply(player, def);
            if (oldLevel != level) StatlogHooks.changed(player, def, oldLevel, level);
        });
        StatlogHooks.xpChanged(player, previousStatlogXp, statlogXp);
        spent.forEach(payment -> StatlogHooks.currencySpent(player, payment.attribute(), payment.currency(), payment.amount()));
        StatlogNetwork.sync(player);
        return null;
    }

    public static String respecCommand(ServerPlayer player, String attribute, String value) {
        AttributeDefinition def = AttributeDefinitions.get(attribute);
        if (def == null || !AttributeDefinitions.registered(attribute)) return "Unknown configured attribute";
        int allocated = StatlogApi.data(player).level(def) - def.minLevel();
        int amount;
        try {
            amount = RespecMath.levelsToRemove(allocated, value);
        } catch (IllegalArgumentException ex) { return ex.getMessage(); }
        int target = Math.max(def.minLevel(), StatlogApi.data(player).level(def) - amount);
        return commit(player, Map.of(attribute, target), Map.of(), true);
    }

    public static String respecAll(ServerPlayer player) {
        Map<String, Integer> targets = new HashMap<>();
        for (AttributeDefinition def : AttributeDefinitions.all().values())
            if (AttributeDefinitions.registered(def.attribute())) targets.put(def.attribute(), def.minLevel());
        return commit(player, targets, Map.of(), true, true);
    }

    public static int cost(AttributeDefinition def, int currentLevel, String currency) {
        if (!currency.equals("XP")) return def.cost();
        long linear = (long) StatlogConfig.XP_COST_BASE.get()
                + (long) Math.max(0, currentLevel - def.minLevel()) * StatlogConfig.XP_COST_INCREMENT.get();
        if (def.cost() == 0) return 0;
        return linear > Integer.MAX_VALUE / def.cost() ? Integer.MAX_VALUE : (int) (linear * def.cost());
    }
    public static boolean accepts(AttributeDefinition def, String currency) {
        if (!currency.equals("XP") && !AttributeDefinitions.customPoints().contains(currency)) return false;
        return def.acceptedPoints().contains("ALL") || def.acceptedPoints().contains(currency);
    }
    public static int xpBalance(ServerPlayer player) {
        if (StatlogConfig.CURRENCY_MODE.get().equalsIgnoreCase("STATLOG_XP")) return StatlogApi.data(player).xp();
        if (StatlogConfig.CURRENCY_MODE.get().equalsIgnoreCase("XP_LEVELS")) return player.experienceLevel;
        return player.totalExperience;
    }
    private static int saturatingAdd(int a, int b) { return (int) Math.min(Integer.MAX_VALUE, (long) a + b); }
    private record Spent(AttributeDefinition attribute, String currency, int amount) {}
    private UpgradeTransaction() {}
}
