package com.mygame.tank.entity.component;

/**
 * Immutable data class holding per-tank dimensional and movement statistics.
 */
public final class TankStats {

    public final float width;
    public final float height;
    public final float maxSpeed;
    public final float acceleration;
    public final float deceleration;

    public TankStats(float width, float height,
                     float maxSpeed, float acceleration, float deceleration) {
        this.width = width;
        this.height = height;
        this.maxSpeed = maxSpeed;
        this.acceleration = acceleration;
        this.deceleration = deceleration;
    }
}
