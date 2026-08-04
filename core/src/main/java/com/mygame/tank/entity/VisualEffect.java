package com.mygame.tank.entity;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;

/**
 * Purely cosmetic visual effects (explosions, muzzle flashes) with no gameplay impact.
 */
public class VisualEffect {

    public enum Type {
        EXPLOSION_AOE,      // large expanding ring + flash
        EXPLOSION_SMALL,    // small hit puff
        MUZZLE_FLASH,       // cone/flash at barrel
        LASER_HIT           // sparks for laser continuous hit
    }

    private final Vector2 position;
    private final Type type;
    private final Color color;
    private final float maxLifetime;
    private float lifetime;

    // For specific effects
    private final float radius;
    private final float angle; // for directional effects like muzzle flash

    public VisualEffect(Type type, float x, float y, float maxLifetime, Color color, float radius, float angle) {
        this.type = type;
        this.position = new Vector2(x, y);
        this.maxLifetime = maxLifetime;
        this.lifetime = maxLifetime;
        this.color = color;
        this.radius = radius;
        this.angle = angle;
    }

    public static VisualEffect aoeExplosion(float x, float y, float radius, Color color) {
        return new VisualEffect(Type.EXPLOSION_AOE, x, y, 0.4f, color, radius, 0f);
    }

    public static VisualEffect smallHit(float x, float y, Color color) {
        return new VisualEffect(Type.EXPLOSION_SMALL, x, y, 0.2f, color, 15f, 0f);
    }

    public static VisualEffect muzzleFlash(float x, float y, float angle, Color color) {
        return new VisualEffect(Type.MUZZLE_FLASH, x, y, 0.1f, color, 20f, angle);
    }

    public void update(float delta) {
        lifetime -= delta;
    }

    public boolean isAlive() {
        return lifetime > 0f;
    }

    public float getProgress() {
        return 1f - Math.max(0f, lifetime / maxLifetime);
    }

    public Type getType() {
        return type;
    }

    public Vector2 getPosition() {
        return position;
    }

    public Color getColor() {
        return color;
    }

    public float getRadius() {
        return radius;
    }

    public float getAngle() {
        return angle;
    }
}
