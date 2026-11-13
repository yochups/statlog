package dev.nidza.statlog.server;

import com.mojang.logging.LogUtils;
import dev.nidza.statlog.config.AttributeDefinition;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.concurrent.CopyOnWriteArrayList;

/** Optional server-side hooks for addons and script bridges. Requirement callbacks must be side-effect free. */
public final class StatlogHooks {
    @FunctionalInterface public interface UpgradeRequirement {
        boolean test(ServerPlayer player, AttributeDefinition attribute, int nextLevel, String currency);
    }
    @FunctionalInterface public interface LevelChanged {
        void accept(ServerPlayer player, AttributeDefinition attribute, int oldLevel, int newLevel);
    }
    @FunctionalInterface public interface XpChanged {
        void accept(ServerPlayer player, int oldXp, int newXp);
    }
    @FunctionalInterface public interface ProgressionChanged {
        void accept(ServerPlayer player, int oldLevel, int newLevel);
    }
    @FunctionalInterface public interface CurrencySpent {
        void accept(ServerPlayer player, AttributeDefinition attribute, String currency, int amount);
    }
    @FunctionalInterface public interface ItemRequirement {
        boolean missing(ServerPlayer player, ItemStack stack, String restriction);
    }
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final CopyOnWriteArrayList<UpgradeRequirement> REQUIREMENTS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<LevelChanged> LISTENERS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<XpChanged> XP_LISTENERS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<ProgressionChanged> PROGRESSION_LISTENERS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<CurrencySpent> SPENDING_LISTENERS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<ItemRequirement> ITEM_REQUIREMENTS = new CopyOnWriteArrayList<>();
    public static Runnable addRequirement(UpgradeRequirement requirement) {
        REQUIREMENTS.add(requirement);
        return () -> REQUIREMENTS.remove(requirement);
    }
    public static Runnable onLevelChanged(LevelChanged listener) {
        LISTENERS.add(listener);
        return () -> LISTENERS.remove(listener);
    }
    public static Runnable onXpChanged(XpChanged listener) {
        XP_LISTENERS.add(listener);
        return () -> XP_LISTENERS.remove(listener);
    }
    public static Runnable onProgressionChanged(ProgressionChanged listener) {
        PROGRESSION_LISTENERS.add(listener);
        return () -> PROGRESSION_LISTENERS.remove(listener);
    }
    public static Runnable onCurrencySpent(CurrencySpent listener) {
        SPENDING_LISTENERS.add(listener);
        return () -> SPENDING_LISTENERS.remove(listener);
    }
    public static Runnable addItemRequirement(ItemRequirement requirement) {
        ITEM_REQUIREMENTS.add(requirement);
        return () -> ITEM_REQUIREMENTS.remove(requirement);
    }
    public static boolean allow(ServerPlayer player, AttributeDefinition def, int nextLevel, String currency) {
        for (UpgradeRequirement requirement : REQUIREMENTS) try {
            if (!requirement.test(player, def, nextLevel, currency)) return false;
        } catch (RuntimeException error) {
            LOGGER.error("Statlog upgrade requirement failed; denying upgrade", error);
            return false;
        }
        return true;
    }
    public static void changed(ServerPlayer player, AttributeDefinition def, int oldLevel, int newLevel) {
        for (LevelChanged listener : LISTENERS) try {
            listener.accept(player, def, oldLevel, newLevel);
        } catch (RuntimeException error) {
            LOGGER.error("Statlog level-change listener failed", error);
        }
    }
    public static void xpChanged(ServerPlayer player, int oldXp, int newXp) {
        if (oldXp == newXp) return;
        for (XpChanged listener : XP_LISTENERS) try {
            listener.accept(player, oldXp, newXp);
        } catch (RuntimeException error) {
            LOGGER.error("Statlog XP listener failed", error);
        }
    }
    public static void progressionChanged(ServerPlayer player, int oldLevel, int newLevel) {
        if (oldLevel == newLevel) return;
        for (ProgressionChanged listener : PROGRESSION_LISTENERS) try {
            listener.accept(player, oldLevel, newLevel);
        } catch (RuntimeException error) {
            LOGGER.error("Statlog progression listener failed", error);
        }
    }
    public static void currencySpent(ServerPlayer player, AttributeDefinition def, String currency, int amount) {
        for (CurrencySpent listener : SPENDING_LISTENERS) try {
            listener.accept(player, def, currency, amount);
        } catch (RuntimeException error) {
            LOGGER.error("Statlog currency-spent listener failed", error);
        }
    }
    public static boolean itemMissing(ServerPlayer player, ItemStack stack, String restriction) {
        for (ItemRequirement requirement : ITEM_REQUIREMENTS) try {
            if (requirement.missing(player, stack, restriction)) return true;
        } catch (RuntimeException error) {
            LOGGER.error("Statlog item requirement failed; restricting item", error);
            return true;
        }
        return false;
    }
    private StatlogHooks() {}
}
