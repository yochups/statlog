package dev.nidza.statlog.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class StatlogConfig {
    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    public static final ModConfigSpec.ConfigValue<String> TITLE;
    public static final ModConfigSpec.ConfigValue<String> CURRENCY_MODE;
    public static final ModConfigSpec.BooleanValue LINEAR_VANILLA_XP;
    public static final ModConfigSpec.IntValue VANILLA_XP_PER_LEVEL;
    public static final ModConfigSpec.IntValue XP_COST_BASE;
    public static final ModConfigSpec.IntValue XP_COST_INCREMENT;
    public static final ModConfigSpec.IntValue STARTING_LEVEL;
    public static final ModConfigSpec.IntValue MAX_LEVEL;
    public static final ModConfigSpec.IntValue XP_REQUIREMENT_BASE;
    public static final ModConfigSpec.IntValue XP_REQUIREMENT_INCREMENT;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> MANUAL_XP_REQUIREMENTS;
    public static final ModConfigSpec.IntValue POINTS_PER_LEVEL;
    public static final ModConfigSpec.ConfigValue<String> POINTS_AWARD_CURRENCY;
    public static final ModConfigSpec.ConfigValue<String> EDIT_ACCESS;
    public static final ModConfigSpec.BooleanValue ENABLE_RESPECCING;
    public static final ModConfigSpec.DoubleValue RESPEC_REFUND_PERCENTAGE;
    public static final ModConfigSpec.ConfigValue<String> RESPEC_REFUND;
    public static final ModConfigSpec.BooleanValue RESPEC_REQUIRES_PERMISSION;
    public static final ModConfigSpec.BooleanValue KEEP_PROGRESS_ON_DEATH;

    public static final ModConfigSpec.BooleanValue SHOW_TAB;
    public static final ModConfigSpec.BooleanValue SHOW_EDIT_TAB;
    public static final ModConfigSpec.BooleanValue HIDE_SINGLE_TAB;
    public static final ModConfigSpec.BooleanValue SHOW_XP_BAR;
    public static final ModConfigSpec.BooleanValue SHOW_TOOLTIPS;
    public static final ModConfigSpec.BooleanValue SHOW_ROW_CURRENCY;
    public static final ModConfigSpec.BooleanValue SHOW_TOOLTIP_CURRENCY;
    public static final ModConfigSpec.BooleanValue SHOW_TOOLTIP_CURRENCY_BY_NAME;
    public static final ModConfigSpec.BooleanValue HIDE_SINGLE_CURRENCY;
    public static final ModConfigSpec.BooleanValue MAX_CURRENCY_ICON;
    public static final ModConfigSpec.BooleanValue MAX_CURRENCY_SEPIA;
    public static final ModConfigSpec.BooleanValue SHOW_CURRENT_VALUE;
    public static final ModConfigSpec.BooleanValue SHOW_INCREMENT_VALUE;
    public static final ModConfigSpec.ConfigValue<String> CURRENT_VALUE_TEXT;
    public static final ModConfigSpec.ConfigValue<String> INCREMENT_VALUE_TEXT;
    public static final ModConfigSpec.ConfigValue<String> CURRENT_VALUE_SEPARATOR;
    public static final ModConfigSpec.ConfigValue<String> INCREMENT_VALUE_SEPARATOR;
    public static final ModConfigSpec.BooleanValue SMOOTH_SCROLLING;
    public static final ModConfigSpec.ConfigValue<String> EXPERIENCE_COLOR;
    public static final ModConfigSpec.DoubleValue SCROLL_X_SCALE;
    public static final ModConfigSpec.DoubleValue SCROLL_X_OFFSET;
    public static final ModConfigSpec.DoubleValue SCROLL_Y_SCALE;
    public static final ModConfigSpec.DoubleValue SCROLL_Y_OFFSET;
    public static final ModConfigSpec.IntValue ATTRIBUTE_ROW_HEIGHT;
    public static final ModConfigSpec.IntValue PANEL_WIDTH;
    public static final ModConfigSpec.IntValue PANEL_HEIGHT;
    public static final ModConfigSpec.DoubleValue TOOLTIP_WIDTH_SCALE;
    public static final ModConfigSpec.IntValue TOOLTIP_WIDTH_OFFSET;
    public static final ModConfigSpec.IntValue TOOLTIP_X_OFFSET;
    public static final ModConfigSpec.IntValue TOOLTIP_Y_OFFSET;
    public static final ModConfigSpec.BooleanValue ANIMATE_UPGRADE_BADGE;
    public static final ModConfigSpec.ConfigValue<String> AFTER_FINALIZE;
    public static final ModConfigSpec.ConfigValue<String> ADD_POINT_SOUND;
    public static final ModConfigSpec.ConfigValue<String> REMOVE_POINT_SOUND;
    public static final ModConfigSpec.ConfigValue<String> FINALIZE_SOUND;
    public static final ModConfigSpec.BooleanValue FINALIZE_SOUND_ENABLED;
    public static final ModConfigSpec.BooleanValue MAXED_BOLD;
    public static final ModConfigSpec.ConfigValue<String> MAXED_COLOR;
    public static final ModConfigSpec.BooleanValue MAXED_TEXT_SEPIA;
    public static final ModConfigSpec.DoubleValue XP_BAR_SCALE;
    public static final ModConfigSpec.IntValue XP_BAR_X_OFFSET;
    public static final ModConfigSpec.IntValue XP_BAR_Y_OFFSET;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("progression");
        TITLE = b.comment("Title shown above the player preview; translation keys are supported.").define("title", "Attributes");
        CURRENCY_MODE = b.comment("XP_LEVELS spends vanilla levels (default); XP spends vanilla points.")
                .define("currency_mode", "XP_LEVELS");
        LINEAR_VANILLA_XP = b.comment("Use a fixed XP cost for each vanilla Minecraft level, so total XP grows linearly. Off keeps Minecraft's usual curve.")
                .define("linear_vanilla_xp", true);
        VANILLA_XP_PER_LEVEL = b.comment("XP points needed for each vanilla level when linear_vanilla_xp is on.")
                .defineInRange("vanilla_xp_per_level", 50, 1, 1000000);
        XP_COST_BASE = b.comment("Base cost for one attribute level in the selected XP currency.").defineInRange("xp_cost_base", 1, 0, Integer.MAX_VALUE);
        XP_COST_INCREMENT = b.comment("Additional cost for each level already above the attribute minimum.").defineInRange("xp_cost_increment", 0, 0, Integer.MAX_VALUE);
        STARTING_LEVEL = b.comment("Initial independent progression level.").defineInRange("starting_level", 1, 0, 1000000);
        MAX_LEVEL = b.comment("Maximum independent progression level.").defineInRange("max_level", 100, 1, 1000000);
        XP_REQUIREMENT_BASE = b.comment("XP required to advance from the starting level.").defineInRange("xp_requirement_base", 100, 1, Integer.MAX_VALUE);
        XP_REQUIREMENT_INCREMENT = b.comment("Additional XP required for each later level.").defineInRange("xp_requirement_increment", 25, 0, Integer.MAX_VALUE);
        MANUAL_XP_REQUIREMENTS = b.comment("Optional next-level costs as level:cost entries; unspecified levels use the linear formula.")
                .defineListAllowEmpty("manual_xp_requirements", List.of(), o -> o instanceof String);
        POINTS_PER_LEVEL = b.comment("Points awarded in one custom currency for each independent level gained.")
                .defineInRange("points_per_level", 0, 0, 1000000);
        POINTS_AWARD_CURRENCY = b.comment("Custom currency ID receiving points_per_level; blank disables automatic awards.")
                .define("points_award_currency", "");
        b.pop();
        b.push("access");
        EDIT_ACCESS = b.comment("ALWAYS, COMMAND, SCRIPT, or POINTS. COMMAND opens edit mode only via the operator command.")
                .define("edit_access", "ALWAYS");
        ENABLE_RESPECCING = b.comment("Allow players to reduce committed stat levels. Disabled by default.")
                .define("enable_respeccing", false);
        RESPEC_REFUND_PERCENTAGE = b.comment("Fraction of the original payment returned per removed level, from 0.0 to 1.0.")
                .defineInRange("respec_refund_percentage", 1.0, 0.0, 1.0);
        RESPEC_REFUND = b.comment("Optional refund override: 50% for a percentage, or 2 for two currency units per level. Blank uses respec_refund_percentage.")
                .define("respec_refund", "");
        RESPEC_REQUIRES_PERMISSION = b.comment("Require a player-specific permission set by the API or command.")
                .define("respec_requires_permission", false);
        KEEP_PROGRESS_ON_DEATH = b.comment("Copy saved progression, currencies, and purchase history on death.")
                .define("keep_progress_on_death", true);
        b.pop();
        COMMON_SPEC = b.build();

        b = new ModConfigSpec.Builder();
        b.push("interface");
        SHOW_TAB = b.comment("Show the Statlog tab on the survival inventory.").define("show_statlog_button", true);
        SHOW_EDIT_TAB = b.comment("Show the book/barrier edit tab. Closing the screen already cancels unfinalized changes.")
                .define("show_edit_tab", false);
        HIDE_SINGLE_TAB = b.comment("Hide attribute tabs when only one tab is configured.")
                .define("hide_single_tab", true);
        SHOW_XP_BAR = b.comment("Show the vanilla-style XP bar and level on the left side of Statlog.")
                .define("show_xp_bar", false);
        SHOW_TOOLTIPS = b.comment("Show attribute descriptions when hovering entries.").define("show_attribute_tooltips", true);
        SHOW_ROW_CURRENCY = b.comment("Show the required currency beside each attribute name. Multi-currency selectors stay visible.")
                .define("show_row_currency", true);
        SHOW_TOOLTIP_CURRENCY = b.comment("Show the required currency in attribute tooltips too.")
                .define("show_tooltip_currency", false);
        SHOW_TOOLTIP_CURRENCY_BY_NAME = b.comment("Show the required currency next to the attribute name in its tooltip.")
                .define("show_tooltip_currency_by_name", false);
        HIDE_SINGLE_CURRENCY = b.comment("Hide row currency icons and the balance beneath the player when XP is the only currency.")
                .define("hide_single_currency", true);
        MAX_CURRENCY_ICON = b.comment("Use the alternate currency dot for maxed attributes.")
                .define("max_currency_icon", true);
        MAX_CURRENCY_SEPIA = b.comment("Tint maxed attribute currency dots sepia.")
                .define("max_currency_sepia", true);
        SHOW_CURRENT_VALUE = b.comment("Show the current stat bonus in attribute tooltips.")
                .define("show_current_value", true);
        SHOW_INCREMENT_VALUE = b.comment("Show the next level's increment on a separate tooltip line.")
                .define("show_increment_value", true);
        CURRENT_VALUE_TEXT = b.comment("Tooltip text for current bonus; {1}, {2}, etc. name each attribute in the stat.")
                .define("current_value_text", "Current bonus: {1}");
        INCREMENT_VALUE_TEXT = b.comment("Tooltip text for the next level; {1}, {2}, etc. name each attribute in the stat.")
                .define("increment_value_text", "Next level: {1}");
        CURRENT_VALUE_SEPARATOR = b.comment("Join aligned current_text entries with this text; Break starts a new line.")
                .define("current_value_separator", ", ");
        INCREMENT_VALUE_SEPARATOR = b.comment("Join aligned increment_text entries with this text; Break starts a new line.")
                .define("increment_value_separator", ", ");
        SMOOTH_SCROLLING = b.comment("Animate attribute list scrolling.").define("smooth_scrolling", true);
        EXPERIENCE_COLOR = b.comment("RGB hex color for Statlog progression displays.").define("experience_color", "#55FF55");
        ATTRIBUTE_ROW_HEIGHT = b.comment("Attribute row height in GUI pixels; 23 keeps icons and controls clear.")
                .defineInRange("attribute_row_height", 23, 23, 64);
        PANEL_WIDTH = b.comment("Whole Statlog window width in GUI pixels; clamped to the screen.")
                .defineInRange("panel_width", 450, 350, 1000);
        PANEL_HEIGHT = b.comment("Whole Statlog window height in GUI pixels; clamped to the screen.")
                .defineInRange("panel_height", 280, 220, 800);
        ANIMATE_UPGRADE_BADGE = b.comment("Make the inventory upgrade badge bob up and down.")
                .define("animate_upgrade_badge", true);
        AFTER_FINALIZE = b.comment("What to do after a successful Finalize: CLOSE, EDIT, or VIEW.")
                .define("after_finalize", "CLOSE");
        ADD_POINT_SOUND = b.comment("Sound event played when adding a stat point. Blank disables it.")
                .define("add_point_sound", "minecraft:entity.experience_orb.pickup");
        REMOVE_POINT_SOUND = b.comment("Sound event played when subtracting a stat point. Blank disables it.")
                .define("remove_point_sound", "minecraft:ui.button.click");
        FINALIZE_SOUND_ENABLED = b.comment("Play a sound when Finalize commits at least one added stat level.")
                .define("finalize_sound_enabled", true);
        FINALIZE_SOUND = b.comment("Sound event played after Finalize commits added stat levels. Blank disables it.")
                .define("finalize_sound", "minecraft:block.enchantment_table.use");
        MAXED_BOLD = b.comment("Bold the stat name and number when it reaches its maximum level.")
                .define("maxed_bold", true);
        MAXED_TEXT_SEPIA = b.comment("Use sepia for maxed stat names and numbers. A maxed_color overrides this.")
                .define("maxed_text_sepia", true);
        MAXED_COLOR = b.comment("Optional #RRGGBB override for a maxed stat name and number. Blank uses maxed_text_sepia.")
                .define("maxed_color", "");
        b.push("tooltip_layout");
        TOOLTIP_WIDTH_SCALE = b.comment("Tooltip width relative to the attribute panel width.")
                .defineInRange("width_scale", 1.0, 0.25, 4.0);
        TOOLTIP_WIDTH_OFFSET = b.comment("Extra tooltip width in GUI pixels after scaling.")
                .defineInRange("width_offset", 0, -500, 500);
        TOOLTIP_X_OFFSET = b.comment("Horizontal GUI-pixel offset from the automatic tooltip placement.")
                .defineInRange("x_offset", 0, -1000, 1000);
        TOOLTIP_Y_OFFSET = b.comment("Vertical GUI-pixel offset from the automatic tooltip placement.")
                .defineInRange("y_offset", 0, -1000, 1000);
        b.pop();
        b.push("xp_bar_layout");
        XP_BAR_SCALE = b.comment("Uniform scale of the 182x5 vanilla XP bar, constrained by the player panel width.")
                .defineInRange("scale", 1.0, 0.25, 4.0);
        XP_BAR_X_OFFSET = b.comment("Horizontal GUI-pixel offset from the centered XP bar.")
                .defineInRange("x_offset", 0, -1000, 1000);
        XP_BAR_Y_OFFSET = b.comment("Vertical GUI-pixel offset from the default XP bar position.")
                .defineInRange("y_offset", 0, -1000, 1000);
        b.pop();
        b.push("scrollbar_position");
        SCROLL_X_SCALE = b.comment("Scrollbar X position = stat list X + list width * x_scale + x_offset. This does not change its size.")
                .defineInRange("x_scale", 1.0, -10.0, 10.0);
        SCROLL_X_OFFSET = b.comment("Horizontal offset in GUI pixels; decimals allow half-pixel placement.")
                .defineInRange("x_offset", -14.5, -10000.0, 10000.0);
        SCROLL_Y_SCALE = b.comment("Scrollbar Y position = stat list Y + list height * y_scale + y_offset. This does not change its size.")
                .defineInRange("y_scale", 0.0, -10.0, 10.0);
        SCROLL_Y_OFFSET = b.comment("Vertical offset in GUI pixels.")
                .defineInRange("y_offset", 0.0, -10000.0, 10000.0);
        b.pop();
        b.pop();
        CLIENT_SPEC = b.build();
    }

    public static String currencyMode() {
        return CURRENCY_MODE.get().equalsIgnoreCase("XP") ? "XP" : "XP_LEVELS";
    }

    private StatlogConfig() {}
}
