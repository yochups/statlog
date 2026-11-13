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
        if (AttributeDefinitions.all().size() != 9 || AttributeDefinitions.tabs().size() != 3
                || !AttributeDefinitions.resolve("strength").attribute().equals("minecraft:generic.attack_damage")
                || !AttributeDefinitions.resolve("minecraft:strength").attribute().equals("minecraft:generic.attack_damage")
                || !AttributeDefinitions.resolve("strength").acceptedPoints().equals(java.util.List.of("XP"))
                || AttributeDefinitions.resolve("speed").maxLevel() != 5)
            throw new AssertionError("Concise default config did not load correctly");

        Files.writeString(file, """
                custom_points = []
                [[attribute_tabs]]
                id = "combat"
                name = "Combat"
                item = "minecraft:iron_sword"
                texture = "combat.png"
                [[listed_attributes]]
                attribute = "minecraft:generic.attack_damage"
                name = "Strength"
                icon = "statlog:textures/gui/attributes/strength.png"
                tabs = ["combat"]
                accepted_points = ["XP"]
                min_level = 1
                max_level = 10
                starting_level = 1
                cost = 1
                """);
        AttributeDefinitions.load(file);
        if (AttributeDefinitions.all().size() != 1
                || !AttributeDefinitions.resolve("strength").attribute().equals("minecraft:generic.attack_damage")
                || !AttributeDefinitions.resolve("minecraft:generic.attack_damage").tabs().equals(java.util.List.of("combat")))
            throw new AssertionError("Older attribute config fields lost compatibility");

        if (args.length > 0) {
            AttributeDefinitions.load(Path.of(args[0]));
            if (AttributeDefinitions.all().size() != 9 || AttributeDefinitions.customPoints().size() != 9
                    || AttributeDefinitions.tabs().size() != 11
                    || AttributeDefinitions.tabs().keySet().stream().anyMatch(tab -> AttributeDefinitions.all().values()
                            .stream().noneMatch(def -> def.tabs().contains(tab)))
                    || AttributeDefinitions.resolve("health").minLevel() != 0
                    || AttributeDefinitions.resolve("strength").maxLevel() != 100
                    || !AttributeDefinitions.resolve("strength").acceptedPoints().equals(java.util.List.of("POWER")))
                throw new AssertionError("Active config lost its custom settings");
        }
    }
}
