package dev.nidza.statlog.server;

import com.electronwill.nightconfig.toml.TomlParser;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public final class ItemRulesCheck {
    public static void main(String[] args) {
        var rules = ItemRules.parse(List.of(
                "minecraft:netherite_sword|statlog:empty/strength|5|UNUSABLE",
                "minecraft:netherite_sword|statlog:empty/strength|10|DEBUFF|minecraft:generic.attack_speed|-0.5",
                "minecraft:netherite_sword|statlog:empty/strength|15|DEBUFF|minecraft:generic.attack_speed|-0.25",
                "minecraft:netherite_sword|statlog:empty/mind|10|DEBUFF|minecraft:generic.movement_speed|-0.1"));
        ResourceLocation sword = ResourceLocation.parse("minecraft:netherite_sword");
        if (!ItemRules.active(rules, sword, stat -> stat.endsWith("mind") ? 10 : 1).getFirst().unusable())
            throw new AssertionError("Low strength should make the sword unusable");
        var halfSpeed = ItemRules.active(rules, sword, stat -> stat.endsWith("mind") ? 10 : 5);
        if (halfSpeed.size() != 1 || halfSpeed.getFirst().strength() != -0.5)
            throw new AssertionError("Level 5 should select the 50% penalty");
        var stacked = ItemRules.active(rules, sword, stat -> stat.endsWith("mind") ? 1 : 10);
        if (stacked.size() != 2 || stacked.stream().anyMatch(ItemRules.Rule::unusable))
            throw new AssertionError("Different stats should contribute separate debuffs");
        if (!ItemRules.active(rules, sword, stat -> 15).isEmpty())
            throw new AssertionError("Meeting every requirement should remove the penalties");

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
        var atFive = ItemRules.active(configured, sword, stat -> stat.endsWith("mind") ? 1 : 5);
        if (atFive.size() != 3 || ItemRules.multiplier(atFive, ItemRules.Kind.ITEM_DAMAGE) != 0.25f)
            throw new AssertionError("Same-tier actions and another stat should stack");
        var atTen = ItemRules.active(configured, sword, stat -> 10);
        if (atTen.size() != 1 || atTen.getFirst().kind() != ItemRules.Kind.PLAYER_ATTRIBUTE)
            throw new AssertionError("Next configured threshold should take over");
        if (ItemRules.multiplier(ItemRules.active(configured, ResourceLocation.parse("minecraft:diamond_pickaxe"),
                stat -> 1), ItemRules.Kind.ITEM_MINING_SPEED) != 0.75f)
            throw new AssertionError("Item mining penalty was not read");
    }
}
