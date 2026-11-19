package dev.nidza.statlog.server;

import dev.nidza.statlog.Statlog;
import dev.nidza.statlog.config.StatlogConfig;
import dev.nidza.statlog.network.StatlogNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.IdentityHashMap;
import java.util.Map;

public final class StatlogEvents {
    private static final Map<LivingEntity, Integer> PENDING_FLASH = new IdentityHashMap<>();
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
        if (blocked(event.getEntity(), event.getItemStack())
                || restricted(event.getEntity(), event.getItemStack(), null, "USE_ITEM")
                || (event.getItemStack().has(DataComponents.FOOD)
                    && restricted(event.getEntity(), event.getItemStack(), null, "EAT"))) event.setCanceled(true);
    }
    @SubscribeEvent
    public static void useBlock(PlayerInteractEvent.RightClickBlock event) {
        if (blocked(event.getEntity(), event.getItemStack())
                || restricted(event.getEntity(), event.getItemStack(), event.getLevel().getBlockState(event.getPos()), "USE_BLOCK"))
            event.setCanceled(true);
    }
    @SubscribeEvent
    public static void useEntity(PlayerInteractEvent.EntityInteract event) {
        if (blocked(event.getEntity(), event.getItemStack())
                || restricted(event.getEntity(), event.getItemStack(), null, "INTERACT_ENTITY")) event.setCanceled(true);
    }
    @SubscribeEvent
    public static void useEntitySpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (blocked(event.getEntity(), event.getItemStack())
                || restricted(event.getEntity(), event.getItemStack(), null, "INTERACT_ENTITY")) event.setCanceled(true);
    }
    @SubscribeEvent
    public static void mine(PlayerInteractEvent.LeftClickBlock event) {
        if (blocked(event.getEntity(), event.getItemStack())
                || restricted(event.getEntity(), event.getItemStack(), event.getLevel().getBlockState(event.getPos()), "BREAK_BLOCK"))
            event.setCanceled(true);
    }
    @SubscribeEvent
    public static void attack(AttackEntityEvent event) {
        if (blocked(event.getEntity(), event.getEntity().getMainHandItem())
                || restricted(event.getEntity(), event.getEntity().getMainHandItem(), null, "ATTACK_ENTITY"))
            event.setCanceled(true);
    }
    @SubscribeEvent
    public static void place(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && (ItemRules.restricted(player, player.getMainHandItem(), event.getPlacedBlock(), "PLACE_BLOCK")
                    || ItemRules.restricted(player, player.getOffhandItem(), event.getPlacedBlock(), "PLACE_BLOCK")))
            event.setCanceled(true);
    }
    @SubscribeEvent
    public static void breakBlock(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player
                && ItemRules.restricted(player, player.getMainHandItem(), event.getState(), "BREAK_BLOCK"))
            event.setCanceled(true);
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
    public static void targetInvulnerability(LivingDamageEvent.Post event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker
                && event.getSource().getDirectEntity() == attacker
                && attacker.getAttribute(Statlog.TARGET_INVULNERABILITY_TICKS) != null) {
            int ticks = Math.clamp((int) Math.round(attacker.getAttributeValue(Statlog.TARGET_INVULNERABILITY_TICKS)), 1, 20);
            if (ticks != 10) {
                event.getEntity().invulnerableTime = 10 + ticks;
                event.getEntity().hurtDuration = ticks;
                event.getEntity().hurtTime = ticks;
                if (!event.getEntity().level().isClientSide)
                    PENDING_FLASH.put(event.getEntity(), ticks);
            }
        }
    }
    @SubscribeEvent
    public static void syncHurtFlash(ServerTickEvent.Post event) {
        PENDING_FLASH.forEach((entity, ticks) -> {
            if (entity.isRemoved() || !(entity.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
            StatlogNetwork.HurtFlash packet = new StatlogNetwork.HurtFlash(entity.getId(), ticks);
            PacketDistributor.sendToPlayersTrackingChunk(level, entity.chunkPosition(), packet);
            if (entity instanceof ServerPlayer player) PacketDistributor.sendToPlayer(player, packet);
        });
        PENDING_FLASH.clear();
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
    private static boolean restricted(net.minecraft.world.entity.player.Player player, ItemStack stack,
                                      net.minecraft.world.level.block.state.BlockState block, String action) {
        return player instanceof ServerPlayer server && ItemRules.restricted(server, stack, block, action);
    }
    private StatlogEvents() {}
}
