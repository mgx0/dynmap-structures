package com.kovuthehusky.dynmap.structures;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Chunk;

final class ChunkScanQueue {
    private record ChunkKey(UUID world, int chunkX, int chunkZ) {}

    private final Map<ChunkKey, Chunk> pending = new LinkedHashMap<>();
    private final int budget;
    private final Consumer<Chunk> scanner;

    ChunkScanQueue(int budget, Consumer<Chunk> scanner) {
        if (budget <= 0) {
            throw new IllegalArgumentException("Chunk budget must be positive");
        }
        this.budget = budget;
        this.scanner = Objects.requireNonNull(scanner);
    }

    void enqueue(Chunk chunk) {
        pending.putIfAbsent(new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ()), chunk);
    }

    void processBatch() {
        for (int processed = 0; processed < budget && !pending.isEmpty(); processed++) {
            var iterator = pending.values().iterator();
            Chunk chunk = iterator.next();
            iterator.remove();
            if (chunk.isLoaded()) {
                scanner.accept(chunk);
            }
        }
    }

    void clear() {
        pending.clear();
    }
}