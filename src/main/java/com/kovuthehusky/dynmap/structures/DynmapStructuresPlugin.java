package com.kovuthehusky.dynmap.structures;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Objects;
import org.bstats.bukkit.Metrics;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;

public class DynmapStructuresPlugin extends JavaPlugin implements Listener {
    private StructureMarkers markers;
    private ChunkScanQueue queue;
    private BukkitTask scanTask;
    private Metrics metrics;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration configuration = getConfig();
        if (!configuration.contains("layer.nolabels", true) && configuration.contains("layer.noLabels", true)) {
            configuration.set("layer.nolabels", configuration.getBoolean("layer.noLabels"));
        }
        configuration.options().copyDefaults(true);
        saveConfig();
        try {
            Plugin dynmap = getServer().getPluginManager().getPlugin("dynmap");
            if (!(dynmap instanceof DynmapCommonAPI common) || !getServer().getPluginManager().isPluginEnabled("dynmap")) {
                throw new IllegalStateException("A compatible enabled Dynmap plugin is required");
            }
            MarkerAPI api = Objects.requireNonNull(common.getMarkerAPI(), "Dynmap marker API is unavailable");
            String label = configuration.getString("layer.name", "Structures");
            String layerId = label.toLowerCase(Locale.ROOT);
            MarkerSet set = api.getMarkerSet(layerId);
            if (set == null) {
                set = api.createMarkerSet(layerId, label, null, true);
            }
            Objects.requireNonNull(set, "Dynmap marker layer could not be created");
            set.setHideByDefault(configuration.getBoolean("layer.hidebydefault"));
            set.setLayerPriority(configuration.getInt("layer.layerprio"));
            set.setMinZoom(Math.max(0, configuration.getInt("layer.minzoom")));
            registerIcons(api);
            markers = new StructureMarkers(api, set, configuration);
            markers.migrateLegacyMarkers();
            queue = new ChunkScanQueue(8, this::scanChunk);
            for (World world : getServer().getWorlds()) {
                for (Chunk chunk : world.getLoadedChunks()) {
                    queue.enqueue(chunk);
                }
            }
            scanTask = getServer().getScheduler().runTaskTimer(this, queue::processBatch, 1L, 1L);
            getServer().getPluginManager().registerEvents(this, this);
            metrics = new Metrics(this, 605);
        } catch (IOException | RuntimeException exception) {
            getLogger().severe("Could not enable structure markers: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void registerIcons(MarkerAPI api) throws IOException {
        for (StructureMarkers.Category category : StructureMarkers.Category.values()) {
            try (InputStream image = getClass().getResourceAsStream("/" + category.resource + ".png")) {
                if (image == null) {
                    throw new IOException("Missing icon resource for " + category.id);
                }
                String iconId = "structures." + category.id;
                MarkerIcon icon = api.getMarkerIcon(iconId);
                if (icon == null) {
                    icon = api.createMarkerIcon(iconId, category.label, image);
                    if (icon == null) {
                        throw new IllegalStateException("Could not register icon " + iconId);
                    }
                } else {
                    icon.setMarkerIconImage(image);
                }
            }
        }
    }

    private void scanChunk(Chunk chunk) {
        try {
            markers.scan(chunk);
        } catch (RuntimeException exception) {
            getLogger().warning("Skipping structures in " + chunk.getWorld().getName() + " ["
                + chunk.getX() + "," + chunk.getZ() + "]: " + exception.getMessage());
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (queue != null) {
            queue.enqueue(event.getChunk());
        }
    }

    @Override
    public void onDisable() {
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
        if (queue != null) {
            queue.clear();
            queue = null;
        }
        if (metrics != null) {
            metrics.shutdown();
            metrics = null;
        }
    }
}