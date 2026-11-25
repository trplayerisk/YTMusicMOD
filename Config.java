package com.ytmusic;

import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.util.Properties;

public final class Config {
    public static float hudX = 0.02f, hudY = 0.05f, volume = 0.5f;
    public static boolean hudVisible = true;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("ytmusic.properties");
    }

    public static void load() {
        try (var r = Files.newBufferedReader(file())) {
            Properties p = new Properties();
            p.load(r);
            hudX = Float.parseFloat(p.getProperty("hudX", "0.02"));
            hudY = Float.parseFloat(p.getProperty("hudY", "0.05"));
            volume = Float.parseFloat(p.getProperty("volume", "0.5"));
            hudVisible = Boolean.parseBoolean(p.getProperty("hudVisible", "true"));
        } catch (Exception ignored) {}
    }

    public static void save() {
        try (var w = Files.newBufferedWriter(file())) {
            Properties p = new Properties();
            p.setProperty("hudX", String.valueOf(hudX));
            p.setProperty("hudY", String.valueOf(hudY));
            p.setProperty("volume", String.valueOf(volume));
            p.setProperty("hudVisible", String.valueOf(hudVisible));
            p.store(w, "YT Music HUD");
        } catch (Exception ignored) {}
    }
}
