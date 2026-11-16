package dev.nidza.statlog.server;

import dev.nidza.statlog.Statlog;
import dev.nidza.statlog.config.AttributeDefinition;
import dev.nidza.statlog.config.AttributeDefinitions;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public final class AttributeManager {
    public static void apply(ServerPlayer player) {
        for (Holder<Attribute> holder : BuiltInRegistries.ATTRIBUTE.holders().toList()) {
            AttributeInstance instance = player.getAttribute(holder);
            if (instance == null) continue;
            for (ResourceLocation id : instance.getModifiers().stream().map(AttributeModifier::id)
                    .filter(id -> id.getNamespace().equals(Statlog.ID) && id.getPath().startsWith("level/"))
                    .toList()) instance.removeModifier(id);
        }
        for (AttributeDefinition definition : AttributeDefinitions.all().values()) apply(player, definition);
    }

    public static void apply(ServerPlayer player, AttributeDefinition definition) {
        int level = player.getData(Statlog.PROGRESS).level(definition);
        for (var effect : definition.effects()) {
            if (effect.attribute().equals("statlog:empty") || effect.attribute().startsWith("statlog:empty/")) continue;
            ResourceLocation key = ResourceLocation.tryParse(effect.attribute());
            if (key == null) continue;
            Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.getHolder(key).orElse(null);
            if (holder == null) continue;
            AttributeInstance instance = player.getAttribute(holder);
            if (instance == null) continue;
            ResourceLocation id = modifierId(definition.attribute(), key);
            instance.removeModifier(id);
            instance.removeModifier(ResourceLocation.fromNamespaceAndPath(Statlog.ID,
                    "level/" + key.getNamespace() + "/" + key.getPath()));
            double bonus = effect.bonus(level, definition.minLevel(), instance.getBaseValue());
            if (bonus != 0 && Double.isFinite(bonus))
                instance.addPermanentModifier(new AttributeModifier(id, bonus, AttributeModifier.Operation.ADD_VALUE));
        }
    }
    private static ResourceLocation modifierId(String stat, ResourceLocation key) {
        ResourceLocation owner = ResourceLocation.parse(stat);
        return ResourceLocation.fromNamespaceAndPath(Statlog.ID,
                "level/" + owner.getNamespace() + "/" + owner.getPath() + "/" + key.getNamespace() + "/" + key.getPath());
    }
    private AttributeManager() {}
}
