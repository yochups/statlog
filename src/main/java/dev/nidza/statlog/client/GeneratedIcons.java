package dev.nidza.statlog.client;

import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.platform.NativeImage;
import dev.nidza.statlog.config.AttributeDefinition;
import dev.nidza.statlog.config.AttributeTab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class GeneratedIcons {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String[] DEFAULTS = {"health", "strength", "speed", "stamina", "defense",
            "attack_speed", "knockback", "luck", "toughness", "mining_speed", "regeneration"};
    private static final Path ROOT = FMLPaths.CONFIGDIR.get().resolve("statlog/icons");
    private static final Map<ResourceLocation, Long> LOADED = new HashMap<>();

    public static void resourceReload() {
        LOADED.clear();
        reload();
    }

    public static void reload() {
        try {
            Files.createDirectories(ROOT);
            Path metadata = ROOT.resolve("pack.mcmeta");
            if (Files.notExists(metadata)) Files.writeString(metadata,
                    "{\"pack\":{\"pack_format\":34,\"description\":\"Statlog generated icons\"}}\n");
            for (AttributeDefinition def : ClientState.definitions().values()) {
                Path base = path(def.icon());
                if (base == null) continue;
                ensure(base, "attributes", base.getFileName().toString(), false);
                load(def.icon(), base);
                if (def.maxIcon()) {
                    String maxIcon = def.displayedIcon(def.maxLevel());
                    Path max = path(maxIcon);
                    if (max != null) {
                        if (Files.notExists(max)) {
                            Files.createDirectories(max.getParent());
                            if (def.maxIconSepia()) sepia(base, max);
                            else Files.copy(base, max);
                        }
                        load(maxIcon, max);
                    }
                }
            }
            List<AttributeTab> tabs = ClientState.tabs();
            for (int i = 0; i < tabs.size(); i++) {
                AttributeTab tab = tabs.get(i);
                Path file = path(tab.texture());
                if (file == null) continue;
                ensure(file, i < 6 ? "tab_bottom" : "tab_top", file.getFileName().toString(), true);
                load(tab.texture(), file);
            }
            for (String id : ClientState.customPoints()) {
                String icon = ClientState.pointIcon(id);
                Path iconFile = path(icon);
                if (iconFile != null) {
                    ensure(iconFile, "currency", iconFile.getFileName().toString(), false);
                    load(icon, iconFile);
                }
                String orb = ClientState.pointOrbTexture(id);
                Path orbFile = path(orb);
                if (orbFile != null) {
                    ensure(orbFile, "orb", orbFile.getFileName().toString(), false);
                    load(orb, orbFile);
                }
            }
        } catch (IOException error) {
            LOGGER.error("Could not prepare Statlog icon files in {}", ROOT, error);
        }
    }

    public static ResourceLocation texture(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) return null;
        if (Minecraft.getInstance().getResourceManager().getResource(key).isPresent()) return key;
        ResourceLocation generated = generatedKey(key);
        return LOADED.containsKey(generated) ? generated : key;
    }

    private static Path path(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !key.getNamespace().equals("statlog")
                || !key.getPath().matches("textures/(gui/(attributes|tabs|currencies)|entity/currency_orbs)/[a-z0-9_.-]+\\.png")) return null;
        return ROOT.resolve("assets/statlog").resolve(key.getPath());
    }

    private static void ensure(Path file, String placeholder, String name, boolean tab) throws IOException {
        if (Files.exists(file)) return;
        Files.createDirectories(file.getParent());
        String source = tab ? placeholder + ".png" : name;
        if (!tab && !List.of(DEFAULTS).contains(name.replace(".png", "")))
            source = DEFAULTS[ThreadLocalRandom.current().nextInt(DEFAULTS.length)] + ".png";
        String resource = placeholder.equals("currency") || placeholder.equals("orb")
                ? "/assets/statlog/textures/gui/statlog_currency.png"
                : "/assets/statlog/icon_placeholders/" + (tab ? "tabs/" : "attributes/") + source;
        try (InputStream in = GeneratedIcons.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException("Missing placeholder " + resource);
            Files.copy(in, file);
        }
    }

    private static void sepia(Path source, Path target) throws IOException {
        BufferedImage image = ImageIO.read(source.toFile());
        if (image == null) throw new IOException("Invalid PNG " + source);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int pixel = image.getRGB(x, y);
            int r = pixel >> 16 & 255, g = pixel >> 8 & 255, b = pixel & 255;
            int sr = Math.min(255, (393 * r + 769 * g + 189 * b) / 1000);
            int sg = Math.min(255, (349 * r + 686 * g + 168 * b) / 1000);
            int sb = Math.min(255, (272 * r + 534 * g + 131 * b) / 1000);
            image.setRGB(x, y, pixel & 0xFF000000 | sr << 16 | sg << 8 | sb);
        }
        ImageIO.write(image, "png", target.toFile());
    }

    private static void load(String id, Path file) throws IOException {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) return;
        ResourceLocation generated = generatedKey(key);
        long modified = Files.getLastModifiedTime(file).toMillis();
        if (LOADED.getOrDefault(generated, -1L) == modified) return;
        try (InputStream in = Files.newInputStream(file)) {
            Minecraft.getInstance().getTextureManager().register(generated, new DynamicTexture(NativeImage.read(in)));
            LOADED.put(generated, modified);
        } catch (Exception error) {
            LOGGER.warn("Could not load Statlog icon {}", file, error);
        }
    }

    private static ResourceLocation generatedKey(ResourceLocation key) {
        return ResourceLocation.fromNamespaceAndPath("statlog", "generated/" + key.getPath());
    }

    private GeneratedIcons() {}
}
