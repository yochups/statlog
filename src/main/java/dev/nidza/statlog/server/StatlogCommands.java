package dev.nidza.statlog.server;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import dev.nidza.statlog.config.AttributeDefinitions;
import dev.nidza.statlog.network.StatlogNetwork;

public final class StatlogCommands {
    public static void register(RegisterCommandsEvent event) {
        var root = Commands.literal("statlog").executes(ctx -> openSelf(ctx.getSource()));
        root.then(Commands.literal("help").executes(ctx -> help(ctx.getSource())));
        var open = Commands.literal("open")
                .executes(ctx -> openSelf(ctx.getSource()))
                .then(Commands.literal("tab").then(Commands.argument("tab", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(AttributeDefinitions.tabs().keySet(), builder))
                        .executes(ctx -> open(ctx.getSource(), ctx.getSource().getPlayerOrException(), "view",
                                StringArgumentType.getString(ctx, "tab")))))
                .then(Commands.argument("player", EntityArgument.player()).requires(source -> source.hasPermission(2))
                .executes(ctx -> open(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), "view"))
                        .then(Commands.literal("tab").then(Commands.argument("tab", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(AttributeDefinitions.tabs().keySet(), builder))
                                .executes(ctx -> open(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), "view",
                                        StringArgumentType.getString(ctx, "tab")))))
                        .then(Commands.literal("view").executes(ctx ->
                                open(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), "view_only")))
                        .then(Commands.literal("edit").executes(ctx -> {
                            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                            StatlogApi.allowEdit(player, true);
                            return open(ctx.getSource(), player, "edit");
                        })));
        root.then(open);
        root.then(Commands.literal("op").requires(source -> source.hasPermission(2)).then(Commands.literal("open")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> remoteOpen(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), "edit"))
                        .then(Commands.literal("tab").then(Commands.argument("tab", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(AttributeDefinitions.tabs().keySet(), builder))
                                .executes(ctx -> remoteOpen(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), "edit",
                                        StringArgumentType.getString(ctx, "tab")))))
                        .then(Commands.literal("view").executes(ctx ->
                                remoteOpen(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), "view")))
                        .then(Commands.literal("edit").executes(ctx ->
                                remoteOpen(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), "edit"))))));
        root.then(attributeCommands("stat"));
        root.then(attributeCommands("attributeLevel"));
        root.then(numberCommands("xp"));
        root.then(numberCommands("level"));
        root.then(pointCommands("currency"));
        root.then(pointCommands("points"));
        root.then(Commands.literal("orb").requires(source -> source.hasPermission(2))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("currency", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(AttributeDefinitions.customPoints(), builder))
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> spawnOrb(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "currency"),
                                                IntegerArgumentType.getInteger(ctx, "amount")))))));
        root.then(Commands.literal("respec").requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.literal("all").executes(ctx -> {
                    ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                    String error = UpgradeTransaction.respecAll(player);
                    return error == null ? success(ctx.getSource(), "Fully respecced " + player.getName().getString())
                            : fail(ctx.getSource(), error);
                }))
                .then(Commands.argument("attribute", ResourceLocationArgument.id())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(attributeSuggestions(), builder))
                        .then(Commands.argument("amount", StringArgumentType.greedyString()).executes(ctx -> {
                            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                            String requested = ResourceLocationArgument.getId(ctx, "attribute").toString();
                            var def = AttributeDefinitions.resolve(requested);
                            if (def == null) return fail(ctx.getSource(), "Unknown configured stat: " + requested);
                            String error = UpgradeTransaction.respecCommand(player,
                                    def.attribute(), StringArgumentType.getString(ctx, "amount"));
                            if (error != null) return fail(ctx.getSource(), error);
                            return success(ctx.getSource(), "Respecced " + player.getName().getString());
                        })))));
        root.then(Commands.literal("reset").requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> {
                    ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                    StatlogApi.resetStats(player);
                    return success(ctx.getSource(), "Reset Statlog stats for " + player.getName().getString());
                })));
        root.then(permissionCommands("edit"));
        root.then(permissionCommands("respec"));
        root.then(Commands.literal("reload").requires(source -> source.hasPermission(2)).executes(ctx -> {
            AttributeDefinitions.load();
            ItemRules.load();
            for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                ItemRules.clear(player);
                AttributeManager.apply(player);
                StatlogNetwork.sync(player);
            }
            return success(ctx.getSource(), "Reloaded Statlog attributes and item tiers");
        }));
        event.getDispatcher().register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> attributeCommands(String name) {
        var group = Commands.literal(name).requires(source -> source.hasPermission(2));
        group.then(Commands.literal("list").executes(ctx -> {
            AttributeDefinitions.keys().forEach((key, id) ->
                    ctx.getSource().sendSuccess(() -> Component.literal(key + " = " + id), false));
            return AttributeDefinitions.keys().size();
        }));
        for (String operation : new String[]{"get", "set", "add", "remove"}) {
            group.then(Commands.literal(operation).then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("attribute", ResourceLocationArgument.id())
                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(attributeSuggestions(), builder))
                            .executes(ctx -> operation.equals("get") ? attribute(ctx.getSource(),
                                    EntityArgument.getPlayer(ctx, "player"), ResourceLocationArgument.getId(ctx, "attribute").toString(), operation, 0) : 0)
                            .then(Commands.argument("amount", IntegerArgumentType.integer(0)).executes(ctx ->
                                    attribute(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                            ResourceLocationArgument.getId(ctx, "attribute").toString(), operation,
                                            IntegerArgumentType.getInteger(ctx, "amount")))))));
        }
        if (name.equals("stat")) group.then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("attribute", ResourceLocationArgument.id())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(attributeSuggestions(), builder))
                        .executes(ctx -> attribute(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                ResourceLocationArgument.getId(ctx, "attribute").toString(), "get", 0))
                        .then(Commands.argument("level", IntegerArgumentType.integer(0)).executes(ctx ->
                                attribute(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                        ResourceLocationArgument.getId(ctx, "attribute").toString(), "set",
                                        IntegerArgumentType.getInteger(ctx, "level"))))));
        return group;
    }

    private static java.util.List<String> attributeSuggestions() {
        return java.util.stream.Stream.concat(AttributeDefinitions.keys().keySet().stream(),
                AttributeDefinitions.all().keySet().stream()).toList();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> pointCommands(String name) {
        var group = Commands.literal(name).requires(source -> source.hasPermission(2));
        group.then(Commands.literal("list").executes(ctx -> success(ctx.getSource(),
                "Custom currencies: " + String.join(", ", AttributeDefinitions.customPoints()))));
        for (String operation : new String[]{"get", "set", "add", "remove"}) {
            group.then(Commands.literal(operation).then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("currency", StringArgumentType.word())
                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(AttributeDefinitions.customPoints(), builder))
                            .executes(ctx -> operation.equals("get") ? points(ctx.getSource(),
                                    EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "currency"), operation, 0) : 0)
                            .then(Commands.argument("amount", IntegerArgumentType.integer(0)).executes(ctx ->
                                    points(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                            StringArgumentType.getString(ctx, "currency"), operation,
                                            IntegerArgumentType.getInteger(ctx, "amount")))))));
        }
        if (name.equals("currency")) group.then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("currency", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(AttributeDefinitions.customPoints(), builder))
                        .executes(ctx -> points(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                StringArgumentType.getString(ctx, "currency"), "get", 0))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0)).executes(ctx ->
                                points(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                        StringArgumentType.getString(ctx, "currency"), "set",
                                        IntegerArgumentType.getInteger(ctx, "amount"))))));
        return group;
    }

    private static int openSelf(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return open(source, source.getPlayerOrException(), "view");
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("/statlog opens your menu; /statlog open <player> opens theirs"), false);
        if (source.hasPermission(2)) {
            source.sendSuccess(() -> Component.literal("/statlog stat list | <player> <key> [level] | get/set/add/remove <player> <key> [amount]"), false);
            source.sendSuccess(() -> Component.literal("/statlog currency list | <player> <id> [amount] | get/set/add/remove <player> <id> [amount]"), false);
            source.sendSuccess(() -> Component.literal("/statlog xp|level <player> [amount]; editAccess, respecAccess, respec, reset, reload, op open"), false);
        }
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> numberCommands(String kind) {
        var group = Commands.literal(kind).requires(source -> source.hasPermission(2));
        group.then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> number(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), kind, "get", 0))
                .then(Commands.argument("amount", IntegerArgumentType.integer(0)).executes(ctx ->
                        number(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), kind, "set",
                                IntegerArgumentType.getInteger(ctx, "amount")))));
        for (String operation : new String[]{"get", "set", "add", "remove"}) {
            group.then(Commands.literal(operation).then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> operation.equals("get") ? number(ctx.getSource(),
                            EntityArgument.getPlayer(ctx, "player"), kind, operation, 0) : 0)
                    .then(Commands.argument("amount", IntegerArgumentType.integer(0)).executes(ctx ->
                            number(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), kind, operation,
                                    IntegerArgumentType.getInteger(ctx, "amount"))))));
        }
        return group;
    }
    private static LiteralArgumentBuilder<CommandSourceStack> permissionCommands(String kind) {
        return Commands.literal(kind + "Access").requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.literal("allow").executes(ctx -> permission(ctx.getSource(),
                        EntityArgument.getPlayer(ctx, "player"), kind, true)))
                .then(Commands.literal("deny").executes(ctx -> permission(ctx.getSource(),
                        EntityArgument.getPlayer(ctx, "player"), kind, false))));
    }
    private static int permission(CommandSourceStack source, ServerPlayer player, String kind, boolean allowed) {
        if (kind.equals("edit")) StatlogApi.allowEdit(player, allowed);
        else StatlogApi.allowRespec(player, allowed);
        return success(source, kind + " access for " + player.getName().getString() + ": " + allowed);
    }
    private static int open(CommandSourceStack source, ServerPlayer player, String mode) {
        return open(source, player, mode, "");
    }
    private static int open(CommandSourceStack source, ServerPlayer player, String mode, String tab) {
        if (!tab.isBlank() && !AttributeDefinitions.tabs().containsKey(tab)) return fail(source, "Unknown Statlog tab: " + tab);
        StatlogNetwork.open(player, mode, tab);
        return success(source, "Opened Statlog for " + player.getName().getString());
    }
    private static int remoteOpen(CommandSourceStack source, ServerPlayer target, String mode) {
        return remoteOpen(source, target, mode, "");
    }
    private static int remoteOpen(CommandSourceStack source, ServerPlayer target, String mode, String tab) {
        if (!tab.isBlank() && !AttributeDefinitions.tabs().containsKey(tab)) return fail(source, "Unknown Statlog tab: " + tab);
        try {
            ServerPlayer viewer = source.getPlayerOrException();
            StatlogNetwork.openRemote(viewer, target, mode, tab);
            return success(source, "Opened " + target.getName().getString() + "'s Statlog");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException error) {
            return fail(source, "This command must be run by a player");
        }
    }
    private static int attribute(CommandSourceStack source, ServerPlayer player, String id, String operation, int amount) {
        try {
            int current = StatlogApi.attributeLevel(player, id);
            int next = switch (operation) {
                case "set" -> amount;
                case "add" -> Math.addExact(current, amount);
                case "remove" -> Math.subtractExact(current, amount);
                default -> current;
            };
            if (!operation.equals("get")) StatlogApi.setAttributeLevel(player, id, next);
            return success(source, id + " level: " + next);
        } catch (IllegalArgumentException | ArithmeticException ex) { return fail(source, ex.getMessage()); }
    }
    private static int number(CommandSourceStack source, ServerPlayer player, String kind, String operation, int amount) {
        try {
            int current = kind.equals("xp") ? StatlogApi.xp(player) : StatlogApi.level(player);
            int next = switch (operation) {
                case "set" -> amount;
                case "add" -> Math.addExact(current, amount);
                case "remove" -> Math.subtractExact(current, amount);
                default -> current;
            };
            if (!operation.equals("get")) {
                if (kind.equals("xp")) StatlogApi.setXp(player, next);
                else StatlogApi.setLevel(player, next);
            }
            return success(source, kind + ": " + next);
        } catch (IllegalArgumentException | ArithmeticException ex) { return fail(source, ex.getMessage()); }
    }
    private static int points(CommandSourceStack source, ServerPlayer player, String currency, String operation, int amount) {
        try {
            int current = StatlogApi.points(player, currency);
            int next = switch (operation) {
                case "set" -> amount;
                case "add" -> Math.addExact(current, amount);
                case "remove" -> Math.subtractExact(current, amount);
                default -> current;
            };
            if (!operation.equals("get")) StatlogApi.setPoints(player, currency, next);
            Component message = switch (operation) {
                case "set" -> Component.translatable("statlog.command.currency.set", player.getName(), currency, next);
                case "add" -> Component.translatable("statlog.command.currency.add", amount, player.getName(), currency);
                case "remove" -> Component.translatable("statlog.command.currency.remove", amount, player.getName(), currency);
                default -> Component.translatable("statlog.command.currency.get", player.getName(), next, currency);
            };
            return success(source, message);
        } catch (IllegalArgumentException | ArithmeticException ex) { return fail(source, ex.getMessage()); }
    }
    private static int spawnOrb(CommandSourceStack source, String currency, int amount) {
        String id = currency.toUpperCase(java.util.Locale.ROOT);
        if (!AttributeDefinitions.customPoints().contains(id)) return fail(source, "Unknown Statlog currency: " + id);
        var position = source.getPosition();
        if (!source.getLevel().addFreshEntity(new CurrencyOrb(source.getLevel(),
                position.x, position.y, position.z, id, amount))) return fail(source, "Could not spawn currency orb");
        return success(source, Component.translatable("statlog.command.orb.spawn", amount, id));
    }
    private static int success(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }
    private static int success(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> message, false);
        return 1;
    }
    private static int fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message == null ? "Invalid value" : message));
        return 0;
    }
    private StatlogCommands() {}
}
