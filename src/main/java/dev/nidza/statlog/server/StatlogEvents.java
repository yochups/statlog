package dev.nidza.statlog.server;

import dev.nidza.statlog.Statlog;
import dev.nidza.statlog.config.StatlogConfig;
import dev.nidza.statlog.network.StatlogNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class StatlogEvents {
    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AttributeManager.apply(player);
            StatlogNetwork.sync(player);
        }
    }
    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ItemRules.clear(player);
            StatlogNetwork.forget(player);
        }
    }
    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        if (event.isWasDeath() && StatlogConfig.KEEP_PROGRESS_ON_DEATH.get()
                && event.getOriginal().hasData(Statlog.PROGRESS)) {
            event.getEntity().getData(Statlog.PROGRESS)
                    .deserializeNBT(event.getEntity().registryAccess(),
                            event.getOriginal().getData(Statlog.PROGRESS).serializeNBT(event.getOriginal().registryAccess()));
        }
    }
    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AttributeManager.apply(player);
            StatlogNetwork.sync(player);
        }
    }
    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AttributeManager.apply(player);
            StatlogNetwork.sync(player);
        }
    }
    @SubscribeEvent
    public static void useItem(PlayerInteractEvent.RightClickItem event) {
        if (blocked(event.getEntity(), event.getItemStack())) event.setCanceled(true);
    }
    @SubscribeEvent
    public static void useBlock(PlayerInteractEvent.RightClickBlock event) {
        if (blocked(event.getEntity(), event.getItemStack())) event.setCanceled(true);
    }
    @SubscribeEvent
    public static void useEntity(PlayerInteractEvent.EntityInteract event) {
        if (blocked(event.getEntity(), event.getItemStack())) event.setCanceled(true);
    }
    @SubscribeEvent
    public static void useEntitySpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (blocked(event.getEntity(), event.getItemStack())) event.setCanceled(true);
    }
    @SubscribeEvent
    public static void mine(PlayerInteractEvent.LeftClickBlock event) {
        if (blocked(event.getEntity(), event.getItemStack())) event.setCanceled(true);
    }
    @SubscribeEvent
    public static void attack(AttackEntityEvent event) {
        if (blocked(event.getEntity(), event.getEntity().getMainHandItem())) event.setCanceled(true);
    }
    @SubscribeEvent
    public static void equipmentDebuff(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        ItemRules.applyDebuffs(player);
    }
    @SubscribeEvent
    public static void itemDamage(LivingDamageEvent.Pre event) {
        if (event.getSource().is(DamageTypes.PLAYER_ATTACK)
                && event.getSource().getEntity() instanceof ServerPlayer player
                && event.getSource().getDirectEntity() == player)
            event.setNewDamage(event.getNewDamage() * ItemRules.itemMultiplier(player,
                    player.getMainHandItem(), ItemRules.Kind.ITEM_DAMAGE));
    }
    @SubscribeEvent
    public static void itemMiningSpeed(PlayerEvent.BreakSpeed event) {
        if (event.getEntity() instanceof ServerPlayer player)
            event.setNewSpeed(event.getNewSpeed() * ItemRules.itemMultiplier(player,
                    player.getMainHandItem(), ItemRules.Kind.ITEM_MINING_SPEED));
    }
    private static boolean blocked(net.minecraft.world.entity.player.Player player, ItemStack stack) {
        return player instanceof ServerPlayer server && !stack.isEmpty() && ItemRules.unusable(server, stack);
    }
    private StatlogEvents() {}
}
