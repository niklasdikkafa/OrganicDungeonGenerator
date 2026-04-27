package com.dungeon.application;

import com.dungeon.domain.Corridor;
import com.dungeon.domain.Room;
import com.dungeon.logic.geometry.Polygon;
import com.dungeon.logic.grid.BaseGrid;
import com.dungeon.logic.grid.BaseGridConfig;
import com.dungeon.logic.grid.builder.BaseGridBuilder;
import com.dungeon.logic.mesh.DungeonMesh;
import com.dungeon.logic.mesh.builder.DungeonMeshBuilder;
import com.dungeon.logic.placement.corridor.CorridorPlacer;
import com.dungeon.logic.placement.corridor.network.CorridorNetwork;
import com.dungeon.logic.placement.corridor.network.builder.CorridorNetworkBuilder;
import com.dungeon.logic.placement.corridor.network.graph.GraphEdge;
import com.dungeon.logic.placement.corridor.network.graph.GraphNode;
import com.dungeon.logic.placement.room.RoomPlacer;
import com.dungeon.logic.placement.corridor.routing.occupancy.VoxelState;
import com.dungeon.logic.placement.corridor.routing.occupancy.VoxelStateGrid;

import com.jme3.app.SimpleApplication;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.light.*;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.*;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.*;
import com.jme3.scene.shape.Line;
import com.jme3.scene.shape.Sphere;
import com.jme3.system.AppSettings;
import com.jme3.util.BufferUtils;

import java.util.*;
import java.util.List;

import static com.dungeon.config.DungeonConfig.WALL_THICKNESS;
import static com.dungeon.config.DungeonConfig.Z_BAND_HEIGHT;
import static com.dungeon.logic.placement.corridor.network.graph.CorridorGraph.edgeKey;

/**
 * Main interactive demo application for the procedural dungeon generator.
 * <p>
 * This class runs the complete generation pipeline (grid → rooms → corridors → corridor network → meshes)
 * and provides an in-engine debug UI to inspect intermediate results such as the corridor graph,
 * per-node frames/tangents, 2d grid view with room placement, and the routing voxel occupancy grid.
 * </p>
 *
 * <h2>Generation overview</h2>
 * <ol>
 *   <li><b>Base grid:</b> A {@link com.dungeon.logic.grid.BaseGrid} is created using {@link com.dungeon.logic.grid.builder.BaseGridBuilder}
 *       from a {@link com.dungeon.logic.grid.BaseGridConfig} (grid sides + edge length).</li>
 *   <li><b>Rooms:</b> {@link com.dungeon.logic.placement.room.RoomPlacer} samples connected vertex clusters and produces {@link com.dungeon.domain.Room}
 *       footprints plus vertical parameters (height / Z-band).</li>
 *   <li><b>Corridors:</b> {@link com.dungeon.logic.placement.corridor.CorridorPlacer} computes connectivity between rooms and routes corridors on a
 *       refined routing grid (including stairs), producing {@link com.dungeon.domain.Corridor} centerlines and a {@link com.dungeon.logic.placement.corridor.routing.occupancy.VoxelStateGrid}
 *       for debug visualization.</li>
 *   <li><b>Corridor network:</b> {@link com.dungeon.logic.placement.corridor.network.builder.CorridorNetworkBuilder} builds a global graph from corridor paths,
 *       performs smoothing, computes node frames, and generates junction corner links.</li>
 *   <li><b>Meshes:</b> The scene is rendered as two shells (inner + outer) using {@link com.dungeon.logic.mesh.builder.DungeonMeshBuilder}.
 *       Optional debug-only meshes can show only rooms ({@link com.dungeon.logic.mesh.builder.RoomMeshBuilder})
 *       or only corridors ({@link com.dungeon.logic.mesh.builder.CorridorMeshBuilder}).</li>
 * </ol>
 *
 * <h2>Controls</h2>
 * <ul>
 *   <li><b>1</b> - Show inner + outer shell (default)</li>
 *   <li><b>2</b> - Show only inner shell</li>
 *   <li><b>3</b> - Show only outer shell</li>
 *   <li><b>R</b> - Toggle “rooms only” view</li>
 *   <li><b>C</b> - Toggle “corridors only” view</li>
 *   <li><b>M</b> - Toggle wireframe for the currently visible mesh(es)</li>
 *   <li><b>G</b> - Toggle graph mode (hides meshes and shows the corridor network graph)</li>
 *   <li><b>F</b> - Toggle corridor node frames (only available in graph mode)</li>
 *   <li><b>V</b> - Toggle voxel occupancy visualization (routing grid)</li>
 *   <li><b>B</b> - Toggle border voxels (only available when voxel view is active)</li>
 *   <li><b>P</b> - Toggle “Grid 2D placement view” (fixed top-down camera, no movement)</li>
 * </ul>
 *
 * <h2>HUD</h2>
 * The bottom-left HUD displays:
 * <ul>
 *   <li>Seed and generation counts (rooms, corridors)</li>
 *   <li>Current view mode (shells / rooms / corridors / graph)</li>
 *   <li>Keyboard shortcuts with dynamic “activate/deactivate” labels reflecting current state</li>
 * </ul>
 *
 * <h2>Configuration and tunable parameters</h2>
 * This demo relies on {@link com.dungeon.config.DungeonConfig} as the central place for global generation and rendering parameters
 * (e.g. corridor dimensions, wall thickness, Z-band height, routing constants and safety margins).
 * <p>
 * Within this class, fields that are <b>not</b> declared {@code final} (e.g. rotation parameters, room Z-band heights or corridor width)
 * can be adjusted and some of the needed parameters are intended to be configured at runtime via terminal input.
 * </p>
 *
 * <h2>Stability note</h2>
 * The current implementation and visual validation were tested with the existing parameter values and typical input ranges.
 * If non-final parameters or {@code DungeonConfig} values are changed significantly, corner cases may appear and
 * correctness cannot be guaranteed in all scenarios (e.g. extreme scales, very small rooms, or unusual density settings).
 *
 * <p>
 * The application uses jMonkeyEngine ({@link com.jme3.app.SimpleApplication}) for rendering and input handling.
 * </p>
 */
public class OrganicDungeonGenerator extends SimpleApplication {

    // =========================================================================
    // Constants
    // =========================================================================

    private static final float NODE_RADIUS         = 0.3f;
    private static final float TANGENT_LENGTH       = 3.0f;
    private static final float FRAME_LINE_WIDTH     = 1.5f;
    private static final float TANGENT_LINE_WIDTH   = 2.5f;

    // =========================================================================
    // Enums / Records
    // =========================================================================

    private enum ShellView { BOTH, INNER_ONLY, OUTER_ONLY }

    private record GridEstimate(int sides, float edgeLength) {}

    private record UserConfig(int roomCount, int minRoomSize, int maxRoomSize,
                              CorridorPlacer.Density density, int gridSides,
                              float gridEdgeLength, long seed) {}

    // =========================================================================
    // User config
    // =========================================================================

    private int roomCount;
    private int minRoomSize;
    private int maxRoomSize;
    private CorridorPlacer.Density density;
    private int gridSides;
    private float gridEdgeLength;
    private long seed;

    // =========================================================================
    // Generated data
    // =========================================================================

    private BaseGrid baseGrid;
    private List<Room>     rooms      = List.of();
    private List<Corridor> corridors  = List.of();
    private CorridorNetwork net;
    private BaseGrid        routingGrid;
    private VoxelStateGrid  voxelState;

    // =========================================================================
    // Lights
    // =========================================================================

    private PointLight cameraLight;

    // =========================================================================
    // UI state
    // =========================================================================

    private ShellView shellView      = ShellView.BOTH;
    private boolean   roomsOnly      = false;
    private boolean   corridorsOnly  = false;
    private boolean   wireframe      = false;
    private boolean   graphMode      = false;
    private boolean   showFrames     = false;
    private boolean   showVoxels     = false;
    private boolean   showBorderVoxels = false;
    private boolean grid2DMode = false;

    // =========================================================================
    // Scene nodes
    // =========================================================================

    private final Node dungeonRoot       = new Node("dungeonRoot");
    private final Node shellsNode        = new Node("shellsNode");
    private final Node roomsOnlyNode     = new Node("roomsOnlyNode");
    private final Node corridorsOnlyNode = new Node("corridorsOnlyNode");
    private final Node graphNode         = new Node("graphNode");
    private final Node framesNode        = new Node("framesNode");
    private final Node tangentsNode      = new Node("tangentsNode");
    private final Node voxelsNode        = new Node("voxelsNode");
    private final Node borderVoxelsNode  = new Node("borderVoxelsNode");
    private final Node grid2DNode = new Node("grid2DNode");

    private Geometry outerShellGeom;
    private Geometry innerShellGeom;
    private Geometry corridorsOuterGeom;
    private Geometry corridorsInnerGeom;

    // HUD
    private BitmapText hud;

    // =========================================================================
    // Camera state backup for toggling 2D mode
    // =========================================================================

    private Vector3f camPosBackup;
    private Quaternion camRotBackup;
    private boolean flyCamEnabledBackup;
    private boolean dragToRotateBackup;
    private boolean cursorVisibleBackup;

    // =========================================================================
    // Entry point
    // =========================================================================

    public static void main(String[] args) {
        UserConfig cfg = readUserConfigFromTerminal();

        OrganicDungeonGenerator app = new OrganicDungeonGenerator();
        app.roomCount     = cfg.roomCount;
        app.minRoomSize   = cfg.minRoomSize;
        app.maxRoomSize   = cfg.maxRoomSize;
        app.density       = cfg.density;
        app.gridSides     = cfg.gridSides;
        app.gridEdgeLength = cfg.gridEdgeLength;
        app.seed          = cfg.seed;

        AppSettings settings = new AppSettings(true);
        settings.setRenderer(AppSettings.LWJGL_OPENGL32);
        settings.setResolution(1400, 900);
        settings.setFullscreen(false);
        settings.setTitle("Organic Dungeon Generator");

        app.setShowSettings(false);
        app.setSettings(settings);
        app.start();
    }

    // =========================================================================
    // Initialization
    // =========================================================================

    @Override
    public void simpleInitApp() {
        setDisplayStatView(false);
        setDisplayFps(false);
        viewPort.setBackgroundColor(new ColorRGBA(0.97f, 0.97f, 0.98f, 1f));

        setupCamera();
        setupLights();
        setupKeys();
        setupHud();

        buildDungeon();
        buildSceneGeometry();
        assembleSceneGraph();
        positionCamera();

        applyVisibilityAndMaterials();
        updateHudText();
    }

    @Override
    public void simpleUpdate(float tpf) {
        if (cameraLight != null) cameraLight.setPosition(cam.getLocation());
        if (hud != null) hud.setLocalTranslation(10f, 10f + hud.getLineHeight() * hud.getText().split("\n").length, 0f);
    }

    // =========================================================================
    // Dungeon pipeline
    // =========================================================================

    private void buildDungeon() {
        System.out.println("Seed: " + seed);
        System.out.println("Grid: sides=" + gridSides + ", edgeLength=" + gridEdgeLength);
        System.out.println("Rooms: " + roomCount + ", min=" + minRoomSize + ", max=" + maxRoomSize + ", density=" + density);

        BaseGrid grid = new BaseGridBuilder().build(new BaseGridConfig(gridSides, gridEdgeLength), seed);
        this.baseGrid = grid;

        rooms     = new RoomPlacer(grid, minRoomSize, maxRoomSize, seed + 1).generateRooms(roomCount);
        CorridorPlacer corridorPlacer = new CorridorPlacer(grid, density, seed + 2);
        corridors  = corridorPlacer.generateCorridors(rooms);
        voxelState = corridorPlacer.getDebugLastState();
        routingGrid = corridorPlacer.getRoutingGrid();
        net        = CorridorNetworkBuilder.build(corridors, rooms, routingGrid.getEdgeLength());

        System.out.println("Generated rooms: " + rooms.size());
        System.out.println("Generated corridors: " + corridors.size());
        System.out.println("Graph nodes: " + net.graph.nodes.size());
        System.out.println("Junction portal links: " + net.junctionLinksByJunctionAndPortal.size());
    }

    private void buildSceneGeometry() {
        DungeonMesh dmOuter = DungeonMeshBuilder.buildDungeonMesh(net, rooms, true);
        DungeonMesh dmInner = DungeonMeshBuilder.buildDungeonMesh(net, rooms, false);
        buildShellGeoms(dmOuter.getDungeon(), dmInner.getDungeon());
        buildRoomsOnlyGeoms(dmOuter.getRooms());
        buildCorridorsOnlyGeoms(dmOuter.getNetwork(), dmInner.getNetwork());
        buildGraphDebug();
        buildVoxelDebug();
    }

    private void assembleSceneGraph() {
        rootNode.attachChild(dungeonRoot);
        dungeonRoot.attachChild(shellsNode);
        dungeonRoot.attachChild(roomsOnlyNode);
        dungeonRoot.attachChild(corridorsOnlyNode);
        dungeonRoot.attachChild(graphNode);
        dungeonRoot.attachChild(framesNode);
        dungeonRoot.attachChild(tangentsNode);
        dungeonRoot.attachChild(voxelsNode);
        dungeonRoot.attachChild(borderVoxelsNode);
        dungeonRoot.attachChild(grid2DNode);
    }

    private void positionCamera() {
        if (rooms.isEmpty()) return;
        Vector2f c = rooms.getFirst().getInteriorPoint();
        float y = rooms.getFirst().getZLevel() + rooms.getFirst().getHeight() * 0.5f + 10f;
        cam.setLocation(new Vector3f(c.x + 25f, y + 30f, c.y + 25f));
        cam.lookAt(new Vector3f(c.x, y, c.y), Vector3f.UNIT_Y);
    }

    // =========================================================================
    // Geometry builders
    // =========================================================================

    private void buildShellGeoms(Mesh dungeonOuter, Mesh dungeonInner) {
        shellsNode.detachAllChildren();

        if (WALL_THICKNESS > 0f) {
            outerShellGeom = geom("DungeonOuterShell",
                    dungeonOuter,
                    litNoCull(new ColorRGBA(0.60f, 0.68f, 0.80f, 1f)));
            shellsNode.attachChild(outerShellGeom);
        }

        innerShellGeom = geom("DungeonInnerShell",
                dungeonInner,
                litNoCull(new ColorRGBA(0.82f, 0.86f, 0.92f, 1f)));

        shellsNode.attachChild(innerShellGeom);
    }

    private void buildRoomsOnlyGeoms(List<Mesh> rooms) {
        roomsOnlyNode.detachAllChildren();
        for (int i = 0; i < rooms.size(); i++) {
            Geometry g = geom("room_" + i,
                    rooms.get(i),
                    litNoCull(pastel(i + 1)));
            roomsOnlyNode.attachChild(g);
        }
    }

    private void buildCorridorsOnlyGeoms(Mesh networkOuter, Mesh networkInner) {
        corridorsOnlyNode.detachAllChildren();

        if (WALL_THICKNESS > 0f) {
            corridorsOuterGeom = geom("CorridorsOuter",
                    networkOuter,
                    litNoCull(new ColorRGBA(0.45f, 0.50f, 0.62f, 1f)));
            corridorsOnlyNode.attachChild(corridorsOuterGeom);
        }
        corridorsInnerGeom = geom("CorridorsInner",
                networkInner,
                litNoCull(new ColorRGBA(0.70f, 0.76f, 0.86f, 1f)));

        corridorsOnlyNode.attachChild(corridorsInnerGeom);
    }

    // =========================================================================
    // Debug: graph, frames, tangents
    // =========================================================================

    private void buildGraphDebug() {
        graphNode.detachAllChildren();
        tangentsNode.detachAllChildren();
        framesNode.detachAllChildren();
        if (net == null) return;

        graphNode.attachChild(buildCorridorGraphNode());
        tangentsNode.attachChild(buildCorridorTangentsNode());
        framesNode.attachChild(buildCorridorFramesNode());
    }

    private Node buildCorridorGraphNode() {
        Node root = new Node("corridorGraph");

        Material normalMat   = unshaded(new ColorRGBA(1f, 0.60f, 0f, 1f), false);
        Material endpointMat = unshaded(new ColorRGBA(1f, 0f, 1f, 1f), false);
        Material junctionMat = unshaded(new ColorRGBA(1f, 0.1f, 0.1f, 1f), false);
        Material edgeMat     = unshaded(new ColorRGBA(0f, 0f, 0f, 1f), false);
        edgeMat.getAdditionalRenderState().setLineWidth(2f);

        Sphere sph = new Sphere(10, 10, NODE_RADIUS);
        int n = net.graph.nodes.size();

        for (int i = 0; i < n; i++) {
            GraphNode gn = net.graph.nodes.get(i);
            if (gn == null || gn.position == null) continue;
            Geometry g = new Geometry("gn_" + i, sph);
            g.setMaterial(gn.isJunction ? junctionMat : gn.isEndpoint ? endpointMat : normalMat);
            g.setLocalTranslation(gn.position);
            root.attachChild(g);
        }

        HashSet<Long> built = new HashSet<>();
        for (int from = 0; from < n; from++) {
            List<GraphEdge> adj = net.graph.adjacency.get(from);
            if (adj == null) continue;
            Vector3f aPos = net.graph.nodes.get(from).position;
            if (aPos == null) continue;

            for (GraphEdge e : adj) {
                if (e.to < 0 || e.to >= n || !built.add(edgeKey(from, e.to))) continue;
                Vector3f bPos = net.graph.nodes.get(e.to).position;
                if (bPos == null) continue;
                Geometry line = new Geometry("ge_" + from + "_" + e.to, new Line(aPos, bPos));
                line.setMaterial(edgeMat);
                root.attachChild(line);
            }
        }
        return root;
    }

    private Node buildCorridorTangentsNode() {
        Node root = new Node("corridorTangents");
        Material mat = unshaded(ColorRGBA.Cyan, false);
        mat.getAdditionalRenderState().setLineWidth(TANGENT_LINE_WIDTH);

        for (GraphNode gn : net.graph.nodes) {
            if (gn == null || gn.position == null) continue;
            Vector3f t = new Vector3f(gn.tangent.x, 0f, gn.tangent.z);
            if (t.lengthSquared() < 1e-8f) continue;
            t.normalizeLocal();

            Geometry line = new Geometry("tan_" + gn.id,
                    new Line(gn.position, gn.position.add(t.mult(TANGENT_LENGTH, new Vector3f()))));
            line.setMaterial(mat);
            root.attachChild(line);
        }
        return root;
    }

    private Node buildCorridorFramesNode() {
        Node root = new Node("corridorFrames");

        Material outerMat = unshaded(ColorRGBA.Blue, false);
        outerMat.getAdditionalRenderState().setLineWidth(FRAME_LINE_WIDTH);

        Material innerMat = unshaded(ColorRGBA.Green, false);
        innerMat.getAdditionalRenderState().setLineWidth(FRAME_LINE_WIDTH);

        for (GraphNode gn : net.graph.nodes) {
            if (gn == null || gn.frameDisabled) continue;
            addRectLines(root, "of_" + gn.id, gn.outerLeftBottom, gn.outerRightBottom, gn.outerRightTop, gn.outerLeftTop, outerMat);
            addRectLines(root, "if_" + gn.id, gn.innerLeftBottom, gn.innerRightBottom, gn.innerRightTop, gn.innerLeftTop, innerMat);
        }
        return root;
    }

    private void addRectLines(Node parent, String prefix,
                              Vector3f lb, Vector3f rb, Vector3f rt, Vector3f lt, Material mat) {
        parent.attachChild(line(prefix + "_0", lb, rb, mat));
        parent.attachChild(line(prefix + "_1", rb, rt, mat));
        parent.attachChild(line(prefix + "_2", rt, lt, mat));
        parent.attachChild(line(prefix + "_3", lt, lb, mat));
    }

    // =========================================================================
    // Debug: grid
    // =========================================================================

    private void enableGrid2DView() {
        rebuildGrid2DGeometry();

        camPosBackup = cam.getLocation().clone();
        camRotBackup = cam.getRotation().clone();
        flyCamEnabledBackup = flyCam.isEnabled();
        dragToRotateBackup = flyCam.isDragToRotate();
        cursorVisibleBackup = inputManager.isCursorVisible();

        flyCam.setEnabled(false);
        inputManager.setCursorVisible(false);

        centerCameraTopDown();
    }

    private void disableGrid2DView() {
        // Restore camera + input state
        if (camPosBackup != null) cam.setLocation(camPosBackup);
        if (camRotBackup != null) cam.setRotation(camRotBackup);

        flyCam.setEnabled(flyCamEnabledBackup);
        flyCam.setDragToRotate(dragToRotateBackup);
        inputManager.setCursorVisible(cursorVisibleBackup);

    }

    private void centerCameraTopDown() {
        if (baseGrid == null) return;

        List<Vector2f> verts = baseGrid.getVertices();
        if (verts == null || verts.isEmpty()) return;

        float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;

        for (Vector2f v : verts) {
            minX = Math.min(minX, v.x);
            maxX = Math.max(maxX, v.x);
            minZ = Math.min(minZ, v.y);
            maxZ = Math.max(maxZ, v.y);
        }

        Vector3f center = new Vector3f(0, 0, 0);
        float span = Math.max(maxX - minX, maxZ - minZ);

        float height = Math.max(20f, span * 1.2f);

        cam.setLocation(new Vector3f(center.x, height, center.z));
        cam.lookAt(center, Vector3f.UNIT_Z.negate());
    }

    private void rebuildGrid2DGeometry() {
        grid2DNode.detachAllChildren();

        if (baseGrid == null) return;

        drawGridPolygons2D();
        drawRooms2D();
    }

    private void drawGridPolygons2D() {
        Node polyNode = new Node("GridPolygons");

        List<Polygon> polys = baseGrid.getAllPolygons();
        List<Vector2f> verts = baseGrid.getVertices();

        ColorRGBA lineColor = ColorRGBA.Black.clone();
        lineColor.a = 1f;

        for (Polygon p : polys) {
            List<Vector2f> points = new ArrayList<>();
            for (int idx : p.getVertexIndices()) {
                points.add(verts.get(idx));
            }
            Geometry outline = createPolyline(points, lineColor, 1f);
            polyNode.attachChild(outline);
        }

        grid2DNode.attachChild(polyNode);
    }

    private void drawRooms2D() {
        if (rooms == null || rooms.isEmpty()) return;

        Node roomsNode = new Node("Rooms2D");
        for (Room r : rooms) {
            Node rn = buildRoom2DNode(r);
            if (rn != null) roomsNode.attachChild(rn);
        }
        grid2DNode.attachChild(roomsNode);
    }

    private Node buildRoom2DNode(Room room) {
        if (room == null) return null;

        Node roomNode = new Node("RoomShape_" + room.getId());

        List<Vector2f> innerCorners = room.getInnerCorners();
        List<Vector2f> outerCorners = room.getOuterCorners();
        if (innerCorners.size() < 3) return null;
        if (outerCorners.size() < 3) return null;

        // room layout outline
        Geometry innerOutline = createPolyline(innerCorners, ColorRGBA.Orange, 4f);
        roomNode.attachChild(innerOutline);

        Geometry outerOutline = createPolyline(outerCorners, ColorRGBA.Red, 4f);
        roomNode.attachChild(outerOutline);

        // inner corners as dots
        Sphere sphere = new Sphere(8, 8, 0.12f);
        for (int i = 0; i < innerCorners.size(); i++) {
            Vector2f c = innerCorners.get(i);

            Geometry g = new Geometry("corner_" + room.getId() + "_" + i, sphere);
            Material m = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            m.setColor("Color", ColorRGBA.Orange);
            g.setMaterial(m);

            g.setLocalTranslation(c.x, 0f, c.y);

            roomNode.attachChild(g);
        }

        return roomNode;
    }

    private Geometry createPolyline(List<Vector2f> points, ColorRGBA color, float lineWidth) {
        if (points == null || points.size() < 2) return new Geometry("polyline_empty");

        // Build line segments (closed loop)
        int n = points.size();
        int segments = n; // closed: n edges
        Vector3f[] pos = new Vector3f[segments * 2];

        int k = 0;
        for (int i = 0; i < n; i++) {
            Vector2f a2 = points.get(i);
            Vector2f b2 = points.get((i + 1) % n);

            // Map (x,z) onto XZ plane
            pos[k++] = new Vector3f(a2.x, 0f, a2.y);
            pos[k++] = new Vector3f(b2.x, 0f, b2.y);
        }

        Mesh m = new Mesh();
        m.setMode(Mesh.Mode.Lines);
        m.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(pos));
        m.updateBound();

        Geometry g = new Geometry("polyline", m);

        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);
        mat.getAdditionalRenderState().setLineWidth(lineWidth);
        mat.getAdditionalRenderState().setDepthTest(true);
        mat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);

        g.setMaterial(mat);
        return g;
    }

    // =========================================================================
    // Debug: voxels
    // =========================================================================

    private void buildVoxelDebug() {
        voxelsNode.detachAllChildren();
        borderVoxelsNode.detachAllChildren();
        if (routingGrid == null || voxelState == null) return;

        buildExtrudedBatches(voxelsNode,       VoxelState.CORRIDOR, unshaded(new ColorRGBA(0.15f, 0.45f, 1f,    0.20f), true));
        buildExtrudedBatches(voxelsNode,       VoxelState.STAIRS,   unshaded(new ColorRGBA(0f,    1f,    0f,    0.25f), true));
        buildExtrudedBatches(voxelsNode,       VoxelState.ROOM,     unshaded(new ColorRGBA(1f,    0.15f, 0.15f, 0.25f), true));
        buildExtrudedBatches(borderVoxelsNode, VoxelState.BORDER,   unshaded(new ColorRGBA(0.05f, 0.05f, 0.05f, 0.40f), true));
    }

    private void buildExtrudedBatches(Node parent, VoxelState target, Material material) {
        List<Vector2f> verts2 = routingGrid.getVertices();
        List<Polygon>  polys  = routingGrid.getAllPolygons();
        int polyCount = voxelState.polyCount();
        int zBands    = voxelState.zBands();

        for (int z = 0; z < zBands; z++) {
            IntArrayList polyIds = new IntArrayList(2048);
            for (int p = 0; p < polyCount; p++) {
                if (voxelState.getState(p, z) == target) polyIds.add(p);
            }
            if (polyIds.size() == 0) continue;

            Geometry g = geom(target.name() + "_z" + z,
                    buildExtrudedPolygonBatchMesh(polys, verts2, polyIds, z * Z_BAND_HEIGHT, Z_BAND_HEIGHT),
                    material);
            g.setQueueBucket(RenderQueue.Bucket.Transparent);
            parent.attachChild(g);
        }
    }

    private Mesh buildExtrudedPolygonBatchMesh(List<Polygon> polys, List<Vector2f> verts2,
                                               IntArrayList polyIds, float yBase, float thickness) {
        float yTop = yBase + thickness;
        int totalVerts = 0, totalTris = 0;

        for (int i = 0; i < polyIds.size(); i++) {
            int k = polys.get(polyIds.get(i)).getVertexIndices().length;
            if (k < 3) continue;
            totalVerts += 2 * k;
            totalTris  += (k - 2) + (k - 2) + (k * 2);
        }

        Vector3f[] pos = new Vector3f[totalVerts];
        int[]      idx = new int[totalTris * 3];
        int vBase = 0, t = 0;

        for (int i = 0; i < polyIds.size(); i++) {
            int[] vi = polys.get(polyIds.get(i)).getVertexIndices();
            int k = vi.length;
            if (k < 3) continue;

            for (int j = 0; j < k; j++) {
                Vector2f p2 = verts2.get(vi[j]);
                pos[vBase + j]     = new Vector3f(p2.x, yBase, p2.y);
                pos[vBase + k + j] = new Vector3f(p2.x, yTop,  p2.y);
            }

            int top0 = vBase + k, bot0 = vBase;
            for (int j = 1; j < k - 1; j++) {
                idx[t++] = top0; idx[t++] = vBase + k + j; idx[t++] = vBase + k + j + 1;
                idx[t++] = bot0; idx[t++] = vBase + j + 1; idx[t++] = vBase + j;
            }

            for (int j = 0; j < k; j++) {
                int j2  = (j + 1) % k;
                int b0  = vBase + j, b1 = vBase + j2;
                int tt0 = vBase + k + j, tt1 = vBase + k + j2;
                idx[t++] = b0; idx[t++] = tt0; idx[t++] = tt1;
                idx[t++] = b0; idx[t++] = tt1; idx[t++] = b1;
            }

            vBase += 2 * k;
        }

        Mesh mesh = new Mesh();
        mesh.setMode(Mesh.Mode.Triangles);
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(pos));
        mesh.setBuffer(VertexBuffer.Type.Index,    3, BufferUtils.createIntBuffer(idx));
        mesh.updateBound();
        return mesh;
    }

    // =========================================================================
    // Visibility and materials
    // =========================================================================

    private void applyVisibilityAndMaterials() {
        boolean showShells    = !roomsOnly && !corridorsOnly && !graphMode;
        boolean showRooms     = roomsOnly  && !graphMode;
        boolean showCorridors = corridorsOnly && !graphMode;

        cull(innerShellGeom,     showShells && shellView != ShellView.OUTER_ONLY);
        cull(outerShellGeom,     showShells && shellView != ShellView.INNER_ONLY);
        cull(shellsNode,         showShells);
        cull(roomsOnlyNode,      showRooms);
        cull(corridorsOnlyNode,  showCorridors);

        cull(graphNode,          graphMode);
        cull(tangentsNode,       graphMode);
        cull(framesNode,         graphMode && showFrames);

        cull(voxelsNode,         showVoxels);
        cull(borderVoxelsNode,   showVoxels && showBorderVoxels);

        cull(grid2DNode, grid2DMode);

        applyWireframe(showShells, showRooms, showCorridors);
    }

    private void applyWireframe(boolean showShells, boolean showRooms, boolean showCorridors) {
        setWireframe(innerShellGeom,    showShells && wireframe);
        setWireframe(outerShellGeom,    showShells && wireframe);
        setWireframe(corridorsOuterGeom, showCorridors && wireframe);
        setWireframe(corridorsInnerGeom, showCorridors && wireframe);
        for (Spatial s : roomsOnlyNode.getChildren()) {
            if (s instanceof Geometry g) setWireframe(g, showRooms && wireframe);
        }
    }

    private static void cull(Spatial s, boolean visible) {
        if (s != null) s.setCullHint(visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
    }

    private static void setWireframe(Geometry g, boolean on) {
        if (g != null && g.getMaterial() != null)
            g.getMaterial().getAdditionalRenderState().setWireframe(on);
    }

    // =========================================================================
    // Input
    // =========================================================================

    private void setupKeys() {
        String[] names = { "shell1","shell2","shell3","roomsOnly","corrOnly",
                "wire","graph","frames","voxels","border","grid2d" };

        int[] keys = { KeyInput.KEY_1, KeyInput.KEY_2, KeyInput.KEY_3,
                KeyInput.KEY_R, KeyInput.KEY_C, KeyInput.KEY_M, KeyInput.KEY_G,
                KeyInput.KEY_F, KeyInput.KEY_V, KeyInput.KEY_B, KeyInput.KEY_P };
        for (int i = 0; i < names.length; i++)
            inputManager.addMapping(names[i], new KeyTrigger(keys[i]));
        inputManager.addListener(actionListener, names);
    }

    private final ActionListener actionListener = (name, isPressed, _) -> {
        if (!isPressed) return;
        switch (name) {
            case "shell1"   -> shellView = ShellView.BOTH;
            case "shell2"   -> shellView = ShellView.INNER_ONLY;
            case "shell3"   -> shellView = ShellView.OUTER_ONLY;
            case "wire"     -> wireframe     = !wireframe;
            case "roomsOnly" -> { roomsOnly = !roomsOnly;     if (roomsOnly)     { corridorsOnly = false; graphMode = false; } }
            case "corrOnly"  -> { corridorsOnly = !corridorsOnly; if (corridorsOnly) { roomsOnly = false;  graphMode = false; } }
            case "graph"     -> { graphMode = !graphMode;     if (graphMode)     { roomsOnly = false;  corridorsOnly = false; } else showFrames = false; }
            case "frames"    -> { if (graphMode) showFrames   = !showFrames; }
            case "voxels"    -> { showVoxels = !showVoxels;   if (!showVoxels) showBorderVoxels = false; }
            case "border"    -> { if (showVoxels) showBorderVoxels = !showBorderVoxels; }
            case "grid2d" -> {
                grid2DMode = !grid2DMode;

                // grid2DMode is mutually exclusive with 3D views
                if (grid2DMode) {
                    roomsOnly = false;
                    corridorsOnly = false;
                    graphMode = false;
                    showFrames = false;
                    showVoxels = false;
                    showBorderVoxels = false;

                    enableGrid2DView();
                } else {
                    disableGrid2DView();
                }
            }
        }
        applyVisibilityAndMaterials();
        updateHudText();
    };

    // =========================================================================
    // HUD
    // =========================================================================

    private void setupHud() {
        if (guiFont == null) guiFont = assetManager.loadFont("Interface/Fonts/Default.fnt");
        hud = new BitmapText(guiFont);
        hud.setSize(14f);
        hud.setColor(ColorRGBA.Black);
        guiNode.attachChild(hud);
    }

    private void updateHudText() {
        String view = grid2DMode ? "GRID_2D" : graphMode ? "GRAPH" : roomsOnly ? "ROOMS" : corridorsOnly ? "CORRIDORS"
                : "SHELLS(" + switch (shellView) { case BOTH -> "IN+OUT"; case INNER_ONLY -> "INNER"; case OUTER_ONLY -> "OUTER"; } + ")";

        hud.setText(
                "Seed: " + seed + "\n" +
                        "Rooms: " + (rooms != null ? rooms.size() : 0) + " | Corridors: " + (corridors != null ? corridors.size() : 0) + "\n" +
                        "View: " + view + "\n" +
                        "1/2/3: Shells  | " +  toggle(roomsOnly, "Show whole dungeon", "Only show rooms") +
                        " | " + toggle(corridorsOnly, "Show whole dungeon", "Only show corridors") + "\n" +
                        toggle(wireframe, "Deactivate wireframe", "Activate wireframe") +
                        " | " + toggle(graphMode, "Don't show graph", "Show graph") +
                        " | " + (graphMode ? toggle(showFrames, "Don't show frames", "Show frames") : "F: Frames (only with graph)") + "\n" +
                        toggle(showVoxels, "Deactivate voxels", "Activate voxels") +
                        (showVoxels ? " | " + toggle(showBorderVoxels, "Don't show border", "Show Border") : " | B: Border (only with voxels)"
                                + "\n" + toggle(grid2DMode, "Disable Grid 2D", "Enable Grid 2D (P)"))
        );
    }

    private static String toggle(boolean active, String offLabel, String onLabel) {
        return active ? "Deactivate: " + offLabel : "Activate: " + onLabel;
    }

    // =========================================================================
    // Camera and lights
    // =========================================================================

    private void setupCamera() {
        flyCam.setEnabled(true);
        flyCam.setDragToRotate(true);
        inputManager.setCursorVisible(true);
        flyCam.setMoveSpeed(40f);
        flyCam.setZoomSpeed(50f);
        flyCam.setRotationSpeed(2.0f);
        cam.setLocation(new Vector3f(0, 120, 140));
        cam.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
        cam.setFrustumPerspective(55f, (float) cam.getWidth() / cam.getHeight(), 0.05f, 8000f);
    }

    private void setupLights() {
        rootNode.getWorldLightList().clear();

        AmbientLight amb = new AmbientLight();
        amb.setColor(ColorRGBA.White.mult(0.55f));
        rootNode.addLight(amb);

        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-0.4f, -1.0f, -0.2f).normalizeLocal());
        sun.setColor(ColorRGBA.White.mult(0.75f));
        rootNode.addLight(sun);

        cameraLight = new PointLight();
        cameraLight.setColor(ColorRGBA.White.mult(1.15f));
        cameraLight.setRadius(220f);
        cameraLight.setPosition(cam.getLocation());
        rootNode.addLight(cameraLight);
    }

    // =========================================================================
    // Material helpers
    // =========================================================================

    private Material litNoCull(ColorRGBA color) {
        Material m = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        m.setBoolean("UseMaterialColors", true);
        m.setColor("Diffuse", color);
        m.setColor("Ambient", color.mult(0.6f));
        m.setColor("Specular", ColorRGBA.Black);
        m.setFloat("Shininess", 1f);
        m.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        return m;
    }

    private Material unshaded(ColorRGBA color, boolean transparent) {
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);
        RenderState rs = mat.getAdditionalRenderState();
        rs.setFaceCullMode(RenderState.FaceCullMode.Off);
        rs.setDepthTest(true);
        rs.setDepthWrite(true);
        rs.setBlendMode(transparent ? RenderState.BlendMode.Alpha : RenderState.BlendMode.Off);
        return mat;
    }

    private ColorRGBA pastel(int i) {
        float h = (i * 0.23f) % 1f;
        float p = 0.9f * (1f - 0.4f);
        float f = h * 6f - (float) Math.floor(h * 6f);
        float q = 0.9f * (1f - f * 0.4f);
        float t = 0.9f * (1f - (1f - f) * 0.4f);
        return switch ((int) Math.floor(h * 6f) % 6) {
            case 0  -> new ColorRGBA(0.9f, t, p, 1f);
            case 1  -> new ColorRGBA(q, 0.9f, p, 1f);
            case 2  -> new ColorRGBA(p, 0.9f, t, 1f);
            case 3  -> new ColorRGBA(p, q, 0.9f, 1f);
            case 4  -> new ColorRGBA(t, p, 0.9f, 1f);
            default -> new ColorRGBA(0.9f, p, q, 1f);
        };
    }

    // =========================================================================
    // Scene object helpers
    // =========================================================================

    private static Geometry geom(String name, Mesh mesh, Material mat) {
        Geometry g = new Geometry(name, mesh);
        g.setMaterial(mat);
        return g;
    }

    private static Geometry line(String name, Vector3f a, Vector3f b, Material mat) {
        Geometry g = new Geometry(name, new Line(a, b));
        g.setMaterial(mat);
        return g;
    }

    // =========================================================================
    // Terminal input
    // =========================================================================

    private static UserConfig readUserConfigFromTerminal() {
        Scanner sc = new Scanner(System.in);
        System.out.println("=== Organic Dungeon Generator ===");

        int roomCount   = readInt(sc, "Number of Rooms", 1, 50);
        int densityInt  = readInt(sc, "Density (1=SPARSE, 2=MEDIUM, 3=DENSE)", 1, 3);
        int minRoom     = readInt(sc, "Min Room Size", 3, 30);
        int maxRoom     = readInt(sc, "Max Room Size", minRoom, 30);

        CorridorPlacer.Density density = switch (densityInt) {
            case 1  -> CorridorPlacer.Density.SPARSE;
            case 3  -> CorridorPlacer.Density.DENSE;
            default -> CorridorPlacer.Density.MEDIUM;
        };

        System.out.print("Seed (empty for random): ");
        String seedLine = sc.nextLine().trim();
        long seed = seedLine.isEmpty() ? System.currentTimeMillis() : Long.parseLong(seedLine);

        GridEstimate ge = estimateGrid(roomCount, minRoom, maxRoom);

        System.out.printf("%nBuilding dungeon with:%n  rooms=%d  size=%d..%d  density=%s  seed=%d%n",
                roomCount, minRoom, maxRoom, density, seed);
        System.out.printf("Grid: sides=%d  edgeLength=%.1f%n", ge.sides, ge.edgeLength);

        return new UserConfig(roomCount, minRoom, maxRoom, density, ge.sides, ge.edgeLength, seed);
    }

    private static int readInt(Scanner sc, String label, int min, int max) {
        while (true) {
            System.out.print(label + " [" + min + ".." + max + "]: ");
            try {
                int v = Integer.parseInt(sc.nextLine().trim());
                if (v >= min && v <= max) return v;
                System.out.println("Value must be between " + min + " and " + max + ".");
            } catch (NumberFormatException e) {
                System.out.println("Please enter an integer.");
            }
        }
    }

    private static GridEstimate estimateGrid(int roomCount, int minRoom, int maxRoom) {
        float avgRoom = 0.5f * (minRoom + maxRoom);
        int   sides   = Math.max(6, Math.min(20, (int) Math.ceil(Math.sqrt(roomCount) * 1.4f + 1.0f)));
        float edge    = Math.max(6f, avgRoom * 0.70f);
        return new GridEstimate(sides, edge);
    }

    // =========================================================================
    // IntArrayList (compact int list to avoid boxing)
    // =========================================================================

    private static final class IntArrayList {
        private int[] a;
        private int size;
        IntArrayList(int cap) {
            a = new int[Math.max(4, cap)];
        }
        void add(int v) {
            if (size == a.length) a = Arrays.copyOf(a, a.length * 2);
            a[size++] = v;
        }
        int get(int i) {
            return a[i];
        }
        int size() {
            return size;
        }
    }
}