package com.dungeon.logic.mesh;

import com.jme3.scene.Mesh;

import java.util.List;

/**
 * Encapsulates the meshes generated for a dungeon, including the corridor network, individual room shells, and the combined dungeon mesh.
 * <p>
 * This class serves as a container for the following meshes:
 * <ul>
 *     <li>{@link #network}: the mesh representing the corridor network.</li>
 *     <li>{@link #rooms}: a list of meshes for each individual room shell.</li>
 *     <li>{@link #dungeon}: the combined mesh of the entire dungeon, including all rooms and corridors.</li>
 * </ul>
 */
public final class DungeonMesh {

    private final Mesh network;
    private final List<Mesh> rooms;
    private final Mesh dungeon;

    public DungeonMesh(Mesh network, List<Mesh> rooms, Mesh dungeon) {
        this.network = network;
        this.rooms = rooms;
        this.dungeon = dungeon;
    }

    public Mesh getNetwork() {
        return network;
    }

    public List<Mesh> getRooms() {
        return rooms;
    }

    public Mesh getDungeon() {
        return dungeon;
    }
}


