package dev.nidza.statlog.mixin;

import dev.nidza.statlog.VanillaXpCurve;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerXpMixin {
    @Inject(method = "getXpNeededForNextLevel", at = @At("HEAD"), cancellable = true)
    private void statlog$xpForNextLevel(CallbackInfoReturnable<Integer> result) {
        int cost = VanillaXpCurve.cost((Player) (Object) this);
        if (cost > 0) result.setReturnValue(cost);
    }
}
