# Organic Dungeon Generator (PDG) - Bachelor Thesis Project

This repository contains an **Organic Procedural Dungeon Generator (PDG)** developed as part of my **Bachelor’s thesis 
(Computer Science)**. The generator creates dungeon layouts consisting of **rooms** and 
**corridors** on an organic, non-gridlike structure, and provides a **3D visualization** 
with multiple debug views for analysis and iteration during research and development.

The project focuses on a **robust pipeline** that goes from abstract placement → 
routing → network construction → mesh generation, with strong emphasis on debuggability 
(graph view, frames, grid, voxel occupancy, etc.).

The folder `results` contains the measured results of the classes `analysis/EmpiricalPerformanceBenchmark` and 
`analysis/QuantitativeAnalysis`. These classes were used for analytical purposes and are not part of the main generator pipeline.

---

## Features

### Dungeon Generation
- **Organic room placement** by sampling connected vertex clusters on a base grid.
- **Corridor routing** between rooms using a 2.5D approach (horizontal plane + discrete height bands).
- **Delaunay triangulation** + **minimum spanning tree (MST)** + optional extra edges for controllable connectivity density.
- Supports **multi-level dungeons** via **z-bands** (discrete vertical layers).

### Corridor Network & Geometry
- Builds a global **corridor centerline network** (graph) from routed corridor paths.
- Detects **junctions** and builds junction geometry using **portal ordering** and corner construction.
- Generates corridor and dungeon meshes:
    - **Inner shell** (walkable interior surface)
    - **Outer shell** (outer hull / wall thickness dependent)

### Visualization & Debug Tooling (in-app)
The application includes extensive debug rendering modes:
- Shell selection: inner only / outer only / both
- Corridor network graph view (nodes/edges) with endpoint & junction coloring
- Frame visualization (corridor cross sections)
- 2D room placement visualization on base grid
- Voxel grid visualization (occupied cells, including optional border voxels)
- Wireframe mode for mesh inspection
- 2D top-down view for grid and placement debugging

See `OrganicDungeonGenerator` for the full list of controls.

---

## Architecture Overview (Pipeline)

The generator is built as a sequence of deterministic steps (except for controlled randomness via seed):

1. **Base Grid Construction**
    - A hex-like point distribution is generated.
    - Triangles are connected and optionally merged.
    - Polygons are split into quads and smoothed (relaxation).
    - Topology (neighbors, polygon centers) is computed for stable queries.

2. **Room Placement**
    - Random start vertex is selected (excluding already used vertices).
    - A connected vertex cluster is grown to match a target room size range.
    - The boundary polygon is extracted and converted into a room footprint.
    - Room parameters are sampled (height bands, z-band index, rotation).
    - A conservative validator rejects overlapping rooms (fast AABB + z-interval test).

3. **Room Connectivity Graph**
    - Room centers are triangulated using **JTS Delaunay triangulation**.
    - Edges are weighted using a **2.5D metric** (horizontal + λ·vertical).
    - An MST is computed (**JGraphT** / Kruskal).
    - Optional extra edges are added depending on the desired density.

4. **Corridor Routing**
    - A refined routing grid (voxel-like) is built for pathfinding.
    - Rooms are rasterized into the voxel grid as blocked regions with clearance.
    - A 3D pathfinder routes corridors through traversable cells, including stair transitions.
    - Paths are committed into the voxel state grid (corridor cells, stairs, vertical clearance/borders).

5. **Corridor Network Build**
    - Corridor paths become a global graph (semantic node reuse).
    - Endpoints and junctions are detected.
    - Smoothing is applied (XZ-only).
    - Frames are computed per node for mesh generation.
    - Junction portal links and corner geometry are generated.

6. **Mesh Generation**
    - Room meshes and corridor meshes are built and combined into the dungeon shell(s) (with **JCSG**).
    - Separate inner and outer shell generation enables inspection and stable rendering.

---

## Running the Generator

### Main Application
The primary entry point is:

- `com.dungeon.application.OrganicDungeonGenerator`

It will prompt you for parameters in the terminal, then launch a **jMonkeyEngine** 3D window.

**Example input flow:**
- Number of rooms
- Density (sparse / medium / dense)
- Min/Max room size
- Optional seed (empty for random seed)

---

## Controls (In-App)

The generator includes multiple rendering modes and debug overlays (see the HUD in the bottom-left corner in the running app). Typical controls include:

- **1 / 2 / 3**: Shell view (both / inner only / outer only)
- **P**: Toggle “Grid 2D placement view” (fixed top-down camera, no movement)
- **R**: Rooms-only view
- **C**: Corridors-only view
- **M**: Wireframe toggle
- **G**: Graph view (hide mesh, show corridor network)
- **F**: Frame visualization (only meaningful in graph mode)
- **V**: Voxel grid visualization
- **B**: Border voxels (only in voxel mode)

---

## Configuration (DungeonConfig)

Most global tuning parameters are stored centrally in:

- `com.dungeon.config.DungeonConfig`

This includes, for example:
- corridor dimensions (width/height)
- wall thickness
- safety margins
- z-band height
- routing parameters / refinement factors
- inflate factor for AABBs

**Important:**  
Many parameters can be adjusted because they are not `final`. However, **the current implementation has primarily been 
tested using the default configuration values**. Changing them may expose edge cases (e.g., too small rooms 
for corridor frames, junction corner instability, degenerated meshes or voxel resolution mismatch). If you modify config values,
expect to re-validate the pipeline and debug views.

---

## Project Structure (High-Level)

- `com.dungeon.logic.grid.*`  
  Base grid construction, topology, relaxation.

- `com.dungeon.logic.placement.room.*`  
  Room placement pipeline (clusters → outline → room).

- `com.dungeon.logic.placement.corridor.*`  
  Corridor connectivity, routing, voxel occupancy, pathfinding.

- `com.dungeon.logic.placement.corridor.network.*`  
  Corridor graph/network build, junction logic, frames.

- `com.dungeon.logic.mesh.builder.*`  
  Mesh builders for rooms, corridors, and the combined dungeon shell.

- `com.dungeon.application.*`
  Main application.

---

## Dependencies

This project uses (at least) the following major libraries:
- **jMonkeyEngine (jME3)** - real-time 3D rendering
- **JTS Topology Suite** - geometry operations (centroids, polygons, Delaunay triangulation)
- **JGraphT** - MST computation (Kruskal)
- **JCSG** - mesh union between rooms and corridor network

---

## Reproducibility

Generation is **seed-based**. If you keep the same:
- seed
- DungeonConfig values
- input parameters

...you should get the same dungeon layout and mesh output.

---

## Known Limitations / Notes

- The pipeline includes conservative collision/rasterization checks to avoid invalid geometry; this may reject valid placements (false positives).
- Junction building may produce unstable geometry for special cases that are not currently handled. This may lead to degenerated meshes or failed union operations.
- The value of `DungeonConfig.INFLATE_F` determines the success rate of non-degenerated dungeons. There will
be cases that have holes and cut-off rooms in the dungeon mesh. To prevent this and increase the success rate,
you will have to increase this value. This will also increase run time and risk of `StackOverflowError`s. 
This is currently the biggest known limitation that can cause degenerated meshes and failed unions. It is assumed that this problem
comes from T-junctions that are generated in the JCSG-union. The union-loop then uses the non-manifold mesh for the next union-call, which
may cause these degenerated cases. One possible solution is to have one union call for all rooms with the corridor network.
This is just an assumption that wasn't tested and must be further investigated. 

---

## License / Attribution
You are free to use and modify this project for personal and academic purposes.  
If you use it in a public project, demo, presentation, or publication, please credit:

**Niklas Dikkafa - Organic Dungeon Generator (Bachelor Thesis Project)**  
(https://github.com/niklasdikkafa/OrganicDungeonGenerator)

---

## Acknowledgements
The organic base grid structure and room placement are inspired by *Townscaper* (Oskar Stålberg).

---

## Contact

- GitHub: www.github.com/niklasdikkafa
- LinkedIn: www.linkedin.com/in/niklas-dikkafa-488680234