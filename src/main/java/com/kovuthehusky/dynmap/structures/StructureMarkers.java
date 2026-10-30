package com.kovuthehusky.dynmap.structures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.util.BoundingBox;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;

final class StructureMarkers {
    enum Category {
        BASTION("bastionremnant", "Bastion Remnant", "bastionremnant", "bastion_remnant"),
        TREASURE("buriedtreasure", "Buried Treasure", "buriedtreasure", "buried_treasure"),
        DESERT("desertpyramid", "Desert Pyramid", "desertpyramid", "desert_pyramid"),
        END("endcity", "End City", "endcity", "end_city"),
        FORTRESS("fortress", "Nether Fortress", "fortress", "fortress"),
        IGLOO("igloo", "Igloo", "igloo", "igloo"),
        JUNGLE("junglepyramid", "Jungle Pyramid", "junglepyramid", "jungle_pyramid"),
        MANSION("mansion", "Woodland Mansion", "mansion", "mansion"),
        MINESHAFT("mineshaft", "Mineshaft", "mineshaft", "mineshaft", "mineshaft_mesa"),
        MONUMENT("monument", "Ocean Monument", "monument", "monument"),
        FOSSIL("netherfossil", "Nether Fossil", "netherfossil", "nether_fossil"),
        RUIN("oceanruin", "Ocean Ruins", "oceanruin", "ocean_ruin_cold", "ocean_ruin_warm"),
        OUTPOST("pillageroutpost", "Pillager Outpost", "pillageroutpost", "pillager_outpost"),
        PORTAL("ruinedportal", "Ruined Portal", "ruinedportal", "ruined_portal", "ruined_portal_desert",
            "ruined_portal_jungle", "ruined_portal_swamp", "ruined_portal_mountain", "ruined_portal_ocean", "ruined_portal_nether"),
        SHIPWRECK("shipwreck", "Shipwreck", "shipwreck", "shipwreck", "shipwreck_beached"),
        STRONGHOLD("stronghold", "Stronghold", "stronghold", "stronghold"),
        HUT("swamphut", "Swamp Hut", "swamphut", "swamp_hut"),
        VILLAGE("village", "Village", "village", "village_plains", "village_desert", "village_savanna", "village_snowy", "village_taiga"),
        ANCIENT_CITY("ancientcity", "Ancient City", "mansion", "ancient_city"),
        TRIAL_CHAMBERS("trialchambers", "Trial Chambers", "fortress", "trial_chambers");

        final String id;
        final String label;
        final String resource;
        final String[] keys;
        final Pattern legacyId;

        Category(String id, String label, String resource, String... keys) {
            this.id = id;
            this.label = label;
            this.resource = resource;
            this.keys = keys;
            this.legacyId = Pattern.compile(Pattern.quote(id) + ",-?\\d+,-?\\d+");
        }
    }

    private static final String PREFIX = "structures-v2,";
    private static final Map<String, Category> CATEGORIES = new HashMap<>();

    static {
        for (Category category : Category.values()) {
            for (String key : category.keys) {
                CATEGORIES.put(key, category);
            }
        }
    }

    private record Settings(boolean enabled, String label, MarkerIcon icon) {}

    private final MarkerSet set;
    private final Map<Category, Settings> settings = new HashMap<>();
    private final boolean noLabels;
    private final boolean includeCoordinates;

    StructureMarkers(MarkerAPI api, MarkerSet set, FileConfiguration config) {
        Objects.requireNonNull(api, "Dynmap marker API is unavailable");
        this.set = Objects.requireNonNull(set, "Dynmap marker layer is unavailable");
        noLabels = config.getBoolean("layer.nolabels", config.getBoolean("layer.noLabels"));
        includeCoordinates = config.getBoolean("layer.inc-coord");
        for (Category category : Category.values()) {
            boolean enabled = config.getBoolean("structures." + category.id);
            MarkerIcon icon = api.getMarkerIcon("structures." + category.id);
            if (enabled && icon == null) {
                throw new IllegalStateException("Missing marker icon for " + category.id);
            }
            settings.put(category, new Settings(enabled, config.getString("labels." + category.id, category.label), icon));
        }
    }

    static Category categoryFor(NamespacedKey key) {
        return key.getNamespace().equals(NamespacedKey.MINECRAFT) ? CATEGORIES.get(key.getKey()) : null;
    }

    void scan(Chunk chunk) {
        World world = chunk.getWorld();
        for (GeneratedStructure structure : chunk.getStructures()) {
            mark(world, structure.getStructure().getKeyOrThrow(), structure.getBoundingBox());
        }
    }

    void mark(World world, NamespacedKey key, BoundingBox bounds) {
        Category category = categoryFor(key);
        if (category == null || !settings.get(category).enabled()) {
            return;
        }
        Settings options = settings.get(category);
        String id = PREFIX + category.id + "," + world.getUID() + "," + key + ","
            + bounds.getMinX() + "," + bounds.getMinY() + "," + bounds.getMinZ() + ","
            + bounds.getMaxX() + "," + bounds.getMaxY() + "," + bounds.getMaxZ();
        double centerX = bounds.getCenterX();
        double centerY = bounds.getCenterY();
        double centerZ = bounds.getCenterZ();
        String label = noLabels ? "" : options.label();
        if (!noLabels && includeCoordinates) {
            label += " [" + (int) Math.floor(centerX) + "," + (int) Math.floor(centerZ) + "]";
        }
        Marker marker = set.findMarker(id);
        if (marker == null) {
            if (set.createMarker(id, label, world.getName(), centerX, centerY, centerZ, options.icon(), true) == null) {
                throw new IllegalStateException("Dynmap could not create marker " + id);
            }
        } else {
            if (!Objects.equals(marker.getLabel(), label)) {
                marker.setLabel(label);
            }
            if (!Objects.equals(marker.getWorld(), world.getName()) || marker.getX() != centerX
                || marker.getY() != centerY || marker.getZ() != centerZ) {
                marker.setLocation(world.getName(), centerX, centerY, centerZ);
            }
            if (!Objects.equals(marker.getMarkerIcon(), options.icon())) {
                marker.setMarkerIcon(options.icon());
            }
        }
    }

    void migrateLegacyMarkers() {
        for (Marker marker : new ArrayList<>(set.getMarkers())) {
            String id = marker.getMarkerID();
            for (Category category : Category.values()) {
                boolean legacyCategory = category != Category.ANCIENT_CITY && category != Category.TRIAL_CHAMBERS;
                if ((legacyCategory && category.legacyId.matcher(id).matches())
                    || (!settings.get(category).enabled() && id.startsWith(PREFIX + category.id + ","))) {
                    marker.deleteMarker();
                    break;
                }
            }
        }
    }
}