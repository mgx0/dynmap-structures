package com.kovuthehusky.dynmap.structures;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChunkScanQueueTest {
    private final List<Chunk> scanned = new ArrayList<>();
    private final ChunkScanQueue queue = new ChunkScanQueue(2, scanned::add);
    private final UUID worldId = UUID.randomUUID();

    @Test
    void deduplicatesWorldAndChunkCoordinates() {
        Chunk first = chunk(worldId, 10, -5, true);
        queue.enqueue(first);
        queue.enqueue(first);
        queue.enqueue(chunk(worldId, 10, -5, true));
        queue.processBatch();
        assertEquals(1, scanned.size());
    }

    @Test
    void keepsSameCoordinatesInDifferentWorlds() {
        queue.enqueue(chunk(worldId, 10, 20, true));
        queue.enqueue(chunk(UUID.randomUUID(), 10, 20, true));
        queue.processBatch();
        assertEquals(2, scanned.size());
    }

    @Test
    void limitsWorkPerBatchAndRetainsPendingChunks() {
        for (int coordinate = 0; coordinate < 5; coordinate++) {
            queue.enqueue(chunk(worldId, coordinate, 0, true));
        }
        queue.processBatch();
        assertEquals(2, scanned.size());
        queue.processBatch();
        assertEquals(4, scanned.size());
        queue.processBatch();
        assertEquals(5, scanned.size());
    }

    @Test
    void unloadedEntriesCountAgainstTheBudget() {
        queue.enqueue(chunk(worldId, 0, 0, false));
        queue.enqueue(chunk(worldId, 1, 0, false));
        queue.enqueue(chunk(worldId, 2, 0, true));
        queue.processBatch();
        assertTrue(scanned.isEmpty());
        queue.processBatch();
        assertEquals(1, scanned.size());
    }

    @Test
    void doesNotScanOrReloadChunksThatUnloadWhileQueued() {
        Chunk chunk = chunk(worldId, 0, 0, true);
        queue.enqueue(chunk);
        when(chunk.isLoaded()).thenReturn(false);
        queue.processBatch();
        assertTrue(scanned.isEmpty());
        verify(chunk, never()).load();
        verify(chunk, never()).getStructures();
    }

    @Test
    void permitsRescanAfterProcessingAndClearsPendingWork() {
        Chunk chunk = chunk(worldId, 0, 0, true);
        queue.enqueue(chunk);
        queue.processBatch();
        queue.enqueue(chunk);
        queue.processBatch();
        assertEquals(2, scanned.size());
        queue.enqueue(chunk);
        queue.clear();
        queue.processBatch();
        assertEquals(2, scanned.size());
    }

    @Test
    void rejectsNonPositiveBudgets() {
        assertThrows(IllegalArgumentException.class, () -> new ChunkScanQueue(0, scanned::add));
        assertThrows(IllegalArgumentException.class, () -> new ChunkScanQueue(-1, scanned::add));
    }

    private Chunk chunk(UUID id, int chunkX, int chunkZ, boolean loaded) {
        World world = mock(World.class);
        when(world.getUID()).thenReturn(id);
        Chunk chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(world);
        when(chunk.getX()).thenReturn(chunkX);
        when(chunk.getZ()).thenReturn(chunkZ);
        when(chunk.isLoaded()).thenReturn(loaded);
        return chunk;
    }
}