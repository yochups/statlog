package dev.nidza.statlog.client;

import dev.nidza.statlog.Statlog;
import dev.nidza.statlog.config.AttributeDefinition;
import dev.nidza.statlog.config.AttributeTab;
import dev.nidza.statlog.config.StatlogConfig;
import dev.nidza.statlog.network.StatlogNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class StatlogScreen extends Screen {
    private static final ResourceLocation ENTRY = texture("statlog_tooltip_ui_light.png");
    private static final ResourceLocation TOOLTIP = texture("statlog_tooltip_ui_dark.png");
    private static final ResourceLocation SCROLL_THUMB = texture("statlog_scroll_thumb.png");
    private static final ResourceLocation SCROLL_TRACK = texture("statlog_scroll_track.png");
    private static final ResourceLocation CURRENCY_ICON = texture("statlog_currency.png");
    private static final ResourceLocation CURRENCY_MAX_ICON = texture("statlog_currency_max.png");
    private static final ResourceLocation WHITE = texture("white_pixel.png");
    private static final ResourceLocation EDIT_TAB = texture("statlog_edit_tab.png");
    private static final ResourceLocation XP_BG = ResourceLocation.withDefaultNamespace("hud/experience_bar_background");
    private static final ResourceLocation XP_FILL = ResourceLocation.withDefaultNamespace("hud/experience_bar_progress");
    private static String lastTab;
    private final Map<String, Integer> pending = new HashMap<>();
    private final Map<String, List<String>> choices = new HashMap<>();
    private final Map<String, Integer> selected = new HashMap<>();
    private boolean editing;
    private final boolean viewOnly;
    private String error = "";
    private double scroll, targetScroll;
    private boolean dragging;
    private String heldAttribute;
    private int heldDirection;
    private long nextRepeat;
    private final boolean requestOpen;
    private int x, y, panelWidth, panelHeight, leftWidth, listX, listY, listWidth, listHeight, rowHeight;
    private Button done, finalize;
    private AbstractWidget edit;
    private final List<AbstractWidget> tabButtons = new ArrayList<>();
    private String activeTab;
    private boolean tabInitialized;

    public StatlogScreen() {
        this("view", true);
    }
    public StatlogScreen(String mode) {
        this(mode, false);
    }
    private StatlogScreen(String mode, boolean requestOpen) {
        super(StatlogLanguage.key("statlog.gui.title"));
        editing = mode.equalsIgnoreCase("edit");
        viewOnly = mode.equalsIgnoreCase("view_only");
        this.requestOpen = requestOpen;
    }
    @Override protected void init() {
        if (requestOpen && minecraft != null && minecraft.player != null) {
            PacketDistributor.sendToServer(new StatlogNetwork.RequestOpen("view"));
        }
        panelWidth = Math.min(450, width - 18);
        panelHeight = Math.min(280, height - 18);
        x = (width - panelWidth) / 2;
        y = (height - panelHeight) / 2;
        listWidth = Math.min(190, Math.max(170, panelWidth * 42 / 100));
        rowHeight = StatlogConfig.ATTRIBUTE_ROW_HEIGHT.get();
        leftWidth = panelWidth - listWidth - 26;
        listX = x + leftWidth + 12;
        // Align the panel and its tabs as one group with the player column.
        listY = y + 46;
        listHeight = panelHeight - 86;
        done = addRenderableWidget(Button.builder(StatlogTextColors.styled(StatlogLanguage.key("statlog.gui.done"),
                        "button_done", 0xFFFFFFFF), b -> onClose())
                .bounds(x + 8, y + panelHeight - 27, leftWidth - 16, 20).build());
        edit = addRenderableWidget(new AbstractWidget(tabX(5), tabY(false, editing), 28, 18,
                StatlogLanguage.key("statlog.gui.edit")) {
            @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float tick) {
                g.blit(EDIT_TAB, getX() - 15, getY() - 19, 58, 55,
                        isHovered() || editing ? 58 : 0, 0, 58, 55, 116, 55);
                g.flush();
                renderTabItem(g, (editing ? Items.BARRIER : Items.WRITABLE_BOOK).getDefaultInstance(),
                        getX() + 6, getY() + 1, false);
            }
            @Override public void onClick(double mouseX, double mouseY) {
                if (editing) {
                    pending.clear();
                    choices.clear();
                    selected.clear();
                    editing = false;
                } else {
                    editing = true;
                }
                error = "";
                refresh();
            }
            @Override protected void updateWidgetNarration(NarrationElementOutput output) {
                defaultButtonNarrationText(output);
            }
        });
        finalize = addRenderableWidget(Button.builder(StatlogTextColors.styled(StatlogLanguage.key("statlog.gui.finalize"),
                        "button_finalize", 0xFFFFFFFF), b -> submit())
                .bounds(x + (leftWidth - listWidth) / 2, y + panelHeight - 27, listWidth, 20).build());
        refresh();
    }
    public void refresh() {
        rebuildTabs();
        if (editing && !ClientState.canEdit()) {
            editing = false;
            pending.clear();
            choices.clear();
        }
        if (done != null) done.visible = !editing;
        if (edit != null) edit.visible = StatlogConfig.SHOW_EDIT_TAB.get() && !viewOnly && ClientState.canEdit();
        if (edit != null) edit.setMessage(StatlogLanguage.key(editing ? "statlog.gui.cancel_edit" : "statlog.gui.edit"));
        if (edit != null) edit.setY(tabY(false, editing));
        if (finalize != null) finalize.visible = editing;
    }
    private void rebuildTabs() {
        tabButtons.forEach(this::removeWidget);
        tabButtons.clear();
        List<AttributeTab> tabs = ClientState.tabs();
        if (!tabs.isEmpty() && !tabInitialized) {
            activeTab = tabs.stream().anyMatch(tab -> tab.id().equals(lastTab)) ? lastTab : tabs.getFirst().id();
            tabInitialized = true;
        } else if (activeTab != null && tabs.stream().noneMatch(tab -> tab.id().equals(activeTab))) {
            activeTab = tabs.isEmpty() ? null : tabs.getFirst().id();
        }
        if (tabs.size() == 1 && StatlogConfig.HIDE_SINGLE_TAB.get()) return;
        for (int i = 0; i < tabs.size() && i < 11; i++) {
            AttributeTab tab = tabs.get(i);
            boolean bottom = i < 6;
            int slot = bottom ? 5 - i : i - 6;
            int tabX = tabX(slot);
            AbstractWidget widget = new AbstractWidget(tabX, tabY(bottom, tab.id().equals(activeTab)),
                    28, 18, StatlogLanguage.value(tab.name())) {
                @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float tick) {
                    ResourceLocation texture = GeneratedIcons.texture(tab.texture());
                    if (texture != null) g.blit(texture, getX() - 15, getY() - (bottom ? 18 : 19), 58, 55,
                            isHovered() || tab.id().equals(activeTab) ? 58 : 0, 0, 58, 55, 116, 55);
                    g.flush();
                    renderTabItem(g, icon(tab.item()), getX() + 6, getY() + (bottom ? 0 : 1), bottom);
                }
                @Override public void onClick(double mouseX, double mouseY) {
                    activeTab = tab.id();
                    lastTab = activeTab;
                    scroll = targetScroll = 0;
                    positionTabs();
                }
                @Override protected void updateWidgetNarration(NarrationElementOutput output) {
                    defaultButtonNarrationText(output);
                }
            };
            tabButtons.add(addRenderableWidget(widget));
        }
    }
    private int tabX(int slot) { return listX + Math.round(slot * (listWidth - 44) / 5f); }
    private int tabY(boolean bottom, boolean selected) {
        return bottom ? listY + listHeight + (selected ? 11 : 6)
                : listY - (selected ? 28 : 23);
    }
    private void renderTabItem(GuiGraphics g, ItemStack item, int itemX, int itemY, boolean bottom) {
        // Items use their own GUI depth; clip the four recessed pixels at the panel edge.
        g.enableScissor(0, bottom ? listY + listHeight + 10 : 0,
                width, bottom ? height : listY - 10);
        g.renderItem(item, itemX, itemY);
        g.flush();
        g.disableScissor();
    }
    private void positionTabs() {
        List<AttributeTab> tabs = ClientState.tabs();
        for (int i = 0; i < tabButtons.size(); i++)
            tabButtons.get(i).setY(tabY(i < 6, tabs.get(i).id().equals(activeTab)));
    }
    public void result(String message) {
        error = message;
        if (message.isEmpty()) {
            if (pending.entrySet().stream().anyMatch(entry -> {
                AttributeDefinition def = ClientState.definitions().get(entry.getKey());
                return def != null && entry.getValue() != ClientState.level(def);
            })) play(StatlogConfig.FINALIZE_SOUND.get());
            pending.clear();
            choices.clear();
            selected.clear();
            switch (StatlogConfig.AFTER_FINALIZE.get().toUpperCase(java.util.Locale.ROOT)) {
                case "CLOSE" -> { onClose(); return; }
                case "VIEW" -> editing = false;
                default -> editing = ClientState.canEdit();
            }
            refresh();
        }
    }
    @Override public void onClose() {
        pending.clear();
        choices.clear();
        if (ClientState.remote()) PacketDistributor.sendToServer(new StatlogNetwork.CloseRemote());
        super.onClose();
    }
    private void play(String id) {
        if (minecraft == null || id.isBlank()) return;
        ResourceLocation key = ResourceLocation.tryParse(id.trim());
        if (key != null && BuiltInRegistries.SOUND_EVENT.containsKey(key))
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(BuiltInRegistries.SOUND_EVENT.get(key), 1f));
    }
    @Override public void render(GuiGraphics g, int mx, int my, float tick) {
        repeatArrow(mx, my);
        // Screen.render draws the menu blur, so it must run before our sharp GUI layers.
        super.render(g, mx, my, tick);
        // Commit tab items before the list panel covers the recessed parts of the tabs.
        g.flush();
        if (ClientState.hasAffordableUpgrade(minecraft == null ? null : minecraft.player)) {
            String title = ClientState.title();
            Component heading = title.equals("Attributes") || title.equals("statlog.gui.attributes")
                    ? StatlogLanguage.key("statlog.gui.level_up") : StatlogLanguage.value(title);
            float pulse = 1.8f - Math.abs(Mth.sin((Util.getMillis() % 3000L) / 3000f * Mth.TWO_PI) * 0.1f);
            drawPulsingCentered(g, heading, x + leftWidth / 2, y + 41, 2f * pulse / 1.8f, leftWidth - 8,
                    StatlogTextColors.get("heading", 0xFFFFFFFF));
        }
        renderPlayer(g, mx, my);
        renderProgress(g);
        renderCurrencies(g, mx, my);
        renderEntries(g);
        if (!error.isEmpty()) g.drawCenteredString(font, font.plainSubstrByWidth(error, panelWidth - 20),
                x + panelWidth / 2, y + panelHeight - 39, StatlogTextColors.get("error", 0xFFFF7777));
        if (edit != null && edit.visible && edit.isHovered()) {
            g.flush();
            g.renderTooltip(font, StatlogTextColors.styled(edit.getMessage(), "edit_tooltip", 0xFFFFFFFF), mx, my);
        }
        for (AbstractWidget tab : tabButtons) if (tab.isHovered()) {
            g.flush();
            g.renderTooltip(font, tab.getMessage(), mx, my);
            break;
        }
        g.flush();
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        renderTooltip(g, mx, my);
        g.flush();
        g.pose().popPose();
    }
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float tick) {
        super.renderBackground(g, mx, my, tick);
        g.fill(0, 0, width, height, 0xCC000000);
    }
    private void renderPlayer(GuiGraphics g, int mx, int my) {
        if (minecraft == null || minecraft.player == null) return;
        int center = x + leftWidth / 2;
        int previewTop = y + 40;
        int previewBottom = y + panelHeight - 86 - leftLift();
        int area = Math.max(52, previewBottom - previewTop);
        net.minecraft.world.entity.LivingEntity shown = ClientState.remote() ? null : minecraft.player;
        if (ClientState.remote() && minecraft.level != null && ClientState.targetId() != null)
            for (var other : minecraft.level.players())
                if (other.getUUID().equals(ClientState.targetId())) { shown = other; break; }
        if (shown != null) InventoryScreen.renderEntityInInventoryFollowsMouse(g, center - 60, previewTop,
                center + 60, previewTop + area, Math.min(65, area / 2), 0.0625f, mx, my, shown);
        Component name = ClientState.remote() ? Component.literal(ClientState.targetName()) : minecraft.player.getName();
        drawScaledCentered(g, name, center, y + panelHeight - 88 - leftLift(),
                1.5f, leftWidth - 8, StatlogTextColors.get("player_name", 0xFFFFFFFF));
    }
    private void renderProgress(GuiGraphics g) {
        if (!StatlogConfig.SHOW_XP_BAR.get() || minecraft == null || minecraft.player == null) return;
        int level = ClientState.currencyMode().equalsIgnoreCase("XP_LEVELS") ? previewBalance("XP")
                : ClientState.remote() ? ClientState.vanillaLevel() : minecraft.player.experienceLevel;
        float fraction = ClientState.remote() ? ClientState.vanillaProgress() : minecraft.player.experienceProgress;
        if (editing && ClientState.currencyMode().equalsIgnoreCase("XP")) {
            XpPreview preview = previewVanillaXp(previewBalance("XP"));
            level = preview.level();
            fraction = preview.fraction();
        }
        float barScale = Math.min(StatlogConfig.XP_BAR_SCALE.get().floatValue(), (leftWidth - 12) / 182f);
        int barWidth = Math.max(1, Math.round(182 * barScale));
        int barX = x + (leftWidth - barWidth) / 2 + StatlogConfig.XP_BAR_X_OFFSET.get();
        int barY = y + panelHeight - 40 + StatlogConfig.XP_BAR_Y_OFFSET.get();
        g.pose().pushPose();
        g.pose().translate(barX, barY, 0);
        g.pose().scale(barScale, barScale, 1f);
        g.blitSprite(XP_BG, 0, 0, 182, 5);
        int color = color();
        g.setColor(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f, 1f);
        if (fraction > 0) g.blitSprite(XP_FILL, 182, 5, 0, 0, 0, 0,
                Math.min(182, Math.max(1, Math.round(182 * fraction))), 5);
        g.setColor(1, 1, 1, 1);
        g.pose().popPose();
        drawScaledCentered(g, Component.literal(Integer.toString(level)), x + leftWidth / 2, barY - 12,
                1f, leftWidth - 8, StatlogTextColors.get("xp_level", color));
    }
    private int leftLift() {
        int rows = (ClientState.customPoints().size() + 5) / 5;
        return (StatlogConfig.SHOW_XP_BAR.get() && rows > 0 ? 26 : 0) + Math.max(0, rows - 2) * 13;
    }
    private record XpPreview(int level, float fraction) {}
    private XpPreview previewVanillaXp(int points) {
        int low = 0;
        int high = Math.max(0, points);
        while (low < high) {
            int middle = low + (int) (((long) high - low + 1) / 2);
            if (totalXpForLevel(middle) <= points) low = middle;
            else high = middle - 1;
        }
        long used = totalXpForLevel(low);
        long next = ClientState.linearVanillaXp() ? ClientState.vanillaXpPerLevel()
                : low >= 30 ? 112L + (low - 30L) * 9 : low >= 15 ? 37L + (low - 15L) * 5 : 7L + low * 2L;
        return new XpPreview(low, Math.min(1f, (points - used) / (float) Math.max(1, next)));
    }
    private long totalXpForLevel(int level) {
        if (ClientState.linearVanillaXp()) return (long) level * ClientState.vanillaXpPerLevel();
        if (level <= 15) return (long) level * level + 6L * level;
        if (level <= 30) {
            long steps = level - 15L;
            return 315 + 37 * steps + 5 * steps * (steps - 1) / 2;
        }
        long steps = level - 30L;
        return 1395 + 112 * steps + 9 * steps * (steps - 1) / 2;
    }
    private void renderCurrencies(GuiGraphics g, int mx, int my) {
        if (ClientState.customPoints().isEmpty()) return;
        List<String> currencies = new ArrayList<>();
        currencies.add("XP");
        currencies.addAll(ClientState.customPoints());
        int rows = (currencies.size() + 4) / 5;
        int firstY = y + panelHeight - (StatlogConfig.SHOW_XP_BAR.get() ? 82 : 63)
                - Math.max(0, rows - 2) * 13;
        for (int row = 0; row < rows; row++) {
            int start = row * 5;
            int end = Math.min(start + 5, currencies.size());
            int width = (end - start - 1) * 12;
            for (int i = start; i < end; i++)
                width += 10 + font.width(Integer.toString(previewBalance(currencies.get(i))));
            int cellX = x + leftWidth / 2 - width / 2;
            int cellY = firstY + row * 13;
            for (int i = start; i < end; i++) {
                String id = currencies.get(i);
                String value = Integer.toString(previewBalance(id));
                int tint = id.equals("XP") ? color() : ClientState.pointColor(id);
                g.setColor(((tint >> 16) & 255) / 255f, ((tint >> 8) & 255) / 255f, (tint & 255) / 255f, 1f);
                g.blit(CURRENCY_ICON, cellX - 4, cellY - 4, 0, 0, 16, 16, 16, 16);
                g.setColor(1, 1, 1, 1);
                int textColor = id.equals("XP") ? StatlogTextColors.get("xp_level", tint)
                        : StatlogTextColors.get("currency_custom", tint);
                g.drawString(font, value, cellX + 10, cellY, textColor, true);
                int cellWidth = 10 + font.width(value);
                if (mx >= cellX && mx < cellX + cellWidth && my >= cellY && my < cellY + 10)
                    g.renderTooltip(font, ClientState.pointLabel(id).copy().withStyle(style -> style.withColor(textColor)), mx, my);
                cellX += cellWidth + 12;
            }
        }
    }
    private int color() {
        try { return 0xFF000000 | Integer.parseInt(StatlogConfig.EXPERIENCE_COLOR.get().replace("#", ""), 16); }
        catch (NumberFormatException ignored) { return 0xFF55FF55; }
    }
    private Component statText(String text, boolean maxed) {
        Component value = Component.literal(text);
        return maxed && StatlogConfig.MAXED_BOLD.get() ? value.copy().withStyle(ChatFormatting.BOLD) : value;
    }
    private int maxedColor(int fallback) {
        String hex = StatlogConfig.MAXED_COLOR.get().trim();
        if (hex.matches("#?[0-9a-fA-F]{6}")) return 0xFF000000 | Integer.parseInt(hex.replace("#", ""), 16);
        return StatlogConfig.MAXED_TEXT_SEPIA.get() ? 0xFFA88A67 : fallback;
    }
    private void renderEntries(GuiGraphics g) {
        List<AttributeDefinition> entries = entries();
        int max = maxScroll();
        targetScroll = Math.max(0, Math.min(max, targetScroll));
        scroll += (targetScroll - scroll) * (StatlogConfig.SMOOTH_SCROLLING.get() ? 0.35 : 1);
        int rowWidth = listWidth - 16;
        int contentHeight = ClientState.tabs().isEmpty()
                ? Math.min(listHeight, Math.max(rowHeight, entries.size() * rowHeight)) : listHeight;
        NineSlice.draw(g, ENTRY, listX - 10, listY - 10, rowWidth + 20, contentHeight + 20);
        g.flush();
        g.enableScissor(listX, listY, listX + listWidth, listY + listHeight);
        for (int i = 0; i < entries.size(); i++) {
            AttributeDefinition def = entries.get(i);
            int rowY = listY + i * rowHeight - (int) scroll;
            if (rowY + rowHeight < listY || rowY > listY + listHeight) continue;
            if (i < entries.size() - 1) {
                g.setColor(0.60f, 0.45f, 0.31f, 1f);
                g.blit(WHITE, listX + 5, rowY + rowHeight - 1, 0, 0, rowWidth - 10, 1, 1, 1);
                g.setColor(1, 1, 1, 1);
            }
            boolean rowCurrency = StatlogConfig.SHOW_ROW_CURRENCY.get() || eligible(def).size() > 1;
            boolean maxed = level(def) >= def.maxLevel();
            if (rowCurrency) drawCurrencyIcon(g, def, listX + 1, rowY + (rowHeight - 16) / 2);
            drawIcon(g, def.displayedIcon(level(def)), listX + (rowCurrency ? 18 : 1),
                    rowY + (rowHeight - 16) / 2);
            int old = ClientState.level(def);
            int current = level(def);
            boolean canIncrease = current < def.maxLevel() && ClientState.canEdit()
                    && ClientState.upgradeAllowed(def.attribute()) && affordable(def);
            boolean canDecrease = current > def.minLevel() && (current > old || ClientState.canRespec());
            int textColor = canIncrease ? StatlogTextColors.get("attribute_available", 0xFF3C332B)
                    : StatlogTextColors.get("attribute_unavailable", 0xFF9A8C80);
            if (maxed) textColor = maxedColor(textColor);
            int textMax = Math.max(16, rowWidth - (editing ? 103 : 66) + (rowCurrency ? 0 : 17));
            int nameX = listX + (rowCurrency ? 38 : 21);
            int nameY = rowY + (rowHeight - 9) / 2;
            Component name = statText(StatlogLanguage.value(def.name()).getString(), maxed);
            int overflow = font.width(name) - textMax;
            if (overflow > 0) {
                long travel = overflow * 40L;
                long phase = Util.getMillis() % (travel * 2 + 1600);
                int offset = phase < 800 ? 0 : phase < travel + 800 ? (int) ((phase - 800) / 40)
                        : phase < travel + 1600 ? overflow : overflow - (int) ((phase - travel - 1600) / 40);
                g.flush();
                g.enableScissor(nameX, nameY, nameX + textMax, nameY + font.lineHeight);
                g.drawString(font, name, nameX - offset, nameY, textColor, false);
                g.flush();
                g.disableScissor();
            } else g.drawString(font, name, nameX, nameY, textColor, false);
            int right = listX + rowWidth - 6;
            if (editing) {
                g.drawString(font, Component.literal("<").withStyle(net.minecraft.ChatFormatting.BOLD),
                        right - 53, rowY + (rowHeight - 9) / 2,
                        canDecrease ? StatlogTextColors.get("selector_enabled", 0xFF2C241B)
                                : StatlogTextColors.get("selector_disabled", 0xFF8A7D71), false);
                String value = Integer.toString(current);
                Component number = statText(value, maxed);
                g.drawString(font, number, right - 28 - font.width(number) / 2, rowY + (rowHeight - 9) / 2, textColor, false);
                g.drawString(font, Component.literal(">").withStyle(net.minecraft.ChatFormatting.BOLD),
                        right - 8, rowY + (rowHeight - 9) / 2,
                        canIncrease ? StatlogTextColors.get("selector_enabled", 0xFF2C241B)
                                : StatlogTextColors.get("selector_disabled", 0xFF8A7D71), false);
            } else {
                String value = Integer.toString(current);
                Component number = statText(value, maxed);
                g.drawString(font, number, right - 10 - font.width(number) / 2, rowY + (rowHeight - 9) / 2, textColor, false);
            }
        }
        g.disableScissor();
        if (max > 0) renderScrollbar(g, max);
    }
    private void renderScrollbar(GuiGraphics g, int max) {
        g.pose().pushPose();
        g.pose().translate(scrollbarX(), scrollbarY(), 0);
        // The thumb stays 8x12; the track stretches between its fixed end rows.
        g.blit(SCROLL_TRACK, 2, 0, 4, 1, 0, 0, 4, 1, 4, 3);
        g.blit(SCROLL_TRACK, 2, 1, 4, listHeight - 2, 0, 1, 4, 1, 4, 3);
        g.blit(SCROLL_TRACK, 2, listHeight - 1, 4, 1, 0, 2, 4, 1, 4, 3);
        int thumbY = (int) (scroll / max * (listHeight - 12));
        g.blit(SCROLL_THUMB, 0, thumbY, 8, 12, 0, 0, 8, 12, 8, 12);
        g.pose().popPose();
    }
    private double scrollbarX() {
        return listX + listWidth * StatlogConfig.SCROLL_X_SCALE.get() + StatlogConfig.SCROLL_X_OFFSET.get();
    }
    private double scrollbarY() {
        return listY + listHeight * StatlogConfig.SCROLL_Y_SCALE.get() + StatlogConfig.SCROLL_Y_OFFSET.get();
    }
    private void renderTooltip(GuiGraphics g, int mx, int my) {
        if (!StatlogConfig.SHOW_TOOLTIPS.get() || mx < listX || mx >= listX + listWidth
                || my < listY || my >= listY + listHeight) return;
        List<AttributeDefinition> entries = entries();
        int index = (my - listY + (int) scroll) / rowHeight;
        if (index < 0 || index >= entries.size()) return;
        AttributeDefinition def = entries.get(index);
        if ((StatlogConfig.SHOW_ROW_CURRENCY.get() || eligible(def).size() > 1) && mx < listX + 17) {
            g.renderTooltip(font, ClientState.pointLabel(currency(def)), mx, my);
            return;
        }
        int baseWidth = listWidth + 4;
        int tw = Math.max(45, Math.min(width - 12,
                (int) Math.round(baseWidth * StatlogConfig.TOOLTIP_WIDTH_SCALE.get())
                        + StatlogConfig.TOOLTIP_WIDTH_OFFSET.get()));
        var lines = font.split(StatlogLanguage.value(def.description()), tw - 20);
        boolean currentValue = StatlogConfig.SHOW_CURRENT_VALUE.get();
        boolean incrementValue = StatlogConfig.SHOW_INCREMENT_VALUE.get() && level(def) < def.maxLevel();
        boolean tooltipCurrency = StatlogConfig.SHOW_TOOLTIP_CURRENCY.get();
        var currentLines = currentValue ? font.split(Component.literal(statValueText(def, level(def), false)), tw - 18)
                : List.<net.minecraft.util.FormattedCharSequence>of();
        var incrementLines = incrementValue ? font.split(Component.literal(statValueText(def, level(def), true)), tw - 18)
                : List.<net.minecraft.util.FormattedCharSequence>of();
        if (currentLines.size() > 2) currentLines = currentLines.subList(0, 2);
        if (incrementLines.size() > 2) incrementLines = incrementLines.subList(0, 2);
        int extraHeight = (currentLines.size() + incrementLines.size()) * 10 + (tooltipCurrency ? 14 : 0);
        int maxLines = Math.max(0, (height - 44 - extraHeight) / 10);
        if (lines.size() > maxLines) lines = lines.subList(0, maxLines);
        int th = Math.max(38, 32 + lines.size() * 10 + extraHeight);
        int tx = Math.max(6, Math.min(width - tw - 6, listX - 10 + (baseWidth - tw) / 2));
        int ty = Math.max(6, Math.min(listY - th - 17, height - th - 6));
        if (edit != null && edit.visible) ty = Math.max(6, Math.min(ty, edit.getY() - th - 21));
        tx = Math.max(6, Math.min(width - tw - 6, tx + StatlogConfig.TOOLTIP_X_OFFSET.get()));
        ty = Math.max(6, Math.min(height - th - 6, ty + StatlogConfig.TOOLTIP_Y_OFFSET.get()));
        NineSlice.draw(g, TOOLTIP, tx, ty, tw, th);
        drawIcon(g, def.displayedIcon(level(def)), tx + 7, ty + 7);
        String tooltipName = font.plainSubstrByWidth(StatlogLanguage.value(def.name()).getString(), Math.max(10, tw - 37));
        g.drawString(font, tooltipName, tx + 28, ty + 10,
                StatlogTextColors.get("tooltip_title", 0xFFFFFFFF), false);
        int lineY = ty + 26;
        for (var line : lines) {
            g.drawString(font, line, tx + 9, lineY,
                    StatlogTextColors.get("tooltip_description", 0xFFDDDDDD), false);
            lineY += 10;
        }
        for (var line : currentLines) {
            g.drawString(font, line, tx + 9, lineY,
                    StatlogTextColors.get("tooltip_description", 0xFFDDDDDD), false);
            lineY += 10;
        }
        for (var line : incrementLines) {
            g.drawString(font, line, tx + 9, lineY, 0xFF999999, false);
            lineY += 10;
        }
        if (tooltipCurrency) {
            drawCurrencyIcon(g, def, tx + 8, lineY - 3);
            g.drawString(font, ClientState.pointLabel(currency(def)), tx + 27, lineY,
                    StatlogTextColors.get("tooltip_description", 0xFFDDDDDD), false);
        }
    }
    private String statValueText(AttributeDefinition def, int currentLevel, boolean increment) {
        String configured = increment ? def.incrementText() : def.currentText();
        String template = configured.isBlank() ? increment ? StatlogConfig.INCREMENT_VALUE_TEXT.get()
                : StatlogConfig.CURRENT_VALUE_TEXT.get() : configured;
        String result = StatlogLanguage.value(template).getString();
        int next = Math.min(def.maxLevel(), currentLevel + 1);
        for (int i = 0; i < def.effects().size(); i++) {
            var effect = def.effects().get(i);
            double base = ClientState.attributeBase(effect.attribute());
            double current = effect.bonus(currentLevel, def.minLevel(), base);
            double amount = increment ? effect.bonus(next, def.minLevel(), base) - current : current;
            if (effect.percent()) amount = Math.abs(base) < 0.000001 ? Double.NaN : amount / base * 100;
            result = result.replace("{" + (i + 1) + "}", formatStatValue(amount, effect.percent()));
        }
        return result;
    }
    private String formatStatValue(double value, boolean percent) {
        if (!Double.isFinite(value)) return "—";
        if (Math.abs(value) < 0.005) value = 0;
        String number = String.format(java.util.Locale.ROOT, "%.2f", Math.abs(value))
                .replaceAll("0+$", "").replaceAll("\\.$", "");
        return (value > 0 ? "+" : value < 0 ? "-" : "") + number + (percent ? "%" : "");
    }
    private ItemStack icon(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        Item item = key == null ? null : BuiltInRegistries.ITEM.get(key);
        return item == null ? ItemStack.EMPTY : item.getDefaultInstance();
    }
    private void drawCurrencyIcon(GuiGraphics g, AttributeDefinition def, int iconX, int iconY) {
        String id = currency(def);
        boolean maxed = level(def) >= def.maxLevel();
        int tint = maxed && StatlogConfig.MAX_CURRENCY_SEPIA.get() ? 0xFFAA8254
                : id.equals("XP") ? color() : ClientState.pointColor(id);
        g.setColor(((tint >> 16) & 255) / 255f, ((tint >> 8) & 255) / 255f, (tint & 255) / 255f, 1f);
        g.blit(maxed && StatlogConfig.MAX_CURRENCY_ICON.get() ? CURRENCY_MAX_ICON : CURRENCY_ICON,
                iconX, iconY, 0, 0, 16, 16, 16, 16);
        g.setColor(1, 1, 1, 1);
    }
    private void drawIcon(GuiGraphics g, String id, int iconX, int iconY) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key != null && id.endsWith(".png")) {
            ResourceLocation texture = GeneratedIcons.texture(id);
            if (texture != null) g.blit(texture, iconX, iconY, 0, 0, 16, 16, 16, 16);
        } else {
            g.renderItem(icon(id), iconX, iconY);
        }
    }
    private List<AttributeDefinition> entries() { return ClientState.definitions().values().stream()
            .filter(def -> activeTab == null || def.tabs().contains(activeTab)
                    || (def.tabs().isEmpty() && !ClientState.tabs().isEmpty()
                    && activeTab.equals(ClientState.tabs().getFirst().id()))).toList(); }
    private int maxScroll() { return Math.max(0, entries().size() * rowHeight - listHeight); }
    private int level(AttributeDefinition def) { return pending.getOrDefault(def.attribute(), ClientState.level(def)); }
    private List<String> eligible(AttributeDefinition def) {
        if (!def.acceptedPoints().contains("ALL")) return def.acceptedPoints();
        List<String> result = new ArrayList<>();
        result.add("XP");
        result.addAll(ClientState.customPoints().stream().sorted().toList());
        return result;
    }
    private String currency(AttributeDefinition def) {
        List<String> values = eligible(def);
        return values.isEmpty() ? "XP" : values.get(selected.getOrDefault(def.attribute(), 0) % values.size());
    }
    private boolean affordable(AttributeDefinition def) {
        if (level(def) < ClientState.level(def)) return true;
        String currency = currency(def);
        int balance = previewBalance(currency);
        return balance >= cost(def, currency);
    }
    private int cost(AttributeDefinition def, String currency) {
        return currency.equals("XP")
                ? (int) Math.min(Integer.MAX_VALUE, (long) def.cost()
                    * (ClientState.xpCostBase() + (long) (level(def) - def.minLevel()) * ClientState.xpCostIncrement()))
                : def.cost();
    }
    private int previewBalance(String currency) {
        int balance = currency.equals("XP") ? xpBalance() : ClientState.balance(currency);
        if (!editing) return balance;
        for (var entry : choices.entrySet()) {
            AttributeDefinition other = ClientState.definitions().get(entry.getKey());
            if (other == null) continue;
            int level = ClientState.level(other);
            for (String spent : entry.getValue()) {
                if (spent.equals(currency)) {
                    int cost = spent.equals("XP")
                            ? (int) Math.min(Integer.MAX_VALUE, (long) other.cost()
                                * (ClientState.xpCostBase() + (long) (level - other.minLevel()) * ClientState.xpCostIncrement()))
                            : other.cost();
                    balance = Math.max(0, balance - cost);
                }
                level++;
            }
        }
        return balance;
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int max = maxScroll();
        if (max > 0 && mx >= scrollbarX() - 2 && mx < scrollbarX() + 10
                && my >= scrollbarY() && my < scrollbarY() + listHeight) {
            dragging = true; dragScroll(my, max); return true;
        }
        if (!editing || button != 0 || mx < listX || mx >= listX + listWidth - 16
                || my < listY || my >= listY + listHeight) return false;
        List<AttributeDefinition> entries = entries();
        int index = (int) (my - listY + scroll) / rowHeight;
        if (index < 0 || index >= entries.size()) return false;
        AttributeDefinition def = entries.get(index);
        int right = listX + listWidth - 22;
        if (eligible(def).size() > 1 && mx >= listX && mx < listX + 18) {
            selected.merge(def.attribute(), 1, Integer::sum); return true;
        }
        if (mx >= right - 59 && mx < right - 42) {
            heldAttribute = def.attribute(); heldDirection = -1; nextRepeat = Util.getMillis() + 350;
            changeLevel(def, -1);
            return true;
        }
        if (mx >= right - 15 && mx < right + 4) {
            heldAttribute = def.attribute(); heldDirection = 1; nextRepeat = Util.getMillis() + 350;
            changeLevel(def, 1);
            return true;
        }
        return false;
    }
    @Override public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        if (button == 0) heldAttribute = null;
        return super.mouseReleased(mx, my, button);
    }
    private void repeatArrow(int mx, int my) {
        if (heldAttribute == null || !editing || my < listY || my >= listY + listHeight) return;
        int index = (int) (my - listY + scroll) / rowHeight;
        List<AttributeDefinition> rows = entries();
        int right = listX + listWidth - 22;
        boolean onArrow = heldDirection < 0 ? mx >= right - 59 && mx < right - 42
                : mx >= right - 15 && mx < right + 4;
        if (index < 0 || index >= rows.size() || !rows.get(index).attribute().equals(heldAttribute) || !onArrow) return;
        long now = Util.getMillis();
        if (now >= nextRepeat) {
            changeLevel(rows.get(index), heldDirection);
            nextRepeat = now + 80;
        }
    }
    private void changeLevel(AttributeDefinition def, int direction) {
        int old = ClientState.level(def), current = level(def);
        if (direction < 0 && current > def.minLevel() && (current > old || ClientState.canRespec())) {
            pending.put(def.attribute(), current - 1);
            if (current > old && !choices.getOrDefault(def.attribute(), List.of()).isEmpty())
                choices.get(def.attribute()).removeLast();
            play(StatlogConfig.REMOVE_POINT_SOUND.get());
        } else if (direction > 0 && current < def.maxLevel() && ClientState.upgradeAllowed(def.attribute())
                && ClientState.canEdit() && affordable(def)) {
            if (current >= old) choices.computeIfAbsent(def.attribute(), ignored -> new ArrayList<>()).add(currency(def));
            pending.put(def.attribute(), current + 1);
            play(StatlogConfig.ADD_POINT_SOUND.get());
        }
    }
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) { dragScroll(my, maxScroll()); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }
    private void dragScroll(double my, int max) {
        targetScroll = max * Math.max(0, Math.min(1, (my - scrollbarY() - 6) / Math.max(1, listHeight - 12)));
        scroll = targetScroll;
    }
    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        boolean overList = mx >= listX && mx < listX + listWidth && my >= listY && my < listY + listHeight;
        boolean overScrollbar = maxScroll() > 0 && mx >= scrollbarX() - 2 && mx < scrollbarX() + 10
                && my >= scrollbarY() && my < scrollbarY() + listHeight;
        if (overList || overScrollbar) {
            targetScroll -= sy * rowHeight; return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }
    private void submit() {
        if (!ClientState.canEdit()) { error = "Editing is no longer available"; return; }
        CompoundTag request = new CompoundTag();
        request.putString("TargetId", ClientState.targetId() == null ? "" : ClientState.targetId().toString());
        CompoundTag levels = new CompoundTag();
        pending.forEach(levels::putInt);
        request.put("Levels", levels);
        CompoundTag currencies = new CompoundTag();
        choices.forEach((id, values) -> {
            ListTag list = new ListTag();
            values.forEach(value -> list.add(StringTag.valueOf(value)));
            currencies.put(id, list);
        });
        request.put("Currencies", currencies);
        PacketDistributor.sendToServer(new StatlogNetwork.Finalize(request));
    }
    private int xpBalance() {
        if (minecraft == null || minecraft.player == null) return 0;
        if (ClientState.currencyMode().equalsIgnoreCase("XP_LEVELS"))
            return ClientState.remote() ? ClientState.vanillaLevel() : minecraft.player.experienceLevel;
        return ClientState.remote() ? ClientState.vanillaExperience() : minecraft.player.totalExperience;
    }
    private void drawScaledCentered(GuiGraphics g, Component text, int centerX, int topY,
                                    float scale, int maxWidth, int color) {
        scale = Math.min(scale, maxWidth / (float) Math.max(1, font.width(text)));
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1f);
        g.drawCenteredString(font, text, Math.round(centerX / scale), Math.round(topY / scale), color);
        g.pose().popPose();
    }
    private void drawPulsingCentered(GuiGraphics g, Component text, int centerX, int centerY,
                                     float scale, int maxWidth, int color) {
        scale = Math.min(scale, maxWidth / (float) Math.max(1, font.width(text)));
        g.pose().pushPose();
        g.pose().translate(centerX, centerY, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawCenteredString(font, text, 0, -font.lineHeight / 2, color);
        g.pose().popPose();
    }
    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(Statlog.ID, "textures/gui/" + name);
    }
}
