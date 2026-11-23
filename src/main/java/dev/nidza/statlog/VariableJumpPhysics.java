package dev.nidza.statlog;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class VariableJumpPhysics {
    public static final int BOOST_TICKS = 4;

    public static double minimumImpulse(LivingEntity entity, double minimumStrength) {
        double strength = entity.getAttributeValue(Attributes.JUMP_STRENGTH);
        double original = entity.getDeltaMovement().y;
        double boost = entity.getJumpBoostPower();
        double blockFactor = strength > 1.0E-5 ? (original - boost) / strength : 1.0;
        return Math.max(0.01, minimumStrength * Math.max(0, blockFactor) + boost);
    }

    public static double rise(double impulse, double gravity) {
        double height = 0;
        double velocity = impulse;
        for (int tick = 0; tick < 100 && velocity > 0; tick++) {
            height += velocity;
            velocity = (velocity - gravity) * 0.98;
        }
        return height;
    }

    public static double boostWeight(int tick) {
        // The first tick stays vanilla, so releasing a quick tap adds no lift.
        return switch (tick) {
            case 1 -> 0.75;
            case 2 -> 0.6;
            case 3 -> 0.3;
            default -> 0;
        };
    }

    public static double holdBoost(double minimumImpulse, double maximumImpulse, double gravity) {
        double target = rise(maximumImpulse, gravity);
        double low = 0;
        double high = Math.max(0.01, maximumImpulse - minimumImpulse);
        while (riseWithBoost(minimumImpulse, gravity, high) < target) high *= 2;
        for (int i = 0; i < 28; i++) {
            double middle = (low + high) / 2;
            if (riseWithBoost(minimumImpulse, gravity, middle) < target) low = middle;
            else high = middle;
        }
        return high;
    }

    private static double riseWithBoost(double impulse, double gravity, double boost) {
        double height = 0;
        double velocity = impulse;
        for (int tick = 0; tick < 100; tick++) {
            height += Math.max(0, velocity);
            velocity = (velocity - gravity) * 0.98 + boost * boostWeight(tick);
            if (velocity <= 0 && tick >= BOOST_TICKS) break;
        }
        return height;
    }

    private VariableJumpPhysics() {}
}
