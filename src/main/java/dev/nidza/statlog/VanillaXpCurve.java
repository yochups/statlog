package dev.nidza.statlog;

import dev.nidza.statlog.config.StatlogConfig;
import net.minecraft.world.entity.player.Player;

public final class VanillaXpCurve {
    private static boolean clientLinear;
    private static int clientCost = 50;

    public static int cost(Player player) {
        if (player.level().isClientSide) {
            return clientLinear ? clientCost : -1;
        }
        return StatlogConfig.LINEAR_VANILLA_XP.get() ? StatlogConfig.VANILLA_XP_PER_LEVEL.get() : -1;
    }

    public static void setClient(boolean linear, int cost) {
        clientLinear = linear;
        clientCost = Math.max(1, cost);
    }

    public static void clearClient() {
        clientLinear = false;
        clientCost = 50;
    }

    private VanillaXpCurve() {}
}
