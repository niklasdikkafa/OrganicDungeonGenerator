package com.dungeon.domain;

/**
 * Base class for all spatial structures generated in the dungeon (rooms, corridors).
 * <p>
 * Each {@code Structure} instance receives a unique, process-local integer ID at construction time.
 */
public abstract class Structure {

    /** Next ID to assign (process-local). */
    private static int NEXT_ID = 0;

    /** Unique ID of this structure instance. */
    private final int id = NEXT_ID++;

    /**
     * Returns the unique ID of this structure within the current JVM process.
     *
     * @return process-local unique integer ID
     */
    public int getId() {
        return id;
    }
}