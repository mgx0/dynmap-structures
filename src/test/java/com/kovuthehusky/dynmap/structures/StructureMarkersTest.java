package com.kovuthehusky.dynmap.structures;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.BoundingBox;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StructureMarkersTest {
    private final MarkerAPI api = mock(MarkerAPI.class);
    private final MarkerSet set = mock(MarkerSet.class);
    private final MarkerIcon icon = mock(MarkerIcon.class);
    private final World world = mock(World.class);
    private final BoundingBox bounds = new BoundingBox(-32, -40, 16, 16, 8, 64);
    private final YamlConfiguration config = new YamlConfiguration();

    @BeforeEach
    void setup() throws Exception {
        try (var reader = new InputStreamReader(getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)) {
            config.load(reader);
        }
        when(api.getMarkerIcon(anyString())).thenReturn(icon);
        when(set.createMarker(anyString(), anyString(), anyString(), anyDouble(), anyDouble(), anyDouble(), any(), anyBoolean()))
            .thenReturn(mock(Marker.class));
        when(world.getUID()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        when(world.getName()).thenReturn("world");
        when(set.getMarkers()).thenReturn(Set.of());
    }

    @ParameterizedTest
    @CsvSource({"village_plains,village", "village_desert,village", "village_savanna,village",
        "village_snowy,village", "village_taiga,village", "mineshaft_mesa,mineshaft",
        "shipwreck_beached,shipwreck", "ocean_ruin_cold,oceanruin", "ocean_ruin_warm,oceanruin",
        "ruined_portal_nether,ruinedportal", "ruined_portal_mountain,ruinedportal",
        "ancient_city,ancientcity", "trial_chambers,trialchambers", "stronghold,stronghold"})
    void groupsModernVariants(String key, String category) {
        assertEquals(category, StructureMarkers.categoryFor(NamespacedKey.minecraft(key)).id);
    }

    @Test
    void ignoresUnknownAndCustomStructures() {
        assertNull(StructureMarkers.categoryFor(NamespacedKey.minecraft("unknown")));
        assertNull(StructureMarkers.categoryFor(new NamespacedKey("custom", "stronghold")));
        var markers = new StructureMarkers(api, set, config);
        markers.mark(world, NamespacedKey.minecraft("unknown"), bounds);
        verify(set, never()).createMarker(anyString(), anyString(), anyString(), anyDouble(), anyDouble(), anyDouble(), any(), anyBoolean());
    }

    @Test
    void createsPersistentMarkerAtBoundingBoxCenter() {
        new StructureMarkers(api, set, config).mark(world, NamespacedKey.minecraft("stronghold"), bounds);
        verify(set).createMarker(anyString(), eq("Stronghold"), eq("world"), eq(-8.0), eq(-16.0), eq(40.0), eq(icon), eq(true));
    }

    @Test
    void idsAreStableAndIncludeWorldTypeAndAllBounds() {
        var markers = new StructureMarkers(api, set, config);
        markers.mark(world, NamespacedKey.minecraft("stronghold"), bounds);
        markers.mark(world, NamespacedKey.minecraft("stronghold"), bounds.clone());
        World otherWorld = mock(World.class);
        when(otherWorld.getUID()).thenReturn(UUID.randomUUID());
        when(otherWorld.getName()).thenReturn("other");
        markers.mark(otherWorld, NamespacedKey.minecraft("stronghold"), bounds);
        markers.mark(world, NamespacedKey.minecraft("village_plains"), bounds);
        markers.mark(world, NamespacedKey.minecraft("stronghold"), new BoundingBox(-32, -39, 16, 16, 8, 64));
        var ids = ArgumentCaptor.forClass(String.class);
        verify(set, times(5)).createMarker(ids.capture(), anyString(), anyString(), anyDouble(), anyDouble(), anyDouble(), any(), eq(true));
        assertEquals(ids.getAllValues().get(0), ids.getAllValues().get(1));
        assertEquals(4, new HashSet<>(ids.getAllValues()).size());
    }

    @Test
    void updatesExistingMarkerRatherThanDuplicatingIt() {
        Marker existing = mock(Marker.class);
        when(set.findMarker(anyString())).thenReturn(existing);
        new StructureMarkers(api, set, config).mark(world, NamespacedKey.minecraft("stronghold"), bounds);
        verify(existing).setLabel("Stronghold");
        verify(existing).setLocation("world", -8, -16, 40);
        verify(existing).setMarkerIcon(icon);
        verify(set, never()).createMarker(anyString(), anyString(), anyString(), anyDouble(), anyDouble(), anyDouble(), any(), anyBoolean());
    }

    @Test
    void unchangedMarkersDoNotCauseRepeatedWrites() {
        Marker existing = mock(Marker.class);
        when(set.findMarker(anyString())).thenReturn(existing);
        when(existing.getLabel()).thenReturn("Stronghold");
        when(existing.getWorld()).thenReturn("world");
        when(existing.getX()).thenReturn(-8.0);
        when(existing.getY()).thenReturn(-16.0);
        when(existing.getZ()).thenReturn(40.0);
        when(existing.getMarkerIcon()).thenReturn(icon);
        var markers = new StructureMarkers(api, set, config);
        markers.mark(world, NamespacedKey.minecraft("stronghold"), bounds);
        markers.mark(world, NamespacedKey.minecraft("stronghold"), bounds);
        verify(existing, never()).setLabel(anyString());
        verify(existing, never()).setLocation(anyString(), anyDouble(), anyDouble(), anyDouble());
        verify(existing, never()).setMarkerIcon(any());
    }

    @Test
    void respectsDisabledCategoriesAndNewDefaults() {
        var markers = new StructureMarkers(api, set, config);
        markers.mark(world, NamespacedKey.minecraft("mineshaft_mesa"), bounds);
        markers.mark(world, NamespacedKey.minecraft("ancient_city"), bounds);
        markers.mark(world, NamespacedKey.minecraft("trial_chambers"), bounds);
        verify(set, times(2)).createMarker(anyString(), anyString(), anyString(), anyDouble(), anyDouble(), anyDouble(), any(), eq(true));
    }

    @Test
    void preservesCustomLabelsAndCoordinateOption() {
        config.set("labels.stronghold", "Portal");
        config.set("layer.inc-coord", true);
        new StructureMarkers(api, set, config).mark(world, NamespacedKey.minecraft("stronghold"), bounds);
        verify(set).createMarker(anyString(), eq("Portal [-8,40]"), anyString(), anyDouble(), anyDouble(), anyDouble(), any(), eq(true));
    }

    @Test
    void canonicalNoLabelsSettingHidesLabels() {
        config.set("layer.nolabels", true);
        config.set("layer.inc-coord", true);
        new StructureMarkers(api, set, config).mark(world, NamespacedKey.minecraft("stronghold"), bounds);
        verify(set).createMarker(anyString(), eq(""), anyString(), anyDouble(), anyDouble(), anyDouble(), any(), eq(true));
    }

    @Test
    void supportsLegacyNoLabelsSpelling() {
        config.set("layer.nolabels", null);
        config.set("layer.noLabels", true);
        new StructureMarkers(api, set, config).mark(world, NamespacedKey.minecraft("stronghold"), bounds);
        verify(set).createMarker(anyString(), eq(""), anyString(), anyDouble(), anyDouble(), anyDouble(), any(), eq(true));
    }

    @Test
    void migratesOnlyRecognizedLegacyIdsAndDisabledManagedMarkers() {
        Marker legacy = marker("stronghold,-100,20");
        Marker manual = marker("my-stronghold");
        Marker manualCoordinates = marker("custom,10,20");
        Marker modern = marker("structures-v2,stronghold,world,minecraft:stronghold,bounds");
        Marker disabled = marker("structures-v2,mineshaft,world,minecraft:mineshaft,bounds");
        when(set.getMarkers()).thenReturn(Set.of(legacy, manual, manualCoordinates, modern, disabled));
        new StructureMarkers(api, set, config).migrateLegacyMarkers();
        verify(legacy).deleteMarker();
        verify(disabled).deleteMarker();
        verify(manual, never()).deleteMarker();
        verify(manualCoordinates, never()).deleteMarker();
        verify(modern, never()).deleteMarker();
    }

    @Test
    void preservesLegacyShapedIdsForCategoriesTheOldPluginNeverSupported() {
        Marker ancientCity = marker("ancientcity,10,20");
        Marker trialChambers = marker("trialchambers,-10,-20");
        when(set.getMarkers()).thenReturn(Set.of(ancientCity, trialChambers));
        new StructureMarkers(api, set, config).migrateLegacyMarkers();
        verify(ancientCity, never()).deleteMarker();
        verify(trialChambers, never()).deleteMarker();
    }

    @Test
    void rejectsMissingMarkerApiOrIconsBeforeProcessing() {
        assertThrows(NullPointerException.class, () -> new StructureMarkers(null, set, config));
        assertThrows(NullPointerException.class, () -> new StructureMarkers(api, null, config));
        when(api.getMarkerIcon(anyString())).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> new StructureMarkers(api, set, config));
    }

    private Marker marker(String id) {
        Marker marker = mock(Marker.class);
        when(marker.getMarkerID()).thenReturn(id);
        return marker;
    }
}