package dev.nidza.statlog.network;

import dev.nidza.statlog.Statlog;
import dev.nidza.statlog.client.StatlogClient;
import dev.nidza.statlog.config.AttributeDefinition;
import dev.nidza.statlog.config.AttributeDefinitions;
import dev.nidza.statlog.server.StatlogApi;
import dev.nidza.statlog.server.UpgradeTransaction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class StatlogNetwork {
    private static final Map<UUID, UUID> REMOTE_TARGETS = new HashMap<>();
    public record Sync(CompoundTag data) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Statlog.ID, "sync"));
        public static final StreamCodec<FriendlyByteBuf, Sync> CODEC =
                StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, Sync::data, Sync::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Open(String mode, String tab) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Statlog.ID, "open"));
        public static final StreamCodec<FriendlyByteBuf, Open> CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Open::mode,
                        ByteBufCodecs.STRING_UTF8, Open::tab, Open::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record RequestOpen(String mode) implements CustomPacketPayload {
        public static final Type<RequestOpen> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Statlog.ID, "request_open"));
        public static final StreamCodec<FriendlyByteBuf, RequestOpen> CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, RequestOpen::mode, RequestOpen::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Finalize(CompoundTag data) implements CustomPacketPayload {
        public static final Type<Finalize> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Statlog.ID, "finalize"));
        public static final StreamCodec<FriendlyByteBuf, Finalize> CODEC =
                StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, Finalize::data, Finalize::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Result(String error) implements CustomPacketPayload {
        public static final Type<Result> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Statlog.ID, "result"));
        public static final StreamCodec<FriendlyByteBuf, Result> CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Result::error, Result::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record HurtFlash(int entityId, int ticks) implements CustomPacketPayload {
        public static final Type<HurtFlash> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Statlog.ID, "hurt_flash"));
        public static final StreamCodec<FriendlyByteBuf, HurtFlash> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, HurtFlash::entityId, ByteBufCodecs.VAR_INT, HurtFlash::ticks, HurtFlash::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record CloseRemote() implements CustomPacketPayload {
        public static final Type<CloseRemote> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Statlog.ID, "close_remote"));
        public static final StreamCodec<FriendlyByteBuf, CloseRemote> CODEC = StreamCodec.unit(new CloseRemote());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(Sync.TYPE, Sync.CODEC, (payload, context) ->
                context.enqueueWork(() -> StatlogClient.sync(payload.data())));
        registrar.playToClient(Open.TYPE, Open.CODEC, (payload, context) ->
                context.enqueueWork(() -> StatlogClient.open(payload.mode(), payload.tab())));
        registrar.playToClient(Result.TYPE, Result.CODEC, (payload, context) ->
                context.enqueueWork(() -> StatlogClient.result(payload.error())));
        registrar.playToClient(HurtFlash.TYPE, HurtFlash.CODEC, (payload, context) ->
                context.enqueueWork(() -> StatlogClient.hurtFlash(payload.entityId(), payload.ticks())));
        registrar.playToServer(RequestOpen.TYPE, RequestOpen.CODEC, StatlogNetwork::handleOpenRequest);
        registrar.playToServer(Finalize.TYPE, Finalize.CODEC, StatlogNetwork::handleFinalize);
        registrar.playToServer(CloseRemote.TYPE, CloseRemote.CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) closeRemote(player);
        }));
    }

    private static void handleOpenRequest(RequestOpen payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) open(player, "view");
        });
    }
    private static void handleFinalize(Finalize payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer viewer)) return;
            UUID remoteId = REMOTE_TARGETS.get(viewer.getUUID());
            ServerPlayer player = remoteId == null ? viewer : viewer.getServer().getPlayerList().getPlayer(remoteId);
            if (player == null || (remoteId != null && !viewer.hasPermissions(2))) {
                PacketDistributor.sendToPlayer(viewer, new Result("Target player is unavailable"));
                return;
            }
            if (!payload.data().getString("TargetId").equals(player.getUUID().toString())) {
                PacketDistributor.sendToPlayer(viewer, new Result("Statlog target changed; reopen the menu"));
                return;
            }
            Map<String, Integer> levels = new HashMap<>();
            Map<String, List<String>> choices = new HashMap<>();
            CompoundTag requested = payload.data().getCompound("Levels");
            if (requested.getAllKeys().size() > AttributeDefinitions.all().size()) {
                PacketDistributor.sendToPlayer(viewer, new Result("Too many requested attributes"));
                return;
            }
            for (String id : requested.getAllKeys()) levels.put(id, requested.getInt(id));
            CompoundTag selected = payload.data().getCompound("Currencies");
            for (String id : selected.getAllKeys()) {
                List<String> list = new ArrayList<>();
                for (var item : selected.getList(id, net.minecraft.nbt.Tag.TAG_STRING)) list.add(item.getAsString());
                choices.put(id, list);
            }
            String error = UpgradeTransaction.commit(player, levels, choices, remoteId != null);
            PacketDistributor.sendToPlayer(viewer, new Result(error == null ? "" : error));
        });
    }

    public static void sync(ServerPlayer player) {
        UUID remoteId = REMOTE_TARGETS.get(player.getUUID());
        ServerPlayer target = remoteId == null ? null : player.getServer().getPlayerList().getPlayer(remoteId);
        if (target != null && player.hasPermissions(2)) sendSnapshot(player, target, true);
        else {
            REMOTE_TARGETS.remove(player.getUUID());
            sendSnapshot(player, player, false);
        }
        for (var entry : REMOTE_TARGETS.entrySet()) {
            if (!entry.getValue().equals(player.getUUID()) || entry.getKey().equals(player.getUUID())) continue;
            ServerPlayer viewer = player.getServer().getPlayerList().getPlayer(entry.getKey());
            if (viewer != null && viewer.hasPermissions(2)) sendSnapshot(viewer, player, true);
        }
    }
    private static void sendSnapshot(ServerPlayer viewer, ServerPlayer target, boolean remote) {
        CompoundTag snapshot = new CompoundTag();
        snapshot.put("Progress", StatlogApi.data(target).serializeNBT(target.registryAccess()));
        snapshot.putBoolean("CanEdit", remote || StatlogApi.canEdit(target));
        snapshot.putBoolean("CanRespec", remote || StatlogApi.canRespec(target));
        snapshot.putBoolean("Remote", remote);
        snapshot.putString("TargetId", target.getUUID().toString());
        snapshot.putString("TargetName", target.getGameProfile().getName());
        snapshot.putString("Title", dev.nidza.statlog.config.StatlogConfig.TITLE.get());
        snapshot.putString("CurrencyMode", dev.nidza.statlog.config.StatlogConfig.currencyMode());
        snapshot.putBoolean("LinearVanillaXp", dev.nidza.statlog.config.StatlogConfig.LINEAR_VANILLA_XP.get());
        snapshot.putBoolean("HoldToJumpHigher", dev.nidza.statlog.config.StatlogConfig.HOLD_TO_JUMP_HIGHER.get());
        snapshot.putDouble("MinimumJumpStrength", dev.nidza.statlog.config.StatlogConfig.MINIMUM_JUMP_STRENGTH.get());
        snapshot.putInt("VanillaXpPerLevel", dev.nidza.statlog.config.StatlogConfig.VANILLA_XP_PER_LEVEL.get());
        snapshot.putInt("XpCostBase", dev.nidza.statlog.config.StatlogConfig.XP_COST_BASE.get());
        snapshot.putInt("XpCostIncrement", dev.nidza.statlog.config.StatlogConfig.XP_COST_INCREMENT.get());
        snapshot.putInt("NextLevelXp", StatlogApi.xpToNextLevel(StatlogApi.level(target)));
        snapshot.putInt("VanillaLevel", target.experienceLevel);
        snapshot.putFloat("VanillaProgress", target.experienceProgress);
        snapshot.putInt("VanillaExperience", target.totalExperience);
        ListTag definitions = new ListTag();
        CompoundTag attributeBases = new CompoundTag();
        for (AttributeDefinition def : AttributeDefinitions.all().values()) {
            if (!AttributeDefinitions.registered(def.attribute())) continue;
            CompoundTag entry = new CompoundTag();
            entry.putString("Id", def.attribute());
            entry.putString("Name", def.name());
            entry.putString("Description", def.description());
            entry.putString("Icon", def.icon());
            entry.putBoolean("MaxIcon", def.maxIcon());
            entry.putBoolean("MaxIconSepia", def.maxIconSepia());
            entry.putInt("Min", def.minLevel());
            entry.putInt("Max", def.maxLevel());
            entry.putInt("Start", def.startingLevel());
            entry.putDouble("Increment", def.incrementValue());
            entry.putBoolean("Percent", def.percent());
            entry.putDouble("PercentageBase", def.percentageBase());
            entry.putString("CurrentText", def.currentText());
            entry.putString("IncrementText", def.incrementText());
            CompoundTag primaryValues = new CompoundTag();
            def.levelValues().forEach((level, value) -> primaryValues.putDouble(Integer.toString(level), value));
            entry.put("LevelValues", primaryValues);
            ListTag extra = new ListTag();
            for (var effect : def.extraAttributes()) {
                CompoundTag row = new CompoundTag();
                row.putString("Id", effect.attribute());
                row.putDouble("Increment", effect.incrementValue());
                row.putBoolean("Percent", effect.percent());
                row.putDouble("PercentageBase", effect.percentageBase());
                CompoundTag values = new CompoundTag();
                effect.levelValues().forEach((level, value) -> values.putDouble(Integer.toString(level), value));
                row.put("LevelValues", values);
                extra.add(row);
            }
            entry.put("ExtraAttributes", extra);
            for (var effect : def.effects()) {
                var holder = BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(effect.attribute())).orElse(null);
                var instance = holder == null ? null : target.getAttribute(holder);
                if (instance != null) attributeBases.putDouble(effect.attribute(), instance.getBaseValue());
            }
            entry.putInt("Cost", def.cost());
            ListTag currencies = new ListTag();
            def.acceptedPoints().forEach(s -> currencies.add(net.minecraft.nbt.StringTag.valueOf(s)));
            entry.put("Currencies", currencies);
            ListTag tabs = new ListTag();
            def.tabs().forEach(s -> tabs.add(net.minecraft.nbt.StringTag.valueOf(s)));
            entry.put("Tabs", tabs);
            definitions.add(entry);
        }
        snapshot.put("Definitions", definitions);
        snapshot.put("AttributeBases", attributeBases);
        ListTag tabs = new ListTag();
        for (var tab : AttributeDefinitions.tabs().values()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Id", tab.id());
            entry.putString("Name", tab.name());
            entry.putString("Item", tab.item());
            entry.putString("Texture", tab.texture());
            entry.putBoolean("Visible", tab.visible());
            tabs.add(entry);
        }
        snapshot.put("Tabs", tabs);
        ListTag points = new ListTag();
        AttributeDefinitions.customPoints().forEach(s -> points.add(net.minecraft.nbt.StringTag.valueOf(s)));
        snapshot.put("CustomPoints", points);
        CompoundTag pointNames = new CompoundTag();
        CompoundTag pointColors = new CompoundTag();
        CompoundTag pointIcons = new CompoundTag();
        CompoundTag pointOrbTextures = new CompoundTag();
        CompoundTag pointOrbColors = new CompoundTag();
        AttributeDefinitions.currencies().forEach((id, def) -> {
            pointNames.putString(id, def.name());
            pointColors.putString(id, def.color());
            pointIcons.putString(id, def.icon());
            pointOrbTextures.putString(id, def.orbTexture());
            pointOrbColors.putString(id, def.orbColor());
        });
        snapshot.put("PointNames", pointNames);
        snapshot.put("PointColors", pointColors);
        snapshot.put("PointIcons", pointIcons);
        snapshot.put("PointOrbTextures", pointOrbTextures);
        snapshot.put("PointOrbColors", pointOrbColors);
        PacketDistributor.sendToPlayer(viewer, new Sync(snapshot));
    }
    public static void open(ServerPlayer player, String mode) {
        open(player, mode, "");
    }
    public static void open(ServerPlayer player, String mode, String tab) {
        REMOTE_TARGETS.remove(player.getUUID());
        sync(player);
        String actual = mode.equalsIgnoreCase("view_only") ? "view_only"
                : StatlogApi.canEdit(player) ? "edit" : "view";
        PacketDistributor.sendToPlayer(player, new Open(actual, tab));
    }
    public static void openRemote(ServerPlayer viewer, ServerPlayer target, String mode) {
        openRemote(viewer, target, mode, "");
    }
    public static void openRemote(ServerPlayer viewer, ServerPlayer target, String mode, String tab) {
        if (!viewer.hasPermissions(2)) return;
        REMOTE_TARGETS.put(viewer.getUUID(), target.getUUID());
        sendSnapshot(viewer, target, true);
        PacketDistributor.sendToPlayer(viewer, new Open(mode.equalsIgnoreCase("view") ? "view_only" : "edit", tab));
    }
    public static void closeRemote(ServerPlayer viewer) {
        if (REMOTE_TARGETS.remove(viewer.getUUID()) != null) sync(viewer);
    }
    public static void forget(ServerPlayer player) {
        REMOTE_TARGETS.remove(player.getUUID());
        List<UUID> viewers = new ArrayList<>();
        for (var entry : REMOTE_TARGETS.entrySet())
            if (entry.getValue().equals(player.getUUID())) viewers.add(entry.getKey());
        for (UUID viewerId : viewers) {
            REMOTE_TARGETS.remove(viewerId);
            ServerPlayer viewer = player.getServer().getPlayerList().getPlayer(viewerId);
            if (viewer != null) {
                sync(viewer);
                PacketDistributor.sendToPlayer(viewer, new Open("view_only", ""));
            }
        }
    }
    private StatlogNetwork() {}
}
