package com.mygame.tank.config;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.math.MathUtils;

/**
 * Persistent controls configuration (split-screen ratio for mobile input).
 *
 * <p>{@code splitRatio} is the fraction of screen height dedicated to the
 * <em>bottom</em> zone (move + aim joysticks).
 * The top {@code (1 - splitRatio)} portion is the FIRE zone.
 *
 * <p>Valid range: [{@link #MIN_SPLIT}, {@link #MAX_SPLIT}] (50%-85%).
 * Default: 55% joystick, 45% fire.
 *
 * <p>Call {@link #load()} once at app start (in {@code TankGame.create()}).
 * Call {@link #save()} after any change (e.g., from the settings screen).
 */
public final class ControlsConfig {

    private static final String PREFS_NAME = "tank_controls";
    private static final String KEY_SPLIT  = "split_ratio";

    /** Minimum fraction for joystick zone (50% = half screen minimum). */
    public static final float MIN_SPLIT     = 0.50f;
    /** Maximum fraction for joystick zone (85% = very small fire zone). */
    public static final float MAX_SPLIT     = 0.85f;
    /** Default split ratio. */
    public static final float DEFAULT_SPLIT = 0.55f;

    private static float splitRatio = DEFAULT_SPLIT;

    private ControlsConfig() {}

    // --- I/O ------------------------------------------------------------------

    /** Loads saved preferences from disk. Falls back to default on error. */
    public static void load() {
        try {
            Preferences p = Gdx.app.getPreferences(PREFS_NAME);
            splitRatio = MathUtils.clamp(
                p.getFloat(KEY_SPLIT, DEFAULT_SPLIT), MIN_SPLIT, MAX_SPLIT);
        } catch (Exception ignored) {
            splitRatio = DEFAULT_SPLIT;
        }
    }

    /** Persists the current split ratio. */
    public static void save() {
        try {
            Preferences p = Gdx.app.getPreferences(PREFS_NAME);
            p.putFloat(KEY_SPLIT, splitRatio);
            p.flush();
        } catch (Exception ignored) {}
    }

    // --- Getters / Setters ----------------------------------------------------

    /**
     * Fraction of screen height for the bottom (joystick) zone.
     * FIRE zone height = (1 - splitRatio) * screenHeight.
     */
    public static float getSplitRatio() { return splitRatio; }

    /** Sets the split ratio, clamped to [MIN_SPLIT, MAX_SPLIT]. */
    public static void setSplitRatio(float r) {
        splitRatio = MathUtils.clamp(r, MIN_SPLIT, MAX_SPLIT);
    }

    /** Resets to the default split ratio without saving. */
    public static void resetToDefault() {
        splitRatio = DEFAULT_SPLIT;
    }
}
