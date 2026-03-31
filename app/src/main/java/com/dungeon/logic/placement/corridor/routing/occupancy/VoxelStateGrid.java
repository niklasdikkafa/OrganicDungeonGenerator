package com.dungeon.logic.placement.corridor.routing.occupancy;


/**
 * Compact 2.5D occupancy grid for corridor routing over {@code (polyId, zBand)} cells.
 * <p>
 * Each cell represents one routing polygon (2D) and one discrete vertical band (z-band).
 * The grid is designed for hot path usage: array-based storage, constant-time access,
 * and minimal allocations.
 * </p>
 *
 * <h2>State model</h2>
 * <p>
 * Internally, each cell stores a {@code byte} state code (0 = FREE). Some states also use an
 * auxiliary {@code stairsLinks} entry to encode restricted transitions for stair traversal.
 * </p>
 *
 * <h3>Overwrite / priority rules</h3>
 * <ul>
 *   <li><b>BORDER</b>: strongest; marks non-traversable safety space and is never overwritten.</li>
 *   <li><b>ROOM</b>: occupies the room voxels; does not overwrite BORDER.</li>
 *   <li><b>STAIRS</b>: occupies the cell as stairs; may be blocked (volume) or walkable (linked).</li>
 *   <li><b>CORRIDOR</b>: traversable routing space; does not overwrite BORDER or STAIRS.</li>
 *   <li><b>FREE_SPACE</b>: default state.</li>
 * </ul>
 *
 * <h2>STAIRS semantics</h2>
 * <p>
 * A STAIRS cell is considered <em>walkable</em> if it stores at least one link.
 * Links are stored as packed cell ids (see {@link #pack(int, int)}). A STAIRS cell can store up to
 * two links to enforce "no branching" along vertical transitions. The pathfinder enforces this by:
 * </p>
 * <ul>
 *   <li>If current cell is walkable STAIRS: expand only to its stored link targets.</li>
 *   <li>When entering STAIRS: allow entry only from one of its stored link sources.</li>
 * </ul>
 *
 * <p>
 * This class only provides the data structure and helper checks. The routing algorithm
 * (e.g., {@link com.dungeon.logic.placement.corridor.routing.path.PathFinder3D}) is responsible for applying the
 * traversal rules consistently.
 * </p>
 */
public final class VoxelStateGrid {

    private final int polyCount;
    private final int zBands;

    /** Packed cell id -> state code, where {@code 0 = FREE_SPACE} and {@code (ordinal + 1)} otherwise. */
    private final byte[] state;

    /**
     * Packed cell id -> encoded STAIRS links.
     * <p>
     * {@code 0} means "no links". If the cell is STAIRS and {@code stairsLinks[id] == 0},
     * the STAIRS cell is treated as a blocked volume (not walkable).
     * </p>
     */
    private final long[] stairsLinks;

    private static final VoxelState[] LUT = VoxelState.values();

    // cached codes (avoid ordinal math repeatedly)
    private static final byte FREE_CODE     = 0;
    private static final byte ROOM_CODE     = (byte) (VoxelState.ROOM.ordinal() + 1);
    private static final byte CORRIDOR_CODE = (byte) (VoxelState.CORRIDOR.ordinal() + 1);
    private static final byte STAIRS_CODE   = (byte) (VoxelState.STAIRS.ordinal() + 1);
    private static final byte BORDER_CODE   = (byte) (VoxelState.BORDER.ordinal() + 1);

    /**
     * Creates a new occupancy grid.
     *
     * @param polyCount number of routing polygons (2D cells)
     * @param zBands number of discrete vertical bands
     * @throws IllegalArgumentException if any dimension is {@code <= 0}
     */
    public VoxelStateGrid(int polyCount, int zBands) {
        if (polyCount <= 0) throw new IllegalArgumentException("polyCount must be > 0");
        if (zBands <= 0) throw new IllegalArgumentException("zBands must be > 0");

        this.polyCount = polyCount;
        this.zBands = zBands;
        this.state = new byte[polyCount * zBands];
        this.stairsLinks = new long[polyCount * zBands];
        // default: FREE_SPACE (0)
    }

    // ---------------- dimensions ----------------

    /** @return number of routing polygons (2D cells). */
    public int polyCount() { return polyCount; }

    /** @return number of discrete vertical bands. */
    public int zBands() { return zBands; }

    // ---------------- packing ----------------

    /**
     * Packs a {@code (polyId, zBand)} pair into a single array index.
     * <p>
     * This id is used throughout the routing system (including STAIRS link encoding).
     * </p>
     *
     * @param polyId polygon id (0-based)
     * @param zBand z-band index (0-based)
     * @return packed cell id (0..polyCount*zBands-1)
     * @throws IndexOutOfBoundsException if {@code polyId} or {@code zBand} are out of range
     */
    public int pack(int polyId, int zBand) {
        if (polyId < 0 || polyId >= polyCount) throw new IndexOutOfBoundsException("polyId=" + polyId);
        if (zBand < 0 || zBand >= zBands) throw new IndexOutOfBoundsException("zBand=" + zBand);
        return polyId * zBands + zBand;
    }

    // ---------------- state access ----------------

    /**
     * Returns the logical occupancy state of a cell.
     *
     * @param polyId polygon id
     * @param zBand z-band index
     * @return {@link VoxelState} for the cell
     */
    public VoxelState getState(int polyId, int zBand) {
        int id = pack(polyId, zBand);
        byte c = state[id];
        return c == FREE_CODE ? VoxelState.FREE_SPACE : LUT[(c & 0xFF) - 1];
    }

    /** @return true if the cell is {@code FREE_SPACE}. */
    public boolean isFree(int polyId, int zBand) { return getStateCode(polyId, zBand) == FREE_CODE; }

    /** @return true if the cell is {@code ROOM}. */
    public boolean isRoom(int polyId, int zBand) { return getStateCode(polyId, zBand) == ROOM_CODE; }

    /** @return true if the cell is {@code BORDER}. */
    public boolean isRoomBorder(int polyId, int zBand) { return getStateCode(polyId, zBand) == BORDER_CODE; }

    /** @return true if the cell is {@code CORRIDOR}. */
    public boolean isCorridor(int polyId, int zBand) { return getStateCode(polyId, zBand) == CORRIDOR_CODE; }

    /** @return true if the cell is {@code STAIRS} (walkable or blocked volume). */
    public boolean isStairs(int polyId, int zBand) { return getStateCode(polyId, zBand) == STAIRS_CODE; }

    private byte getStateCode(int polyId, int zBand) {
        return state[pack(polyId, zBand)];
    }

    // ---------------- traversal semantics helpers ----------------

    /**
     * Checks whether a cell is traversable under basic occupancy rules.
     * <p>
     * This ignores routing-policy constraints (e.g., corridor costs, pathfinder heuristics),
     * but respects that:
     * </p>
     * <ul>
     *   <li>BORDER is never traversable.</li>
     *   <li>STAIRS is traversable only if it is walkable (has links).</li>
     * </ul>
     *
     * @param polyId polygon id
     * @param zBand  z-band index
     * @return true if the cell is traversable at a basic level
     */
    public boolean isTraversableBasic(int polyId, int zBand) {
        int id = pack(polyId, zBand);
        byte c = state[id];
        if (c == BORDER_CODE) return false;
        if (c != STAIRS_CODE) return true; // FREE/ROOM/CORRIDOR traversable
        return stairsLinks[id] != 0L;      // STAIRS walkable only if links exist
    }

    // ---------------- overwrite rules (setters) ----------------

    /**
     * Marks a cell as {@code BORDER}.
     * <p>
     * BORDER is absolute: it overwrites any previous state and clears STAIRS links.
     * Intended for conservative clearance during initialization (e.g., wall thickness).
     * </p>
     */
    public void setBorder(int polyId, int zBand) {
        int id = pack(polyId, zBand);
        state[id] = BORDER_CODE;
        stairsLinks[id] = 0L;
    }

    /**
     * Marks a cell as {@code ROOM}.
     * <p>
     * Does not overwrite {@code BORDER}. Clears STAIRS links.
     * Intended for initial room rasterization.
     * </p>
     */
    public void setRoom(int polyId, int zBand) {
        int id = pack(polyId, zBand);
        if (state[id] == BORDER_CODE) return;
        state[id] = ROOM_CODE;
        stairsLinks[id] = 0L;
    }

    /**
     * Marks a cell as {@code CORRIDOR}.
     * <p>
     * Does not overwrite {@code BORDER} or {@code STAIRS}. Corridors must not replace stairs cells.
     * </p>
     */
    public void setCorridor(int polyId, int zBand) {
        int id = pack(polyId, zBand);
        byte cur = state[id];
        if (cur == BORDER_CODE || cur == STAIRS_CODE) return;
        state[id] = CORRIDOR_CODE;
    }

    /**
     * Marks a cell as walkable STAIRS and stores up to two allowed neighbor transitions.
     * <p>
     * Does not overwrite {@code ROOM} or {@code BORDER}. A link value of {@code -1} means "unused".
     * </p>
     *
     * @param polyId polygon id
     * @param zBand  z-band index
     * @param linkA  packed id of allowed transition A, or -1
     * @param linkB  packed id of allowed transition B, or -1
     */
    public void setStairsWalkable(int polyId, int zBand, int linkA, int linkB) {
        int id = pack(polyId, zBand);
        byte cur = state[id];
        if (cur == BORDER_CODE || cur == ROOM_CODE) return;
        state[id] = STAIRS_CODE;
        stairsLinks[id] = pack2(linkA, linkB);
    }

    // ---------------- stairs links (allocation-free) ----------------

    /**
     * @return true if the cell is {@code STAIRS} and has stored links (walkable stairs cell).
     */
    public boolean isStairsWalkable(int polyId, int zBand) {
        int id = pack(polyId, zBand);
        return state[id] == STAIRS_CODE && stairsLinks[id] != 0L;
    }

    /**
     * Returns the raw encoded link value for STAIRS cells.
     * <p>
     * {@code 0} means no links (blocked stairs volume) or a non-stairs cell.
     * </p>
     */
    public long getStairsLinksRaw(int polyId, int zBand) {
        return stairsLinks[pack(polyId, zBand)];
    }

    /** Decodes the first link from a raw STAIRS encoding (returns -1 if unused). */
    public static int linkA(long raw) {
        int a = (int) (raw >>> 32);
        return (a == 0xFFFFFFFF) ? -1 : a;
    }

    /** Decodes the second link from a raw STAIRS encoding (returns -1 if unused). */
    public static int linkB(long raw) {
        int b = (int) (raw & 0xFFFFFFFFL);
        return (b == 0xFFFFFFFF) ? -1 : b;
    }

    /**
     * Checks whether entering a STAIRS cell from {@code fromPacked} is allowed.
     * <p>
     * This prevents side-entry and branching: only the explicitly linked neighbors may enter.
     * </p>
     */
    public boolean stairsAllowsEnterFrom(int stairsPoly, int stairsZ, int fromPacked) {
        int id = pack(stairsPoly, stairsZ);
        if (state[id] != STAIRS_CODE) return false;
        long raw = stairsLinks[id];
        if (raw == 0L) return false; // volume / blocked
        int a = linkA(raw);
        int b = linkB(raw);
        return (a == fromPacked) || (b == fromPacked);
    }

    // ---------------- encoding helpers ----------------

    /**
     * Packs two 32-bit link ids into one {@code long}.
     * <p>
     * {@code -1} is stored as {@code 0xFFFFFFFF} to preserve the "0 means no-links" convention.
     * </p>
     */
    private static long pack2(int a, int b) {
        long A = (a == -1) ? 0xFFFFFFFFL : (a & 0xFFFFFFFFL);
        long B = (b == -1) ? 0xFFFFFFFFL : (b & 0xFFFFFFFFL);
        return (A << 32) | B;
    }
}