package dev.nidza.statlog.data;

import dev.nidza.statlog.config.AttributeDefinition;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Persisted player attachment. Addons can read it with player.getData(Statlog.PROGRESS). */
public final class PlayerProgress implements INBTSerializable<CompoundTag> {
    public record Purchase(String currency, int amount) {}

    private int xp;
    private int progressionLevel = 1;
    private boolean editAllowed;
    private boolean respecAllowed;
    private final Map<String, Integer> levels = new HashMap<>();
    private final Map<String, Integer> balances = new HashMap<>();
    private final Map<String, List<Purchase>> purchases = new HashMap<>();
    private final Map<String, Boolean> upgradeAllowed = new HashMap<>();

    public int xp() { return xp; }
    public void xp(int value) { xp = Math.max(0, value); }
    public int progressionLevel() { return progressionLevel; }
    public void progressionLevel(int value) { progressionLevel = Math.max(0, value); }
    public boolean editAllowed() { return editAllowed; }
    public void editAllowed(boolean value) { editAllowed = value; }
    public boolean respecAllowed() { return respecAllowed; }
    public void respecAllowed(boolean value) { respecAllowed = value; }
    public int level(AttributeDefinition def) { return levels.getOrDefault(def.attribute(), def.startingLevel()); }
    public int level(String attribute, int fallback) { return levels.getOrDefault(attribute, fallback); }
    public void setLevel(String attribute, int level) { levels.put(attribute, level); }
    public void resetStats(int startingLevel) {
        xp = 0;
        progressionLevel = Math.max(0, startingLevel);
        levels.clear();
        balances.clear();
        purchases.clear();
    }
    public Map<String, Integer> levels() { return Map.copyOf(levels); }
    public int balance(String currency) { return balances.getOrDefault(currency, 0); }
    public void balance(String currency, int amount) { balances.put(currency, Math.max(0, amount)); }
    public Map<String, Integer> balances() { return Map.copyOf(balances); }
    public List<Purchase> purchases(String attribute) { return purchases.computeIfAbsent(attribute, ignored -> new ArrayList<>()); }
    public boolean upgradeAllowed(String attribute) { return upgradeAllowed.getOrDefault(attribute, true); }
    public void upgradeAllowed(String attribute, boolean allowed) { upgradeAllowed.put(attribute, allowed); }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("XP", xp);
        tag.putInt("ProgressionLevel", progressionLevel);
        tag.putBoolean("EditAllowed", editAllowed);
        tag.putBoolean("RespecAllowed", respecAllowed);
        tag.put("Levels", intMap(levels));
        tag.put("Balances", intMap(balances));
        CompoundTag history = new CompoundTag();
        for (var entry : purchases.entrySet()) {
            ListTag list = new ListTag();
            for (Purchase purchase : entry.getValue()) {
                CompoundTag item = new CompoundTag();
                item.putString("Currency", purchase.currency());
                item.putInt("Amount", purchase.amount());
                list.add(item);
            }
            history.put(entry.getKey(), list);
        }
        tag.put("Purchases", history);
        CompoundTag access = new CompoundTag();
        upgradeAllowed.forEach(access::putBoolean);
        tag.put("UpgradeAllowed", access);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        xp = Math.max(0, tag.getInt("XP"));
        progressionLevel = Math.max(0, tag.getInt("ProgressionLevel"));
        editAllowed = tag.getBoolean("EditAllowed");
        respecAllowed = tag.getBoolean("RespecAllowed");
        levels.clear(); readIntMap(tag.getCompound("Levels"), levels);
        balances.clear(); readIntMap(tag.getCompound("Balances"), balances);
        purchases.clear();
        CompoundTag history = tag.getCompound("Purchases");
        for (String key : history.getAllKeys()) {
            List<Purchase> list = new ArrayList<>();
            for (Tag item : history.getList(key, Tag.TAG_COMPOUND)) {
                CompoundTag purchase = (CompoundTag) item;
                list.add(new Purchase(purchase.getString("Currency"), Math.max(0, purchase.getInt("Amount"))));
            }
            purchases.put(key, list);
        }
        upgradeAllowed.clear();
        CompoundTag access = tag.getCompound("UpgradeAllowed");
        for (String key : access.getAllKeys()) upgradeAllowed.put(key, access.getBoolean(key));
    }

    private static CompoundTag intMap(Map<String, Integer> values) {
        CompoundTag tag = new CompoundTag();
        values.forEach(tag::putInt);
        return tag;
    }
    private static void readIntMap(CompoundTag tag, Map<String, Integer> result) {
        for (String key : tag.getAllKeys()) result.put(key, tag.getInt(key));
    }
}
