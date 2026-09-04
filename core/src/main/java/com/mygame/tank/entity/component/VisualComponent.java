package com.mygame.tank.entity.component;

import com.badlogic.gdx.graphics.Color;

/**
 * Holds all visual/rendering metadata for a tank.
 * <p>
 * Decouples rendering parameters from game logic:
 * <ul>
 *   <li>Texture IDs for body, turret, and bullet — resolved at render time via SpriteAssets.</li>
 *   <li>Scale multipliers applied on top of the hitbox size.</li>
 *   <li>A current tint {@link Color} that strategies/phases may update each frame
 *       (e.g. flickering red when Boss HP drops below 50%).</li>
 * </ul>
 *
 * Used for Player, Enemy, and Boss tanks alike.
 * {@link com.mygame.tank.render.GameRenderer} reads this component to determine
 * what to draw and how — there is no fallback to hard-coded constants.
 */
public class VisualComponent {

    // ─── Texture keys ─────────────────────────────────────────────────────────
    private String bodyTextureId;
    private String turretTextureId;
    private String bulletTextureId;

    // ─── Scale ────────────────────────────────────────────────────────────────
    /** Multiplier applied to hitbox width/height when drawing the body sprite. */
    private float bodyScale;
    /** Multiplier applied to hitbox width when drawing the turret sprite. */
    private float turretScale;

    // ─── Tint ─────────────────────────────────────────────────────────────────
    /**
     * Current render tint — copied to SpriteBatch before each draw call.
     * Default is {@link Color#WHITE} (no tint).
     * Mutated by phase strategies to produce visual feedback (flash, darken, etc.).
     */
    private final Color currentTint = new Color(Color.WHITE);

    // ─── Constructor ─────────────────────────────────────────────────────────

    public VisualComponent(String bodyTextureId,
                           String turretTextureId,
                           String bulletTextureId,
                           float  bodyScale,
                           float  turretScale) {
        this.bodyTextureId   = bodyTextureId;
        this.turretTextureId = turretTextureId;
        this.bulletTextureId = bulletTextureId;
        this.bodyScale       = bodyScale;
        this.turretScale     = turretScale;
    }

    // ─── Tint helpers ─────────────────────────────────────────────────────────

    /**
     * Sets the current tint to a solid colour and alpha.
     * Renderer will call {@link #getCurrentTint()} to read this value.
     */
    public void setTint(float r, float g, float b, float a) {
        currentTint.set(r, g, b, a);
    }

    /** Resets tint to fully opaque white (no colour effect). */
    public void resetTint() {
        currentTint.set(Color.WHITE);
    }

    // ─── Getters / Setters ────────────────────────────────────────────────────

    public String getBodyTextureId()   { return bodyTextureId; }
    public String getTurretTextureId() { return turretTextureId; }
    public String getBulletTextureId() { return bulletTextureId; }

    public float getBodyScale()   { return bodyScale; }
    public float getTurretScale() { return turretScale; }

    /** Returns the live tint colour — callers must NOT store a reference to this object. */
    public Color getCurrentTint() { return currentTint; }

    public void setBodyTextureId(String id)   { this.bodyTextureId = id; }
    public void setTurretTextureId(String id) { this.turretTextureId = id; }
    public void setBulletTextureId(String id) { this.bulletTextureId = id; }
    public void setBodyScale(float s)   { this.bodyScale = s; }
    public void setTurretScale(float s) { this.turretScale = s; }
}
