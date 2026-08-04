package com.mygame.tank.world;

import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.mygame.tank.config.GameConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapManager {
    private static final String LAYER_COLLISION    = "CollisionLayer";
    private static final String LAYER_SPAWN_POINTS = "SpawnPoints";
    private static final String LAYER_AREA_TRIGGERS= "AreaTriggers";
    private static final String LAYER_DOORS        = "Doors";

    private TiledMap tiledMap;

    private final List<Rectangle>         collisionRects = new ArrayList<>();
    private final Map<String, Rectangle>  doorRects      = new HashMap<>();  // key = areaId
    private final Map<String, Rectangle>  areaBounds     = new HashMap<>();  // key = areaId
    private final Map<String, List<Vector2>> enemySpawns = new HashMap<>();  // key = areaId
    private Vector2 playerSpawn;
    private Vector2 bossSpawn;

    public void load(String tmxPath) {
        tiledMap = new TmxMapLoader().load(tmxPath);
        parseCollisionLayer();
        parseDoors();
        parseAreaTriggers();
        parseSpawnPoints();
    }

    // ─── Layer Parsers ────────────────────────────────────────────────────────

    private void parseCollisionLayer() {
        MapLayer layer = tiledMap.getLayers().get(LAYER_COLLISION);
        if (layer == null) return;
        for (MapObject obj : layer.getObjects()) {
            if (obj instanceof RectangleMapObject) {
                Rectangle tiled = ((RectangleMapObject) obj).getRectangle();
                collisionRects.add(tiledRectToWorld(tiled));
            }
        }
    }

    private void parseDoors() {
        MapLayer layer = tiledMap.getLayers().get(LAYER_DOORS);
        if (layer == null) return;
        for (MapObject obj : layer.getObjects()) {
            if (!(obj instanceof RectangleMapObject)) continue;
            String name = obj.getName();
            if (name == null || name.isEmpty()) continue;
            Rectangle tiled = ((RectangleMapObject) obj).getRectangle();
            doorRects.put(name, tiledRectToWorld(tiled));
        }
    }

    private void parseAreaTriggers() {
        MapLayer layer = tiledMap.getLayers().get(LAYER_AREA_TRIGGERS);
        if (layer == null) return;
        for (MapObject obj : layer.getObjects()) {
            if (!(obj instanceof RectangleMapObject)) continue;
            String name = obj.getName();
            if (name == null || name.isEmpty()) continue;
            Rectangle tiled = ((RectangleMapObject) obj).getRectangle();
            areaBounds.put(name, tiledRectToWorld(tiled));
        }
    }

    private void parseSpawnPoints() {
        MapLayer layer = tiledMap.getLayers().get(LAYER_SPAWN_POINTS);
        if (layer == null) {
            // Fallback defaults so the game can still run without a TMX
            playerSpawn = new Vector2(264f, 1656f);
            bossSpawn   = new Vector2(960f, 168f);
            return;
        }

        for (MapObject obj : layer.getObjects()) {
            if (!(obj instanceof RectangleMapObject)) continue;
            String name = obj.getName();
            if (name == null || name.isEmpty()) continue;

            Rectangle tiled = ((RectangleMapObject) obj).getRectangle();
            // For spawn points (1×1 rects), use top-left corner as the point position
            Vector2 worldPos = tiledPointToWorld(tiled.x + tiled.width / 2f,
                                                 tiled.y + tiled.height / 2f);

            if (name.equals("player_spawn")) {
                playerSpawn = worldPos;
            } else if (name.equals("boss_spawn")) {
                bossSpawn = worldPos;
            } else if (name.startsWith("enemy_")) {
                // Read areaId property, fall back to name-based extraction
                String areaId = obj.getProperties().get("areaId", String.class);
                if (areaId == null) areaId = extractAreaFromName(name);
                enemySpawns.computeIfAbsent(areaId, k -> new ArrayList<>()).add(worldPos);
            }
        }

        if (playerSpawn == null) playerSpawn = new Vector2(264f, 1656f);
        if (bossSpawn   == null) bossSpawn   = new Vector2(960f, 168f);
    }

    // ─── Coordinate Conversion ────────────────────────────────────────────────

    /**
     * Convert a Tiled rectangle (y-down, origin at top-left) to
     * a LibGDX rectangle (y-up, origin at bottom-left).
     */
    private Rectangle tiledRectToWorld(Rectangle r) {
        float worldY = GameConfig.MAP_HEIGHT - r.y - r.height;
        return new Rectangle(r.x, worldY, r.width, r.height);
    }

    /** Convert a Tiled point (y-down) to a LibGDX world point (y-up). */
    private Vector2 tiledPointToWorld(float tiledX, float tiledY) {
        return new Vector2(tiledX, GameConfig.MAP_HEIGHT - tiledY);
    }

    /** Extract areaId from spawn-point name, e.g. "enemy_a_1" → "A". */
    private String extractAreaFromName(String name) {
        String lower = name.toLowerCase();
        if (lower.contains("_a_") || lower.endsWith("_a")) return "A";
        if (lower.contains("_b_") || lower.endsWith("_b")) return "B";
        if (lower.contains("_c_") || lower.endsWith("_c")) return "C";
        return "A"; // default
    }

    // ─── Getters ─────────────────────────────────────────────────────────────
    public TiledMap                       getTiledMap()      { return tiledMap; }
    public List<Rectangle>                getCollisionRects(){ return collisionRects; }
    public Map<String, Rectangle>         getDoorRects()     { return doorRects; }
    public Map<String, Rectangle>         getAreaBounds()    { return areaBounds; }
    public Map<String, List<Vector2>>     getEnemySpawns()   { return enemySpawns; }
    public Vector2                        getPlayerSpawn()   { return playerSpawn; }
    public Vector2                        getBossSpawn()     { return bossSpawn; }

    public void dispose() {
        if (tiledMap != null) tiledMap.dispose();
    }
}
