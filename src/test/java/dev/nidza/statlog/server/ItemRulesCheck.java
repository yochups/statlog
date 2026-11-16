package dev.nidza.statlog.server;

import com.electronwill.nightconfig.toml.TomlParser;
import net.minecraft.resources.ResourceLocation;

public final class ItemRulesCheck {
    public static void main(String[] args) {
        ResourceLocation sword = ResourceLocation.parse("minecraft:netherite_sword");
        var configured = ItemRules.parseTiers(new TomlParser().parse("""
                [[tiers]]
                item = "minecraft:netherite_sword"
                stat = "statlog:empty/strength"
                required_level = 5
                type = "UNUSABLE"

                [[tiers]]
                item = "minecraft:netherite_sword"
                stat = "statlog:empty/strength"
                required_level = 10
                type = "ITEM_DAMAGE"
                multiplier = 0.5

                [[tiers]]
                item = "minecraft:netherite_sword"
                stat = "statlog:empty/strength"
                required_level = 10
                type = "EFFECT"
                target = "minecraft:slowness"
                amplifier = 0

                [[tiers]]
                item = "minecraft:netherite_sword"
                stat = "statlog:empty/mind"
                required_level = 10
                type = "ITEM_DAMAGE"
                multiplier = 0.5

                [[tiers]]
                item = "minecraft:netherite_sword"
                stat = "statlog:empty/strength"
                required_level = 15
                type = "PLAYER_ATTRIBUTE"
                target = "minecraft:generic.attack_speed"
                amount = -0.25

                [[tiers]]
                item = "minecraft:diamond_pickaxe"
                stat = "statlog:empty/mind"
                required_level = 4
                type = "ITEM_MINING_SPEED"
                multiplier = 0.75
                """));
        if (configured.size() != 6) throw new AssertionError("Tier file did not load every supported action");
        if (!ItemRules.active(configured, sword, stat -> 1).stream().anyMatch(ItemRules.Rule::unusable))
            throw new AssertionError("Low strength should make the sword unusable");
        var atFive = ItemRules.active(configured, sword, stat -> stat.endsWith("mind") ? 1 : 5);
        if (atFive.size() != 3 || ItemRules.multiplier(atFive, ItemRules.Kind.ITEM_DAMAGE) != 0.25f)
            throw new AssertionError("Same-tier actions and another stat should stack");
        var atTen = ItemRules.active(configured, sword, stat -> 10);
        if (atTen.size() != 1 || atTen.getFirst().kind() != ItemRules.Kind.PLAYER_ATTRIBUTE)
            throw new AssertionError("Next configured threshold should take over");
        if (ItemRules.multiplier(ItemRules.active(configured, ResourceLocation.parse("minecraft:diamond_pickaxe"),
                stat -> 1), ItemRules.Kind.ITEM_MINING_SPEED) != 0.75f)
            throw new AssertionError("Item mining penalty was not read");
        var restricted = ItemRules.parseRestrictions(new TomlParser().parse("""
                [[tiers]]
                type = "RESTRICT"
                block = "#minecraft:doors"
                actions = ["USE_BLOCK", "BREAK_BLOCK", "PLACE_BLOCK"]
                stat = "strength"
                required_level = 5
                message = "Need {stat} {required}"
                """));
        if (restricted.size() != 1 || !restricted.getFirst().block().equals("#minecraft:doors")
                || restricted.getFirst().actions().size() != 3)
            throw new AssertionError("Tagged multi-action restriction was not parsed");
    }
}
