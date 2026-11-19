package dev.nidza.statlog.server;

import dev.nidza.statlog.Statlog;
import dev.nidza.statlog.config.AttributeDefinitions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class CurrencyOrb extends ExperienceOrb {
    private static final EntityDataAccessor<String> CURRENCY =
            SynchedEntityData.defineId(CurrencyOrb.class, EntityDataSerializers.STRING);
    private int amount;

    public CurrencyOrb(EntityType<? extends ExperienceOrb> type, Level level) {
        super(type, level);
    }

    public CurrencyOrb(Level level, double x, double y, double z, String currency, int amount) {
        this(Statlog.CURRENCY_ORB.get(), level);
        setPos(x, y, z);
        entityData.set(CURRENCY, currency);
        this.amount = amount;
        // Vanilla orbs merge by value; a unique negative value keeps currency payloads separate.
        value = -Math.max(1, getId());
    }

    @Override public void playerTouch(Player player) {
        if (!(player instanceof ServerPlayer server) || player.takeXpDelay != 0
                || !AttributeDefinitions.customPoints().contains(currency()) || amount <= 0) return;
        try {
            StatlogApi.addPoints(server, currency(), amount);
        } catch (IllegalArgumentException | ArithmeticException ignored) {
            return;
        }
        player.take(this, 1);
        player.takeXpDelay = 2;
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 0.1f, 1f);
        discard();
    }

    public String currency() { return entityData.get(CURRENCY); }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CURRENCY, "");
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Currency", currency());
        tag.putInt("CurrencyValue", amount);
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(CURRENCY, tag.getString("Currency"));
        amount = tag.getInt("CurrencyValue");
        value = -Math.max(1, getId());
    }
}
