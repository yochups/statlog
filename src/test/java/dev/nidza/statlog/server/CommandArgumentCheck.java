package dev.nidza.statlog.server;

import com.mojang.brigadier.StringReader;
import net.minecraft.commands.arguments.ResourceLocationArgument;

public final class CommandArgumentCheck {
    public static void main(String[] args) throws Exception {
        StringReader input = new StringReader("minecraft:generic.max_health 1");
        String id = ResourceLocationArgument.id().parse(input).toString();
        if (!id.equals("minecraft:generic.max_health") || !input.getRemaining().equals(" 1"))
            throw new AssertionError("Namespaced attribute argument must leave the amount intact");
        StringReader shortCommand = new StringReader("strength 5");
        if (!ResourceLocationArgument.id().parse(shortCommand).toString().equals("minecraft:strength")
                || !shortCommand.getRemaining().equals(" 5"))
            throw new AssertionError("Short stat key must leave the level intact");
        StringReader fullCommand = new StringReader("minecraft:generic.max_health 5");
        if (!ResourceLocationArgument.id().parse(fullCommand).toString().equals("minecraft:generic.max_health")
                || !fullCommand.getRemaining().equals(" 5"))
            throw new AssertionError("Full stat ID must still work with the short command parser");
    }
}
