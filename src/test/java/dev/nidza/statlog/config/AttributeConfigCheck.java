package dev.nidza.statlog.config;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AttributeConfigCheck {
    public static void main(String[] args) throws Exception {
        Path file = Path.of("build/tmp/attribute-config-check.toml");
        Files.createDirectories(file.getParent());
        try (InputStream template = AttributeConfigCheck.class.getResourceAsStream("/default-statlog-attributes.toml")) {
            if (template == null) throw new AssertionError("Missing default config template");
            Files.copy(template, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        AttributeDefinitions.load(file);
        if (AttributeDefinitions.all().size() != 16 || AttributeDefinitions.tabs().size() != 3
                || !AttributeDefinitions.currencies().get("POWER").orbTexture()
                        .equals("statlog:textures/entity/currency_orbs/power.png")
                || !AttributeDefinitions.resolve("strength").attribute().equals("minecraft:generic.attack_damage")
                || !AttributeDefinitions.resolve("minecraft:strength").attribute().equals("minecraft:generic.attack_damage")
                || !AttributeDefinitions.resolve("strength").acceptedPoints().equals(java.util.List.of("XP"))
                || AttributeDefinitions.resolve("speed").maxLevel() != 5
                || AttributeDefinitions.resolve("strength").minLevel() != 0
                || AttributeDefinitions.resolve("attack_speed").extraAttributes().size() != 1
                || AttributeDefinitions.resolve("attack_speed").extraAttributes().getFirst().bonus(10, 0, 10) != -6
                || AttributeDefinitions.resolve("armor").extraAttributes().getFirst().incrementValue() != 0.25
                || !AttributeDefinitions.resolve("armor").currentText().contains("{value_separator}")
                || AttributeDefinitions.resolve("fall_reduction").extraAttributes().size() != 1
                || AttributeDefinitions.resolve("knockback").extraAttributes().size() != 1
                || AttributeDefinitions.resolve("swimming").effects().getFirst().percentageReference(0, 0, 10) != 1
                || !AttributeDefinitions.resolve("health").tabs().equals(java.util.List.of("combat", "survival", "utility")))
            throw new AssertionError("Concise default config did not load correctly");

        Files.writeString(file, """
                custom_points = []
                [[attribute_tabs]]
                id = "combat"
                name = "Combat"
                item = "minecraft:iron_sword"
                texture = "combat.png"
                visible = false
                [[listed_attributes]]
                attribute = "minecraft:generic.attack_damage"
                name = "Strength"
                currency = "XP"
                icon = "statlog:textures/gui/attributes/strength.png"
                tabs = ["combat"]
                accepted_points = ["XP"]
                min_level = 1
                max_level = 10
                starting_level = 1
                cost = 1
                [[listed_attributes.extra_attributes]]
                attribute = "statlog:target_invulnerability_ticks"
                increment_value = -0.6
                level_values = ["5:4"]
                """);
        AttributeDefinitions.load(file);
        if (AttributeDefinitions.all().size() != 1
                || !AttributeDefinitions.resolve("strength").attribute().equals("minecraft:generic.attack_damage")
                || !AttributeDefinitions.resolve("minecraft:generic.attack_damage").tabs().equals(java.util.List.of("combat"))
                || AttributeDefinitions.tabs().get("combat").visible()
                || AttributeDefinitions.resolve("strength").extraAttributes().getFirst().bonus(5, 1, 10) != -6
                || AttributeDefinitions.resolve("strength").extraAttributes().getFirst().bonus(4, 1, 10) != -1.8)
            throw new AssertionError("Older attribute config fields lost compatibility");

        Files.writeString(file, """
                [[listed_attributes]]
                attribute = "minecraft:generic.max_health"
                key = "health"
                name = "Health"
                icon = "health.png"
                """);
        AttributeDefinitions.load(file);
        if (!AttributeDefinitions.all().isEmpty())
            throw new AssertionError("Stat without required currency was accepted");
    }
}
