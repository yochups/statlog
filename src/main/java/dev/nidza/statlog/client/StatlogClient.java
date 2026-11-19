package dev.nidza.statlog.client;

import dev.nidza.statlog.Statlog;
import dev.nidza.statlog.VanillaXpCurve;
import dev.nidza.statlog.config.StatlogConfig;
import dev.nidza.statlog.network.StatlogNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = Statlog.ID, dist = Dist.CLIENT)
public final class StatlogClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatlogClient.class);
    private static final ResourceLocation TAB = ResourceLocation.fromNamespaceAndPath(Statlog.ID, "textures/gui/statlog_button.png");
    private static final ResourceLocation UPGRADE_BADGE = ResourceLocation.fromNamespaceAndPath(Statlog.ID, "textures/gui/statlog_upgrade_badge.png");
    private static final KeyMapping OPEN_KEY = new KeyMapping("key.statlog.open",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_CAPS_LOCK, "key.categories.statlog");
    public StatlogClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(StatlogClient::setup);
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(OPEN_KEY));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
                event.registerEntityRenderer(Statlog.CURRENCY_ORB.get(), CurrencyOrbRenderer::new));
        modBus.addListener((RegisterClientReloadListenersEvent event) ->
                event.registerReloadListener((ResourceManagerReloadListener) StatlogTextColors::reload));
        modBus.addListener((RegisterClientReloadListenersEvent event) ->
                event.registerReloadListener((ResourceManagerReloadListener) ignored -> GeneratedIcons.resourceReload()));
        modBus.addListener((RegisterClientReloadListenersEvent event) ->
                event.registerReloadListener((ResourceManagerReloadListener) ignored -> StatlogLanguage.reload()));
        NeoForge.EVENT_BUS.addListener(StatlogClient::inventoryInit);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            while (OPEN_KEY.consumeClick()) {
                if (Minecraft.getInstance().player != null && Minecraft.getInstance().screen == null)
                    PacketDistributor.sendToServer(new StatlogNetwork.RequestOpen("view"));
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> VanillaXpCurve.clearClient());
    }
    private static void setup(FMLClientSetupEvent event) {
        if (!ModList.get().isLoaded("fancymenu")) return;
        event.enqueueWork(() -> {
            try {
                Class<?> identifiers = Class.forName(
                        "de.keksuccino.fancymenu.customization.screen.identifier.UniversalScreenIdentifierRegistry");
                identifiers.getMethod("register", String.class, String.class)
                        .invoke(null, "statlog_screen", StatlogScreen.class.getName());
            } catch (ReflectiveOperationException | LinkageError error) {
                LOGGER.warn("FancyMenu could not register the Statlog screen", error);
            }
        });
    }
    private static void inventoryInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen inventory) || !StatlogConfig.SHOW_TAB.get()) return;
        int left = inventory.getGuiLeft();
        int top = inventory.getGuiTop();
        int x = left + 2 + (ModList.get().isLoaded("questlog") ? 30 : 0);
        event.addListener(new AbstractWidget(x, top - 25, 28, 26, StatlogLanguage.key("statlog.gui.open")) {
            @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                setX(inventory.getGuiLeft() + 2 + (ModList.get().isLoaded("questlog") ? 30 : 0));
                setY(inventory.getGuiTop() - 25);
                graphics.blit(TAB, getX() - 18, getY() - 19, 64, 64, 96, 96, 64, 64, 256, 256);
                if (ClientState.hasAffordableUpgrade(Minecraft.getInstance().player)) {
                    float time = Minecraft.getInstance().level == null ? partialTick
                            : Minecraft.getInstance().level.getGameTime() + partialTick;
                    int bob = StatlogConfig.ANIMATE_UPGRADE_BADGE.get()
                            ? Math.round((float) Math.sin(time * 0.2f) * 1.5f) : 0;
                    graphics.blit(UPGRADE_BADGE, getX() + 6, getY() - 19 + bob,
                            16, 16, 0, 0, 16, 16, 16, 16);
                }
                if (mouseX >= getX() && mouseX < getX() + getWidth()
                        && mouseY >= getY() && mouseY < getY() + getHeight())
                    graphics.renderTooltip(Minecraft.getInstance().font,
                            StatlogTextColors.styled(getMessage(), "inventory_tab_tooltip", 0xFFFFFFFF), mouseX, mouseY);
            }
            @Override public void onClick(double mouseX, double mouseY) {
                PacketDistributor.sendToServer(new StatlogNetwork.RequestOpen("view"));
            }
            @Override protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
                defaultButtonNarrationText(output);
            }
        });
    }
    public static void sync(CompoundTag data) {
        ClientState.update(data);
        StatlogLanguage.reload();
        GeneratedIcons.reload();
        VanillaXpCurve.setClient(data.getBoolean("LinearVanillaXp"), data.getInt("VanillaXpPerLevel"));
        if (Minecraft.getInstance().player != null) {
            var player = Minecraft.getInstance().player;
            if (!data.getBoolean("Remote")) {
                player.getData(Statlog.PROGRESS).deserializeNBT(player.registryAccess(), data.getCompound("Progress"));
                if (data.contains("VanillaLevel")) {
                    player.experienceLevel = data.getInt("VanillaLevel");
                    player.experienceProgress = data.getFloat("VanillaProgress");
                    player.totalExperience = data.getInt("VanillaExperience");
                }
            }
        }
        if (Minecraft.getInstance().screen instanceof StatlogScreen screen) screen.refresh();
    }
    public static void open(String mode, String tab) { Minecraft.getInstance().setScreen(new StatlogScreen(mode, tab)); }
    public static void result(String error) {
        if (Minecraft.getInstance().screen instanceof StatlogScreen screen) screen.result(error);
    }
    public static void hurtFlash(int entityId, int ticks) {
        if (Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getEntity(entityId) instanceof LivingEntity entity) {
            entity.hurtDuration = ticks;
            entity.hurtTime = ticks;
        }
    }
}
