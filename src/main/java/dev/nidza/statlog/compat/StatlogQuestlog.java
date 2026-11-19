package dev.nidza.statlog.compat;

import com.google.gson.JsonObject;
import dev.nidza.statlog.Statlog;
import dev.nidza.statlog.config.AttributeDefinition;
import dev.nidza.statlog.config.AttributeDefinitions;
import dev.nidza.statlog.server.StatlogApi;
import dev.nidza.statlog.server.StatlogHooks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.infernalstudios.questlog.core.quests.EditorMetadata;
import org.infernalstudios.questlog.core.quests.QuestObjectiveRegistry;
import org.infernalstudios.questlog.core.quests.QuestRewardRegistry;
import org.infernalstudios.questlog.core.quests.objectives.Objective;
import org.infernalstudios.questlog.core.quests.rewards.Reward;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class StatlogQuestlog {
    private static final List<WeakReference<StatObjective>> OBJECTIVES = new ArrayList<>();

    public static void register() {
        QuestObjectiveRegistry.register(id("stat"), StatObjective::new,
                new EditorMetadata("stat", "Stat", "required_amount", "Levels"));
        QuestRewardRegistry.register(id("currency"), CurrencyReward::new,
                new EditorMetadata("currency", "Currency", "amount", "Amount"));
        QuestRewardRegistry.register(id("stat"), StatReward::new,
                new EditorMetadata("stat", "Stat", "amount", "Levels"));
        StatlogHooks.onLevelChanged(StatlogQuestlog::changed);
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post event) -> {
            if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0)
                for (WeakReference<StatObjective> reference : List.copyOf(OBJECTIVES)) {
                    StatObjective objective = reference.get();
                    if (objective != null) objective.checkReached(player);
                }
        });
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Statlog.ID, path);
    }

    private static void changed(ServerPlayer player, AttributeDefinition def, int oldLevel, int newLevel) {
        OBJECTIVES.removeIf(ref -> ref.get() == null);
        for (WeakReference<StatObjective> reference : OBJECTIVES) {
            StatObjective objective = reference.get();
            if (objective != null) objective.changed(player, def, oldLevel, newLevel);
        }
    }

    private static String requiredString(JsonObject json, String field) {
        if (!json.has(field) || !json.get(field).isJsonPrimitive())
            throw new IllegalArgumentException("Questlog Statlog entry requires " + field);
        return json.get(field).getAsString();
    }

    private static int amount(JsonObject json) {
        int amount = json.has("amount") ? json.get("amount").getAsInt() : 1;
        if (amount <= 0) throw new IllegalArgumentException("Questlog Statlog reward amount must be positive");
        return amount;
    }

    public static final class StatObjective extends Objective {
        private final String stat;
        private final String mode;

        public StatObjective(JsonObject json) {
            super(json);
            stat = requiredString(json, "stat");
            mode = json.has("mode") ? json.get("mode").getAsString().toUpperCase(Locale.ROOT) : "GAIN";
            if (!List.of("GAIN", "LOSS", "REACH").contains(mode))
                throw new IllegalArgumentException("Questlog Statlog mode must be GAIN, LOSS, or REACH");
        }

        @Override public void registerEventListeners() {
            super.registerEventListeners();
            OBJECTIVES.add(new WeakReference<>(this));
        }

        private boolean belongsTo(ServerPlayer player) {
            return getParent() != null && getParent().manager.player == player && !isCompleted();
        }

        private void changed(ServerPlayer player, AttributeDefinition def, int oldLevel, int newLevel) {
            if (!belongsTo(player)) return;
            AttributeDefinition selected = AttributeDefinitions.resolve(stat);
            if (selected == null || !selected.attribute().equals(def.attribute())) return;
            int delta = switch (mode) {
                case "GAIN" -> Math.max(0, newLevel - oldLevel);
                case "LOSS" -> Math.max(0, oldLevel - newLevel);
                default -> 0;
            };
            if (mode.equals("REACH")) setUnits(Math.max(0, newLevel));
            else if (delta > 0) setUnits(getUnits() + delta);
        }

        private void checkReached(ServerPlayer player) {
            if (!mode.equals("REACH") || !belongsTo(player)) return;
            try { setUnits(Math.max(0, StatlogApi.attributeLevel(player, stat))); }
            catch (IllegalArgumentException ignored) {}
        }
    }

    public static final class CurrencyReward extends Reward {
        private final String currency;
        private final int amount;

        public CurrencyReward(JsonObject json) {
            super(json);
            currency = requiredString(json, "currency");
            amount = amount(json);
        }

        @Override public void applyReward(ServerPlayer player) {
            StatlogApi.addPoints(player, currency, amount);
            super.applyReward(player);
        }
    }

    public static final class StatReward extends Reward {
        private final String stat;
        private final int amount;
        private final boolean set;

        public StatReward(JsonObject json) {
            super(json);
            stat = requiredString(json, "stat");
            set = json.has("operation") && json.get("operation").getAsString().equalsIgnoreCase("SET");
            amount = json.has("amount") ? json.get("amount").getAsInt() : 1;
            if (amount < 0 || (!set && amount == 0))
                throw new IllegalArgumentException("Questlog Statlog stat reward amount is invalid");
        }

        @Override public void applyReward(ServerPlayer player) {
            AttributeDefinition def = AttributeDefinitions.resolve(stat);
            if (def == null) throw new IllegalArgumentException("Unknown Statlog stat " + stat);
            int next = set ? amount : Math.min(def.maxLevel(), StatlogApi.attributeLevel(player, stat) + amount);
            StatlogApi.setAttributeLevel(player, stat, next);
            super.applyReward(player);
        }
    }

    private StatlogQuestlog() {}
}
