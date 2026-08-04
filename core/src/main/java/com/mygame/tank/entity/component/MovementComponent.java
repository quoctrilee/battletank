package com.mygame.tank.entity.component;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

/**
 * Owns position, bounding box, and body orientation.
 *
 * Responsibilities: movement and collision shape only.
 * HP and combat belong to other components.
 */
public final class MovementComponent {

    private final Vector2   position;
    private final Rectangle bounds;
    private float           bodyAngle;     // degrees: 0 = right, 90 = up
    private float           currentSpeed;  // signed, px/s
    private final float     maxSpeed;
    private final float     acceleration;  // px/s²
    private final float     deceleration;  // px/s²

    public MovementComponent(float x, float y, float width, float height,
                             float maxSpeed, float acceleration, float deceleration) {
        this.position     = new Vector2(x, y);
        this.bounds       = new Rectangle(x - width / 2f, y - height / 2f, width, height);
        this.bodyAngle    = 90f;
        this.currentSpeed = 0f;
        this.maxSpeed     = maxSpeed;
        this.acceleration = acceleration;
        this.deceleration = deceleration;
    }

    /**
     * Player-style drive: accelerates or decelerates along the current bodyAngle.
     * @param accelerating true = speed up, false = coast to stop
     */
    public void driveForward(boolean accelerating, float delta) {
        if (accelerating) {
            currentSpeed = Math.min(currentSpeed + acceleration * delta, maxSpeed);
        } else {
            currentSpeed = Math.max(0f, currentSpeed - deceleration * delta);
        }
        float rad   = MathUtils.degreesToRadians * bodyAngle;
        position.x += MathUtils.cos(rad) * currentSpeed * delta;
        position.y += MathUtils.sin(rad) * currentSpeed * delta;
        bounds.setCenter(position.x, position.y);
    }

    /**
     * Enemy-style move: steers directly toward a target at the given speed.
     * Updates bodyAngle to face the target.
     */
    public void moveToward(Vector2 target, float speed, float delta) {
        float dx  = target.x - position.x;
        float dy  = target.y - position.y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.01f) return;
        position.x += (dx / len) * speed * delta;
        position.y += (dy / len) * speed * delta;
        bodyAngle   = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees;
        bounds.setCenter(position.x, position.y);
    }

    /** Instantly teleport to an absolute world position (used by Boss orbit). */
    public void setPosition(float x, float y) {
        position.set(x, y);
        bounds.setCenter(x, y);
    }

    /** Snap body to face a target (does NOT move). */
    public void faceTarget(Vector2 target) {
        float dx  = target.x - position.x;
        float dy  = target.y - position.y;
        bodyAngle = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees;
    }

    /** Override body angle directly (e.g., WASD 8-direction snap for player). */
    public void setBodyAngle(float angleDeg) { this.bodyAngle = angleDeg; }

    /** Called by CollisionSystem to push entity out of an overlap. */
    public void resolvePosition(float newX, float newY) {
        position.set(newX, newY);
        bounds.setCenter(newX, newY);
    }

    // ─── Getters ─────────────────────────────────────────────────────────────
    public Vector2   getPosition()     { return position; }
    public Rectangle getBounds()       { return bounds; }
    public float     getBodyAngle()    { return bodyAngle; }
    public float     getCurrentSpeed() { return currentSpeed; }
}
