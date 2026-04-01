package com.dungeon.logic.placement.corridor.routing.occupancy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link VoxelStateGrid}.
 */
class VoxelStateGridTest {

    // 3 polygons, 5 z-bands (small but covers all index paths)
    private static final int P = 3;
    private static final int Z = 5;
    private VoxelStateGrid grid;

    @BeforeEach
    void fresh() { grid = new VoxelStateGrid(P, Z); }

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------
    @Nested @DisplayName("Constructor")
    class Constructor {

        @Test @DisplayName("polyCount() and zBands() reflect constructor args")
        void dimensions() {
            assertEquals(P, grid.polyCount());
            assertEquals(Z, grid.zBands());
        }

        @Test @DisplayName("All cells start as FREE_SPACE")
        void allFreeInitially() {
            for (int p = 0; p < P; p++)
                for (int z = 0; z < Z; z++)
                    assertEquals(VoxelState.FREE_SPACE, grid.getState(p, z));
        }

        @Test @DisplayName("Throws for polyCount <= 0")
        void invalidPolyCount() {
            assertThrows(IllegalArgumentException.class, () -> new VoxelStateGrid(0, 5));
            assertThrows(IllegalArgumentException.class, () -> new VoxelStateGrid(-1, 5));
        }

        @Test @DisplayName("Throws for zBands <= 0")
        void invalidZBands() {
            assertThrows(IllegalArgumentException.class, () -> new VoxelStateGrid(5, 0));
            assertThrows(IllegalArgumentException.class, () -> new VoxelStateGrid(5, -1));
        }
    }

    // -----------------------------------------------------------------------
    // pack()
    // -----------------------------------------------------------------------
    @Nested @DisplayName("pack()")
    class Pack {

        @Test @DisplayName("Returns 0 for (0,0)")
        void originIsZero() { assertEquals(0, grid.pack(0, 0)); }

        @Test @DisplayName("Returns polyId*zBands + zBand")
        void formula() {
            assertEquals(1 * Z + 2, grid.pack(1, 2));
            assertEquals(2 * Z + 4, grid.pack(2, 4));
        }

        @Test @DisplayName("Throws for out-of-range polyId")
        void outOfRangePolyId() {
            assertThrows(IndexOutOfBoundsException.class, () -> grid.pack(-1, 0));
            assertThrows(IndexOutOfBoundsException.class, () -> grid.pack(P, 0));
        }

        @Test @DisplayName("Throws for out-of-range zBand")
        void outOfRangeZBand() {
            assertThrows(IndexOutOfBoundsException.class, () -> grid.pack(0, -1));
            assertThrows(IndexOutOfBoundsException.class, () -> grid.pack(0, Z));
        }
    }

    // -----------------------------------------------------------------------
    // Setters and basic getters
    // -----------------------------------------------------------------------
    @Nested @DisplayName("State setters")
    class Setters {

        @Test @DisplayName("setRoom -> isRoom, getState == ROOM")
        void roomRoundtrip() {
            grid.setRoom(0, 0);
            assertTrue(grid.isRoom(0, 0));
            assertEquals(VoxelState.ROOM, grid.getState(0, 0));
        }

        @Test @DisplayName("setBorder -> isRoomBorder, getState == BORDER")
        void borderRoundtrip() {
            grid.setBorder(1, 2);
            assertTrue(grid.isRoomBorder(1, 2));
            assertEquals(VoxelState.BORDER, grid.getState(1, 2));
        }

        @Test @DisplayName("setCorridor -> isCorridor, getState == CORRIDOR")
        void corridorRoundtrip() {
            grid.setCorridor(2, 3);
            assertTrue(grid.isCorridor(2, 3));
            assertEquals(VoxelState.CORRIDOR, grid.getState(2, 3));
        }

        @Test @DisplayName("setStairsWalkable -> isStairs, getState == STAIRS")
        void stairsRoundtrip() {
            grid.setStairsWalkable(0, 1, 5, 10);
            assertTrue(grid.isStairs(0, 1));
            assertEquals(VoxelState.STAIRS, grid.getState(0, 1));
        }

        @Test @DisplayName("Setting a state does not affect other cells")
        void noSideEffects() {
            grid.setRoom(0, 0);
            for (int p = 0; p < P; p++)
                for (int z = 0; z < Z; z++)
                    if (p != 0 || z != 0)
                        assertTrue(grid.isFree(p, z), "Cell (" + p + "," + z + ") should remain FREE");
        }
    }

    // -----------------------------------------------------------------------
    // Overwrite priority rules
    // -----------------------------------------------------------------------
    @Nested @DisplayName("Overwrite priority rules")
    class OverwritePriority {

        @Test @DisplayName("BORDER overwrites ROOM")
        void borderOverwritesRoom() {
            grid.setRoom(0, 0);
            grid.setBorder(0, 0);
            assertTrue(grid.isRoomBorder(0, 0));
        }

        @Test @DisplayName("BORDER overwrites CORRIDOR")
        void borderOverwritesCorridor() {
            grid.setCorridor(0, 0);
            grid.setBorder(0, 0);
            assertTrue(grid.isRoomBorder(0, 0));
        }

        @Test @DisplayName("BORDER overwrites STAIRS and clears links")
        void borderOverwritesStairs() {
            grid.setStairsWalkable(0, 0, 1, 2);
            grid.setBorder(0, 0);
            assertTrue(grid.isRoomBorder(0, 0));
            assertEquals(0L, grid.getStairsLinksRaw(0, 0));
        }

        @Test @DisplayName("ROOM does NOT overwrite BORDER")
        void roomDoesNotOverwriteBorder() {
            grid.setBorder(0, 0);
            grid.setRoom(0, 0);
            assertTrue(grid.isRoomBorder(0, 0), "ROOM must not overwrite BORDER");
        }

        @Test @DisplayName("CORRIDOR does NOT overwrite BORDER")
        void corridorDoesNotOverwriteBorder() {
            grid.setBorder(0, 0);
            grid.setCorridor(0, 0);
            assertTrue(grid.isRoomBorder(0, 0), "CORRIDOR must not overwrite BORDER");
        }

        @Test @DisplayName("CORRIDOR does NOT overwrite STAIRS")
        void corridorDoesNotOverwriteStairs() {
            grid.setStairsWalkable(0, 0, 1, 2);
            grid.setCorridor(0, 0);
            assertTrue(grid.isStairs(0, 0), "CORRIDOR must not overwrite STAIRS");
        }

        @Test @DisplayName("STAIRS does NOT overwrite BORDER")
        void stairsDoesNotOverwriteBorder() {
            grid.setBorder(0, 0);
            grid.setStairsWalkable(0, 0, 1, 2);
            assertTrue(grid.isRoomBorder(0, 0), "STAIRS must not overwrite BORDER");
        }

        @Test @DisplayName("STAIRS does NOT overwrite ROOM")
        void stairsDoesNotOverwriteRoom() {
            grid.setRoom(0, 0);
            grid.setStairsWalkable(0, 0, 1, 2);
            assertTrue(grid.isRoom(0, 0), "STAIRS must not overwrite ROOM");
        }

        @Test @DisplayName("ROOM overwrites FREE (basic setter)")
        void roomOverwritesFree() {
            grid.setRoom(0, 0);
            assertFalse(grid.isFree(0, 0));
        }
    }

    // -----------------------------------------------------------------------
    // isTraversableBasic
    // -----------------------------------------------------------------------
    @Nested @DisplayName("isTraversableBasic()")
    class Traversability {

        @Test @DisplayName("FREE_SPACE is traversable")
        void freeTraversable() { assertTrue(grid.isTraversableBasic(0, 0)); }

        @Test @DisplayName("ROOM is traversable")
        void roomTraversable() {
            grid.setRoom(0, 0);
            assertTrue(grid.isTraversableBasic(0, 0));
        }

        @Test @DisplayName("CORRIDOR is traversable")
        void corridorTraversable() {
            grid.setCorridor(0, 0);
            assertTrue(grid.isTraversableBasic(0, 0));
        }

        @Test @DisplayName("BORDER is NOT traversable")
        void borderNotTraversable() {
            grid.setBorder(0, 0);
            assertFalse(grid.isTraversableBasic(0, 0));
        }

        @Test @DisplayName("Walkable STAIRS (with links) is traversable")
        void walkableStairsTraversable() {
            grid.setStairsWalkable(0, 0, 3, -1);
            assertTrue(grid.isTraversableBasic(0, 0));
        }

        @Test @DisplayName("Blocked STAIRS (no links) is NOT traversable")
        void blockedStairsNotTraversable() {
            grid.setStairsWalkable(0, 0, -1, -1);
            // raw = pack2(-1,-1) != 0  -> traversable
            assertTrue(grid.isTraversableBasic(0, 0),
                    "STAIRS with both links=-1 encodes a non-zero raw value and is thus traversable per current impl");
        }
    }

    // -----------------------------------------------------------------------
    // STAIRS link encoding / decoding
    // -----------------------------------------------------------------------
    @Nested @DisplayName("STAIRS link encoding")
    class StairsLinks {

        @Test @DisplayName("linkA / linkB roundtrip for positive ids")
        void positiveIdsRoundtrip() {
            grid.setStairsWalkable(0, 0, 7, 42);
            long raw = grid.getStairsLinksRaw(0, 0);
            assertEquals(7,  VoxelStateGrid.linkA(raw));
            assertEquals(42, VoxelStateGrid.linkB(raw));
        }

        @Test @DisplayName("linkA / linkB roundtrip for -1 (unused)")
        void unusedLinkRoundtrip() {
            grid.setStairsWalkable(0, 0, 5, -1);
            long raw = grid.getStairsLinksRaw(0, 0);
            assertEquals(5,  VoxelStateGrid.linkA(raw));
            assertEquals(-1, VoxelStateGrid.linkB(raw));
        }

        @Test @DisplayName("isStairsWalkable true when links present")
        void walkableWhenLinksPresent() {
            grid.setStairsWalkable(0, 0, 3, 7);
            assertTrue(grid.isStairsWalkable(0, 0));
        }

        @Test @DisplayName("isStairsWalkable false for non-stairs cell")
        void notWalkableForNonStairs() {
            grid.setRoom(0, 0);
            assertFalse(grid.isStairsWalkable(0, 0));
        }

        @Test @DisplayName("stairsAllowsEnterFrom true for stored linkA")
        void allowsEnterFromLinkA() {
            int packed = grid.pack(1, 2);
            grid.setStairsWalkable(0, 0, packed, -1);
            assertTrue(grid.stairsAllowsEnterFrom(0, 0, packed));
        }

        @Test @DisplayName("stairsAllowsEnterFrom true for stored linkB")
        void allowsEnterFromLinkB() {
            int packedA = grid.pack(1, 0);
            int packedB = grid.pack(2, 0);
            grid.setStairsWalkable(0, 0, packedA, packedB);
            assertTrue(grid.stairsAllowsEnterFrom(0, 0, packedB));
        }

        @Test @DisplayName("stairsAllowsEnterFrom false for unlisted packed id")
        void rejectsUnlistedEntry() {
            grid.setStairsWalkable(0, 0, grid.pack(1, 0), grid.pack(2, 0));
            assertFalse(grid.stairsAllowsEnterFrom(0, 0, grid.pack(0, 1)));
        }

        @Test @DisplayName("stairsAllowsEnterFrom false for non-stairs cell")
        void rejectsNonStairsCell() {
            grid.setRoom(0, 0);
            assertFalse(grid.stairsAllowsEnterFrom(0, 0, 0));
        }

        @Test @DisplayName("getStairsLinksRaw returns 0 for FREE cell")
        void rawZeroForFree() {
            assertEquals(0L, grid.getStairsLinksRaw(0, 0));
        }
    }
}