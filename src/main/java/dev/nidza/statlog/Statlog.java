package dev.nidza.statlog;

import dev.nidza.statlog.config.AttributeDefinitions;
import dev.nidza.statlog.config.StatlogConfig;
import dev.nidza.statlog.data.PlayerProgress;
import dev.nidza.statlog.network.StatlogNetwork;
import dev.nidza.statlog.server.StatlogCommands;
import dev.nidza.statlog.server.StatlogEvents;
import dev.nidza.statlog.server.ItemRules;
import dev.nidza.statlog.server.CurrencyOrb;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

@Mod(Statlog.ID)
public final class Statlog {
    public static final String ID = "statlog";
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ID);
    public static final Supplier<AttachmentType<PlayerProgress>> PROGRESS =
            ATTACHMENTS.register("progress", () -> AttachmentType.serializable(PlayerProgress::new).build());
    private static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(BuiltInRegistries.ATTRIBUTE, ID);
    public static final Holder<Attribute> EMPTY = ATTRIBUTES.register("empty",
            () -> new RangedAttribute("attribute.name.statlog.empty", 0, 0, 1024));
    public static final Holder<Attribute> TARGET_INVULNERABILITY_TICKS = ATTRIBUTES.register("target_invulnerability_ticks",
            () -> new RangedAttribute("attribute.name.statlog.target_invulnerability_ticks", 10, 1, 20).setSyncable(true));
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, ID);
    public static final Supplier<EntityType<CurrencyOrb>> CURRENCY_ORB = ENTITIES.register("currency_orb",
            () -> EntityType.Builder.<CurrencyOrb>of(CurrencyOrb::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(6).updateInterval(20).build("statlog:currency_orb"));

    public Statlog(IEventBus modBus, ModContainer container) {
        ATTACHMENTS.register(modBus);
        ATTRIBUTES.register(modBus);
        ENTITIES.register(modBus);
        modBus.addListener((EntityAttributeModificationEvent event) -> {
            event.add(EntityType.PLAYER, EMPTY);
            for (var type : event.getTypes())
                if (!event.has(type, TARGET_INVULNERABILITY_TICKS)) event.add(type, TARGET_INVULNERABILITY_TICKS);
        });
        modBus.addListener(StatlogNetwork::register);
        moveOldConfigs();
        container.registerConfig(ModConfig.Type.COMMON, StatlogConfig.COMMON_SPEC, "statlog/statlog-common.toml");
        container.registerConfig(ModConfig.Type.CLIENT, StatlogConfig.CLIENT_SPEC, "statlog/statlog-client.toml");
        NeoForge.EVENT_BUS.addListener(StatlogCommands::register);
        NeoForge.EVENT_BUS.register(StatlogEvents.class);
        AttributeDefinitions.load();
        ItemRules.load();
    }

    private static void moveOldConfigs() {
        Path root = FMLPaths.CONFIGDIR.get();
        Path folder = root.resolve(ID);
        try {
            Files.createDirectories(folder);
            copyDefault(folder.resolve("README.md"), "/default-statlog-guide.md");
            Files.createDirectories(folder.resolve("lang"));
            copyDefault(folder.resolve("lang/en_us.json"), "/assets/statlog/lang/en_us.json");
            try (DirectoryStream<Path> files = Files.newDirectoryStream(root, "statlog-*.toml*")) {
                for (Path oldFile : files) {
                    String name = oldFile.getFileName().toString();
                    if (!name.matches("statlog-(common|client|attributes)(-\\d+)?\\.toml(\\.bak)?")) continue;
                    Path newFile = folder.resolve(name);
                    int copy = 1;
                    while (Files.exists(newFile)) {
                        newFile = folder.resolve(name + ".migrated-" + copy++);
                    }
                    if (Files.isRegularFile(oldFile)) {
                        Files.move(oldFile, newFile);
                    }
                }
            }
        } catch (IOException error) {
            throw new IllegalStateException("Could not move Statlog configs to " + folder, error);
        }
    }
    private static void copyDefault(Path target, String resource) throws IOException {
        if (Files.exists(target)) return;
        try (var source = Statlog.class.getResourceAsStream(resource)) {
            if (source == null) throw new IOException("Missing bundled resource " + resource);
            Files.copy(source, target);
        }
    }
}
