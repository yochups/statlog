package dev.nidza.statlog.client;

import dev.nidza.statlog.config.AttributeDefinition;
import dev.nidza.statlog.config.AttributeTab;
import dev.nidza.statlog.config.StatEffect;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;

public final class ClientState {
    private static CompoundTag progress = new CompoundTag();
    private static Map<String, AttributeDefinition> definitions = Map.of();
    private static List<AttributeTab> tabs = List.of();
    private static Set<String> customPoints = Set.of();
    private static CompoundTag pointNames = new CompoundTag();
    private static CompoundTag pointColors = new CompoundTag();
    private static CompoundTag pointIcons = new CompoundTag();
    private static CompoundTag pointOrbTextures = new CompoundTag();
    private static CompoundTag pointOrbColors = new CompoundTag();
    private static CompoundTag attributeBases = new CompoundTag();
    private static boolean canEdit;
    private static boolean canRespec;
    private static String title = "Attributes";
    private static String currencyMode = "XP_LEVELS";
    private static int xpCostBase = 1;
    private static int xpCostIncrement;
    private static int nextLevelXp = 100;
    private static boolean remote;
    private static UUID targetId;
    private static String targetName = "";
    private static int vanillaLevel;
    private static int vanillaExperience;
    private static float vanillaProgress;
    private static boolean linearVanillaXp;
    private static int vanillaXpPerLevel = 50;

    public static void update(CompoundTag snapshot) {
        progress = snapshot.getCompound("Progress");
        canEdit = snapshot.getBoolean("CanEdit");
        canRespec = snapshot.getBoolean("CanRespec");
        title = snapshot.getString("Title");
        currencyMode = snapshot.getString("CurrencyMode");
        xpCostBase = snapshot.getInt("XpCostBase");
        xpCostIncrement = snapshot.getInt("XpCostIncrement");
        nextLevelXp = Math.max(1, snapshot.getInt("NextLevelXp"));
        remote = snapshot.getBoolean("Remote");
        try { targetId = UUID.fromString(snapshot.getString("TargetId")); }
        catch (IllegalArgumentException ignored) { targetId = null; }
        targetName = snapshot.getString("TargetName");
        vanillaLevel = snapshot.getInt("VanillaLevel");
        vanillaExperience = snapshot.getInt("VanillaExperience");
        vanillaProgress = snapshot.getFloat("VanillaProgress");
        linearVanillaXp = snapshot.getBoolean("LinearVanillaXp");
        vanillaXpPerLevel = Math.max(1, snapshot.getInt("VanillaXpPerLevel"));
        Map<String, AttributeDefinition> loaded = new LinkedHashMap<>();
        for (Tag raw : snapshot.getList("Definitions", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) raw;
            List<String> currencies = new ArrayList<>();
            for (Tag currency : row.getList("Currencies", Tag.TAG_STRING)) currencies.add(currency.getAsString());
            List<String> assignedTabs = new ArrayList<>();
            for (Tag tab : row.getList("Tabs", Tag.TAG_STRING)) assignedTabs.add(tab.getAsString());
            List<StatEffect> extra = new ArrayList<>();
            for (Tag rawEffect : row.getList("ExtraAttributes", Tag.TAG_COMPOUND)) {
                CompoundTag effect = (CompoundTag) rawEffect;
                extra.add(new StatEffect(effect.getString("Id"), effect.getDouble("Increment"),
                        effect.getBoolean("Percent"), levelValues(effect.getCompound("LevelValues")),
                        effect.getDouble("PercentageBase")));
            }
            AttributeDefinition def = new AttributeDefinition(row.getString("Id"), row.getString("Name"),
                    row.getString("Description"), row.getString("Icon"), row.getInt("Min"), row.getInt("Max"),
                    row.getInt("Start"), row.getDouble("Increment"), row.getInt("Cost"), currencies,
                    assignedTabs, row.getBoolean("MaxIcon"), row.getBoolean("MaxIconSepia"), row.getBoolean("Percent"),
                    row.getString("CurrentText"), row.getString("IncrementText"),
                    levelValues(row.getCompound("LevelValues")), row.getDouble("PercentageBase"), List.copyOf(extra));
            loaded.put(def.attribute(), def);
        }
        definitions = java.util.Collections.unmodifiableMap(loaded);
        List<AttributeTab> loadedTabs = new ArrayList<>();
        for (Tag raw : snapshot.getList("Tabs", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) raw;
            loadedTabs.add(new AttributeTab(row.getString("Id"), row.getString("Name"),
                    row.getString("Item"), row.getString("Texture"), row.getBoolean("Visible")));
        }
        tabs = List.copyOf(loadedTabs);
        Set<String> points = new LinkedHashSet<>();
        for (Tag point : snapshot.getList("CustomPoints", Tag.TAG_STRING)) points.add(point.getAsString());
        customPoints = java.util.Collections.unmodifiableSet(points);
        pointNames = snapshot.getCompound("PointNames");
        pointColors = snapshot.getCompound("PointColors");
        pointIcons = snapshot.getCompound("PointIcons");
        pointOrbTextures = snapshot.getCompound("PointOrbTextures");
        pointOrbColors = snapshot.getCompound("PointOrbColors");
        attributeBases = snapshot.getCompound("AttributeBases");
    }
    public static Map<String, AttributeDefinition> definitions() { return definitions; }
    public static List<AttributeTab> tabs() { return tabs; }
    public static Set<String> customPoints() { return customPoints; }
    public static String pointName(String id) {
        if (id.equals("XP")) return currencyMode.equalsIgnoreCase("XP_LEVELS") ? "XP Levels" : "XP";
        return pointNames.contains(id) ? pointNames.getString(id) : id;
    }
    public static Component pointLabel(String id) {
        String name = pointName(id);
        return StatlogLanguage.value(name);
    }
    public static boolean remote() { return remote; }
    public static UUID targetId() { return targetId; }
    public static String targetName() { return targetName; }
    public static int vanillaLevel() { return vanillaLevel; }
    public static int vanillaExperience() { return vanillaExperience; }
    public static float vanillaProgress() { return vanillaProgress; }
    public static boolean linearVanillaXp() { return linearVanillaXp; }
    public static int vanillaXpPerLevel() { return vanillaXpPerLevel; }
    public static int pointColor(String id) {
        try { return 0xFF000000 | Integer.parseInt(pointColors.getString(id).replace("#", ""), 16); }
        catch (NumberFormatException ignored) { return 0xFFFFFFFF; }
    }
    public static String pointIcon(String id) { return pointIcons.getString(id); }
    public static String pointOrbTexture(String id) { return pointOrbTextures.getString(id); }
    public static int pointOrbColor(String id) {
        try { return 0xFF000000 | Integer.parseInt(pointOrbColors.getString(id).replace("#", ""), 16); }
        catch (NumberFormatException ignored) { return pointColor(id); }
    }
    public static double attributeBase(String id) { return attributeBases.getDouble(id); }
    private static Map<Integer, Double> levelValues(CompoundTag values) {
        Map<Integer, Double> result = new LinkedHashMap<>();
        for (String key : values.getAllKeys()) try { result.put(Integer.parseInt(key), values.getDouble(key)); }
        catch (NumberFormatException ignored) {}
        return Map.copyOf(result);
    }
    public static boolean canEdit() { return canEdit; }
    public static boolean canRespec() { return canRespec; }
    public static String title() { return title; }
    public static String currencyMode() { return currencyMode; }
    public static int xpCostBase() { return xpCostBase; }
    public static int xpCostIncrement() { return xpCostIncrement; }
    public static int nextLevelXp() { return nextLevelXp; }
    public static int level(AttributeDefinition def) { return progress.getCompound("Levels").contains(def.attribute())
            ? progress.getCompound("Levels").getInt(def.attribute()) : def.startingLevel(); }
    public static int balance(String currency) { return progress.getCompound("Balances").getInt(currency); }
    public static int xp() { return progress.getInt("XP"); }
    public static int progressionLevel() { return progress.getInt("ProgressionLevel"); }
    public static boolean upgradeAllowed(String id) {
        CompoundTag access = progress.getCompound("UpgradeAllowed");
        return !access.contains(id) || access.getBoolean(id);
    }
    public static boolean hasAffordableUpgrade(LocalPlayer player) {
        if (player == null || !canEdit) return false;
        int xpBalance = currencyMode.equalsIgnoreCase("XP_LEVELS") ? remote ? vanillaLevel : player.experienceLevel
                : remote ? vanillaExperience : player.totalExperience;
        for (AttributeDefinition def : definitions.values()) {
            int current = level(def);
            if (current >= def.maxLevel() || !upgradeAllowed(def.attribute())) continue;
            boolean all = def.acceptedPoints().contains("ALL");
            long xpCost = (long) def.cost() * (xpCostBase + (long) (current - def.minLevel()) * xpCostIncrement);
            if ((all || def.acceptedPoints().contains("XP")) && xpBalance >= xpCost) return true;
            for (String currency : customPoints)
                if ((all || def.acceptedPoints().contains(currency)) && balance(currency) >= def.cost()) return true;
        }
        return false;
    }
    private ClientState() {}
}
