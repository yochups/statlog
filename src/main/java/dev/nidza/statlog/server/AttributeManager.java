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
            ResourceLocation key = BuiltInRegistries.ATTRIBUTE.getKey(holder.value());
            instance.removeModifier(modifierId(key));
        }
        for (AttributeDefinition definition : AttributeDefinitions.all().values()) apply(player, definition);
    }

    public static void apply(ServerPlayer player, AttributeDefinition definition) {
        if (definition.attribute().equals("statlog:empty") || definition.attribute().startsWith("statlog:empty/")) return;
        ResourceLocation key = ResourceLocation.tryParse(definition.attribute());
        if (key == null) return;
        Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.getHolder(key).orElse(null);
        if (holder == null) return;
        AttributeInstance instance = player.getAttribute(holder);
        if (instance == null) return;
        ResourceLocation modifierId = modifierId(key);
        instance.removeModifier(modifierId);
        double bonus = definition.bonus(player.getData(Statlog.PROGRESS).level(definition));
        if (bonus != 0 && Double.isFinite(bonus))
            instance.addPermanentModifier(new AttributeModifier(modifierId, bonus, AttributeModifier.Operation.ADD_VALUE));
    }
    private static ResourceLocation modifierId(ResourceLocation key) {
        return ResourceLocation.fromNamespaceAndPath(Statlog.ID, "level/" + key.getNamespace() + "/" + key.getPath());
    }
    private AttributeManager() {}
}
