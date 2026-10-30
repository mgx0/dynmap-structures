package com.kovuthehusky.dynmap.structures;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bstats.bukkit.Metrics;
import org.bukkit.Chunk;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DynmapStructuresPluginTest {
    private final Server server = mock(Server.class);
    private final PluginManager manager = mock(PluginManager.class);
    private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    private final BukkitTask task = mock(BukkitTask.class);
    private final MarkerAPI api = mock(MarkerAPI.class);
    private final MarkerSet set = mock(MarkerSet.class);
    private final World world = mock(World.class);
    private final Chunk chunk = mock(Chunk.class);
    private final YamlConfiguration config = new YamlConfiguration();
    private DynmapStructuresPlugin plugin;
    private MockedConstruction<Metrics> metrics;

    @BeforeEach
    void setup() throws Exception {
        metrics = mockConstruction(Metrics.class);
        plugin = mock(DynmapStructuresPlugin.class, CALLS_REAL_METHODS);
        try (var reader = new InputStreamReader(getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)) {
            config.load(reader);
        }
        doReturn(server).when(plugin).getServer();
        doReturn(config).when(plugin).getConfig();
        doReturn(Logger.getLogger("dynmap-structures-test")).when(plugin).getLogger();
        doNothing().when(plugin).saveDefaultConfig();
        doNothing().when(plugin).saveConfig();
        when(server.getPluginManager()).thenReturn(manager);
        when(server.getScheduler()).thenReturn(scheduler);
        Plugin dynmap = mock(Plugin.class, withSettings().extraInterfaces(DynmapCommonAPI.class));
        when(manager.getPlugin("dynmap")).thenReturn(dynmap);
        when(manager.isPluginEnabled("dynmap")).thenReturn(true);
        when(((DynmapCommonAPI) dynmap).getMarkerAPI()).thenReturn(api);
        when(api.getMarkerSet("structures")).thenReturn(set);
        when(api.getMarkerIcon(anyString())).thenReturn(mock(MarkerIcon.class));
        when(set.getMarkers()).thenReturn(Set.of());
        when(server.getWorlds()).thenReturn(List.of(world));
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(world.getLoadedChunks()).thenReturn(new Chunk[]{chunk});
        when(chunk.getWorld()).thenReturn(world);
        when(chunk.isLoaded()).thenReturn(true);
        when(chunk.getStructures()).thenReturn(List.of());
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L))).thenReturn(task);
    }

    @AfterEach
    void teardown() {
        if (metrics != null) {
            metrics.close();
        }
    }

    @Test
    void scansAlreadyLoadedChunksAndFutureLoadsWithoutGeneratingTerrain() {
        plugin.onEnable();
        Runnable batch = batch();
        verify(chunk, never()).getStructures();
        batch.run();
        verify(chunk).getStructures();
        plugin.onChunkLoad(new ChunkLoadEvent(chunk, false));
        batch.run();
        verify(chunk, times(2)).getStructures();
        verify(chunk, never()).load();
        verify(world, never()).canGenerateStructures();
        verify(manager).registerEvents(plugin, plugin);
    }

    @Test
    void cancelsWorkAndClearsPendingChunksOnDisable() {
        plugin.onEnable();
        Runnable batch = batch();
        plugin.onDisable();
        batch.run();
        verify(task).cancel();
        verify(chunk, never()).getStructures();
    }

    @Test
    void disablesCleanlyWhenMarkerApiIsUnavailable() {
        Plugin dynmap = manager.getPlugin("dynmap");
        when(((DynmapCommonAPI) dynmap).getMarkerAPI()).thenReturn(null);
        plugin.onEnable();
        verify(manager).disablePlugin(plugin);
        verify(manager, never()).registerEvents(plugin, plugin);
        verify(scheduler, never()).runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong());
    }

    @Test
    void disablesCleanlyWhenLayerCreationFails() {
        when(api.getMarkerSet("structures")).thenReturn(null);
        plugin.onEnable();
        verify(manager).disablePlugin(plugin);
        verify(manager, never()).registerEvents(plugin, plugin);
    }

    @Test
    void preservesCanonicalAndLegacyNoLabelSettings() {
        config.set("layer.nolabels", null);
        config.set("layer.noLabels", true);
        plugin.onEnable();
        assertTrue(config.getBoolean("layer.nolabels"));
        verify(set).setHideByDefault(false);
        verify(set).setLayerPriority(10);
        verify(set).setMinZoom(0);
    }

    @Test
    void disablesCleanlyWhenRequiredIconsCannotBeRegistered() {
        when(api.getMarkerIcon(anyString())).thenReturn(null);
        plugin.onEnable();
        verify(manager).disablePlugin(plugin);
        verify(manager, never()).registerEvents(plugin, plugin);
    }

    private Runnable batch() {
        var runnable = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskTimer(eq(plugin), runnable.capture(), eq(1L), eq(1L));
        return runnable.getValue();
    }
}