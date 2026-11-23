package dev.nidza.statlog.client;

import dev.nidza.statlog.VariableJumpPhysics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

public final class VariableJump {
    private static LocalPlayer activePlayer;
    private static double targetY;
    private static double startY;
    private static double baselineVelocity;
    private static double boost;
    private static int boostTick;
    private static boolean released;

    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || !ClientState.holdToJumpHigher()
                || !ordinaryJump(player)) return;
        double original = player.getDeltaMovement().y;
        double minimum = VariableJumpPhysics.minimumImpulse(player, ClientState.minimumJumpStrength());
        double gravity = player.getGravity();
        if (!Double.isFinite(original) || !Double.isFinite(minimum) || gravity <= 0) return;
        Vec3 movement = player.getDeltaMovement();
        player.setDeltaMovement(movement.x, minimum, movement.z);
        double limit = VariableJumpPhysics.rise(Math.max(original, minimum), gravity);
        double base = VariableJumpPhysics.rise(minimum, gravity);
        if (limit <= base + 1.0E-4) {
            clear();
            return;
        }
        activePlayer = player;
        startY = player.getY();
        targetY = startY + limit;
        baselineVelocity = minimum;
        boost = VariableJumpPhysics.holdBoost(minimum, Math.max(original, minimum), gravity);
        boostTick = 0;
        released = false;
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || player != activePlayer || !ClientState.holdToJumpHigher()
                || minecraft.screen != null || !ordinaryJump(player) || player.onGround()
                || player.verticalCollision || player.getY() < startY - 0.1) {
            clear();
            return;
        }
        Vec3 movement = player.getDeltaMovement();
        double remaining = targetY - player.getY();
        if (remaining <= 0.001) {
            clear();
            return;
        }
        // Keep the tap trajectory so releasing Space only removes lift added by holding it.
        baselineVelocity = (baselineVelocity - player.getGravity()) * 0.98;
        double next;
        if (!released && minecraft.options.keyJump.isDown()) {
            next = movement.y + boost * VariableJumpPhysics.boostWeight(boostTick++);
        } else {
            released = true;
            double extra = Math.max(0, movement.y - baselineVelocity);
            if (extra < 0.001) {
                clear();
                return;
            }
            next = baselineVelocity + extra * 0.45;
        }
        player.setDeltaMovement(movement.x, next, movement.z);
    }

    public static void clear() {
        activePlayer = null;
        boost = 0;
        boostTick = 0;
        released = false;
    }

    private static boolean ordinaryJump(LocalPlayer player) {
        return !player.getAbilities().flying && !player.isPassenger() && !player.isFallFlying() && !player.isInWater()
                && !player.isInLava() && !player.hasEffect(MobEffects.LEVITATION)
                && !player.hasEffect(MobEffects.SLOW_FALLING);
    }

    private VariableJump() {}
}
