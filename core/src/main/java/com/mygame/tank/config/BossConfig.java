package com.mygame.tank.config;

import java.util.Map;

/**
 * Immutable data object describing one boss type.
 * Instances are loaded by {@link BossConfigLoader} from JSON and shared across
 * the runtime — never mutate fields after construction.
 *
 * <h3>Strategy classes</h3>
 * {@code moveStrategyClass}, {@code fireStrategyClass}, and {@code phaseStrategyClass}
 * are simple short class names (e.g. {@code "OrbitMoveStrategy"}) resolved by
 * {@link com.mygame.tank.controller.ai.StrategyFactory}.
 *
 * <h3>Strategy params</h3>
 * {@code params} carries every strategy-specific numeric or string value (orbit radius,
 * spread count, phase thresholds, etc.).  Keys are plain strings; values may be
 * {@code Float}, {@code Integer}, {@code Boolean}, or {@code String}.
 * Each strategy is responsible for reading its own keys and casting safely.
 */
public class BossConfig {

    // ─── Identity ─────────────────────────────────────────────────────────────
    /** Unique string key — also used as the areaId suffix ("BOSS_<id>"). */
    public String id = "unknown";

    // ─── Core stats ───────────────────────────────────────────────────────────
    public float maxHp       = 1000f;
    public float width       = 56f;
    public float height      = 56f;
    public float fireRate    = 2f;
    public float bulletSpeed = 300f;
    public float damage      = 18f;
    /** Turret rotation speed in degrees/second. */
    public float turretRotationSpeed = 180f;

    // ─── Visual ───────────────────────────────────────────────────────────────
    /** Key in SpriteAssets for body texture, e.g. "boss_iron_body". */
    public String bodyTextureId    = "boss_iron_body";
    /** Key in SpriteAssets for turret texture. */
    public String turretTextureId  = "boss_iron_turret";
    /** Key in SpriteAssets for bullet texture. */
    public String bulletTextureId  = "boss_iron_bullet";
    /**
     * Visual render scale multiplier applied on top of the hitbox width.
     * 1.6 = sprite drawn at width*1.6 to match current boss rendering.
     */
    public float bodyScale   = 1.6f;
    public float turretScale = 1.6f;

    // ─── Strategy class names ─────────────────────────────────────────────────
    /** Short class name inside {@code com.mygame.tank.controller.ai.strategy}. */
    public String moveStrategyClass  = "OrbitMoveStrategy";
    public String fireStrategyClass  = "SpreadFireStrategy";
    public String phaseStrategyClass = "ThresholdPhaseStrategy";

    // ─── Per-strategy parameters ──────────────────────────────────────────────
    /**
     * Flat map of arbitrary parameters forwarded to each strategy.
     * Strategies cast values via helper {@link #getFloat}, {@link #getInt}, etc.
     * <p>
     * LibGDX Json deserialises JSON numbers as {@code Float} by default; keep that
     * in mind when writing strategies.
     */
    public Map<String, Object> params;

    // ─── Param helpers ────────────────────────────────────────────────────────

    public float getFloat(String key, float defaultValue) {
        if (params == null) return defaultValue;
        Object v = params.get(key);
        if (v instanceof Number) return ((Number) v).floatValue();
        return defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        if (params == null) return defaultValue;
        Object v = params.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        return defaultValue;
    }

    public boolean getBool(String key, boolean defaultValue) {
        if (params == null) return defaultValue;
        Object v = params.get(key);
        if (v instanceof Boolean) return (Boolean) v;
        return defaultValue;
    }

    public String getString(String key, String defaultValue) {
        if (params == null) return defaultValue;
        Object v = params.get(key);
        if (v instanceof String) return (String) v;
        return defaultValue;
    }
}
