package com.mygame.tank.config;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads {@link BossConfig} instances from JSON files in the {@code boss/} asset folder.
 * <p>
 * JSON format (single file per boss, e.g. {@code boss/iron_guard.json}):
 * <pre>{@code
 * {
 *   "id": "iron_guard",
 *   "maxHp": 600,
 *   "width": 56, "height": 56,
 *   "fireRate": 1.8, "bulletSpeed": 260, "damage": 18,
 *   "bodyTextureId":   "boss_iron_body",
 *   "turretTextureId": "boss_iron_turret",
 *   "bulletTextureId": "boss_iron_bullet",
 *   "bodyScale": 1.6, "turretScale": 1.6,
 *   "moveStrategyClass":  "OrbitMoveStrategy",
 *   "fireStrategyClass":  "SpreadFireStrategy",
 *   "phaseStrategyClass": "ThresholdPhaseStrategy",
 *   "params": {
 *     "orbitRadius": 110, "orbitSpeedDeg": 60,
 *     "spreadAngleDeg": 30, "spreadCount": 3,
 *     "phase2Threshold": 0.5, "phase3Threshold": 0.25,
 *     "miniSpawnInterval": 12, "miniTankCount": 2,
 *     "miniSpawnIntervalP3": 7, "miniTankCountP3": 3
 *   }
 * }
 * }</pre>
 *
 * Call {@link #loadAll()} once at startup; then retrieve by id with {@link #get(String)}
 * or get the full list with {@link #getAll()}.
 */
public class BossConfigLoader {

    private static final String TAG = "BossConfigLoader";
    private static final String BOSS_DIR = "boss/";

    private final Map<String, BossConfig> configById = new HashMap<>();
    private final List<BossConfig>        allConfigs = new ArrayList<>();

    // ─── Loading ─────────────────────────────────────────────────────────────

    /**
     * Known boss config filenames — add one entry here when adding a new boss JSON.
     * Direct-file loading is used because {@code Gdx.files.internal(dir).isDirectory()}
     * returns {@code false} on LWJGL3 Desktop (classpath-backed paths are not enumerable).
     */
    private static final String[] BOSS_FILES = {
        "iron_guard.json",
        "viper.json",
        "siege.json",
    };

    /**
     * Loads each known boss JSON file from the {@code boss/} asset folder and
     * caches the resulting {@link BossConfig} objects.
     * Call once during game initialisation (e.g. in {@code GameScreen.show()}).
     */
    public void loadAll() {
        configById.clear();
        allConfigs.clear();

        Json json = new Json();
        json.addClassTag("BossConfig", BossConfig.class);

        for (String filename : BOSS_FILES) {
            FileHandle file = Gdx.files.internal(BOSS_DIR + filename);
            if (!file.exists()) {
                Gdx.app.error(TAG, "Boss config not found: " + BOSS_DIR + filename);
                continue;
            }
            try {
                BossConfig cfg = parseConfig(json, file);
                if (cfg == null) continue;
                configById.put(cfg.id, cfg);
                allConfigs.add(cfg);
                Gdx.app.log(TAG, "Loaded boss config: " + cfg.id);
            } catch (Exception e) {
                Gdx.app.error(TAG, "Failed to parse " + filename + ": " + e.getMessage());
            }
        }

        Gdx.app.log(TAG, "Total boss configs loaded: " + allConfigs.size());
    }

    /**
     * Parse one JSON file into a {@link BossConfig}, including the nested
     * {@code params} map whose values can be number or string.
     */
    @SuppressWarnings("unchecked")
    private BossConfig parseConfig(Json json, FileHandle file) {
        String text = file.readString("UTF-8");
        JsonValue root = new com.badlogic.gdx.utils.JsonReader().parse(text);
        if (root == null) return null;

        // Use LibGDX Json to fill primitive fields automatically
        BossConfig cfg = new BossConfig();
        cfg.id                  = root.getString("id", cfg.id);
        cfg.maxHp               = root.getFloat("maxHp", cfg.maxHp);
        cfg.width               = root.getFloat("width", cfg.width);
        cfg.height              = root.getFloat("height", cfg.height);
        cfg.fireRate            = root.getFloat("fireRate", cfg.fireRate);
        cfg.bulletSpeed         = root.getFloat("bulletSpeed", cfg.bulletSpeed);
        cfg.damage              = root.getFloat("damage", cfg.damage);
        cfg.turretRotationSpeed = root.getFloat("turretRotationSpeed", cfg.turretRotationSpeed);
        cfg.bodyTextureId       = root.getString("bodyTextureId", cfg.bodyTextureId);
        cfg.turretTextureId     = root.getString("turretTextureId", cfg.turretTextureId);
        cfg.bulletTextureId     = root.getString("bulletTextureId", cfg.bulletTextureId);
        cfg.bodyScale           = root.getFloat("bodyScale", cfg.bodyScale);
        cfg.turretScale         = root.getFloat("turretScale", cfg.turretScale);
        cfg.moveStrategyClass   = root.getString("moveStrategyClass", cfg.moveStrategyClass);
        cfg.fireStrategyClass   = root.getString("fireStrategyClass", cfg.fireStrategyClass);
        cfg.phaseStrategyClass  = root.getString("phaseStrategyClass", cfg.phaseStrategyClass);

        // Parse params map manually (mixed-type values)
        JsonValue paramsNode = root.get("params");
        if (paramsNode != null && paramsNode.isObject()) {
            Map<String, Object> params = new HashMap<>();
            for (JsonValue entry = paramsNode.child; entry != null; entry = entry.next) {
                if (entry.isDouble())        params.put(entry.name, entry.asFloat());
                else if (entry.isLong())     params.put(entry.name, entry.asInt());
                else if (entry.isBoolean())  params.put(entry.name, entry.asBoolean());
                else if (entry.isString())   params.put(entry.name, entry.asString());
            }
            cfg.params = params;
        }

        return cfg;
    }

    // ─── Accessors ───────────────────────────────────────────────────────────

    /** Returns the config with the given id, or {@code null} if not found. */
    public BossConfig get(String id) {
        return configById.get(id);
    }

    /** Returns an unmodifiable view of all loaded configs in load order. */
    public List<BossConfig> getAll() {
        return Collections.unmodifiableList(allConfigs);
    }

    /** Returns {@code true} if at least one config was loaded. */
    public boolean hasConfigs() {
        return !allConfigs.isEmpty();
    }

    /**
     * Returns configs in load order, cycling by index.
     * Useful for assigning a different boss per boss room (index wraps around).
     */
    public BossConfig getByIndex(int index) {
        if (allConfigs.isEmpty()) return null;
        return allConfigs.get(index % allConfigs.size());
    }
}
