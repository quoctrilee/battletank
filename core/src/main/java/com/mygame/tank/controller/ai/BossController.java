package com.mygame.tank.controller.ai;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.mygame.tank.controller.TankController;
import com.mygame.tank.controller.UpdateContext;
import com.mygame.tank.controller.ai.strategy.BossFireStrategy;
import com.mygame.tank.controller.ai.strategy.BossMoveStrategy;
import com.mygame.tank.controller.ai.strategy.BossPhaseStrategy;
import com.mygame.tank.entity.Projectile;
import com.mygame.tank.entity.Tank;

import java.util.Collections;
import java.util.List;

/**
 * General-purpose boss controller.
 * <p>
 * Acts as the <em>Context</em> in the Strategy pattern: it holds shared state
 * (spawn centre, orbit angle, direction) and delegates all behaviour to three
 * interchangeable strategies:
 * <ul>
 *   <li>{@link BossMoveStrategy}  — decides how the boss moves each frame</li>
 *   <li>{@link BossFireStrategy}  — decides what projectiles to fire each frame</li>
 *   <li>{@link BossPhaseStrategy} — watches HP, drives phase transitions and mini-tank spawns</li>
 * </ul>
 *
 * <h3>Shared state exposed to strategies</h3>
 * Strategies read/write via getters/setters on this class:
 * {@link #getCircleCenter()}, {@link #getCircleAngle()}, {@link #setCircleAngle},
 * {@link #getDirection()}, {@link #setDirection}, {@link #getPhaseIndex()}.
 *
 * <h3>Wall bounce</h3>
 * {@link #onWallCollision} is still called by GameWorld to resync the orbit angle
 * and flip the orbit direction after a wall push-out (used by OrbitMoveStrategy).
 */
public class BossController implements TankController {

    // ─── Shared state (exposed to strategies) ────────────────────────────────
    private float   circleAngle   = 0f;
    private final Vector2 circleCenter;
    private float   direction     = 1f;   // +1 clockwise, -1 counter-clockwise

    // ─── Strategies ───────────────────────────────────────────────────────────
    private final BossMoveStrategy  moveStrategy;
    private final BossFireStrategy  fireStrategy;
    private final BossPhaseStrategy phaseStrategy;

    // ─── Constructor ─────────────────────────────────────────────────────────

    public BossController(float startX, float startY,
                          BossMoveStrategy  moveStrategy,
                          BossFireStrategy  fireStrategy,
                          BossPhaseStrategy phaseStrategy) {
        this.circleCenter  = new Vector2(startX, startY);
        this.moveStrategy  = moveStrategy;
        this.fireStrategy  = fireStrategy;
        this.phaseStrategy = phaseStrategy;
    }

    // ─── TankController ───────────────────────────────────────────────────────

    @Override
    public List<Projectile> update(Tank tank, float delta, UpdateContext ctx) {
        if (!tank.getHealth().isAlive()) return Collections.emptyList();

        // Stand still and idle until the area is activated (player enters room)
        if (!ctx.areaActive) {
            tank.getMovement().setPosition(circleCenter.x, circleCenter.y);
            return Collections.emptyList();
        }

        if (!ctx.playerAlive) return Collections.emptyList();

        tank.getTurret().update(delta);

        if (tank.isStunned()) return Collections.emptyList();

        // ── Phase update (drives tint, spawn queues, phase transitions) ──────
        phaseStrategy.update(tank, delta, this);

        // ── Move ─────────────────────────────────────────────────────────────
        moveStrategy.update(tank, delta, ctx.playerPos, this);

        // ── Fire (blocked when EMP-suppressed) ───────────────────────────────
        if (tank.isEmpSuppressed()) return Collections.emptyList();
        return fireStrategy.fire(tank, delta, ctx.playerPos, this);
    }

    // ─── Wall collision (called by GameWorld after collision resolve) ─────────

    /**
     * Resyncs orbit angle and reflects orbit direction after a wall push-out.
     * Only relevant when using {@link com.mygame.tank.controller.ai.strategy.OrbitMoveStrategy};
     * other strategies ignore this or handle wall bounce themselves.
     */
    public void onWallCollision(Tank tank, Vector2 wallNormal) {
        if (wallNormal.isZero(0.0001f)) return;

        Vector2 pos = tank.getMovement().getPosition();
        Vector2 rel = new Vector2(pos.x - circleCenter.x, pos.y - circleCenter.y);
        circleAngle = MathUtils.radiansToDegrees * MathUtils.atan2(rel.y, rel.x);

        float rad = MathUtils.degreesToRadians * circleAngle;
        Vector2 tangent = new Vector2(-MathUtils.sin(rad), MathUtils.cos(rad));
        Vector2 velocity = tangent.cpy().scl(direction);

        Vector2 normal = wallNormal.cpy().nor();
        Vector2 reflected = velocity.cpy().sub(normal.cpy().scl(2f * velocity.dot(normal)));

        direction = (reflected.dot(tangent) >= 0f) ? 1f : -1f;
    }

    // ─── Mini-spawn passthrough ───────────────────────────────────────────────

    /**
     * Returns and clears the number of mini-tanks to spawn this frame.
     * Called by GameWorld after {@link #update}.
     */
    public int consumePendingMiniSpawn() {
        return phaseStrategy.consumePendingMiniSpawn();
    }

    // ─── Shared state accessors (used by strategies) ─────────────────────────

    public Vector2 getCircleCenter() { return circleCenter; }
    public float   getCircleAngle()  { return circleAngle; }
    public void    setCircleAngle(float a) { this.circleAngle = a; }
    public float   getDirection()    { return direction; }
    public void    setDirection(float d)   { this.direction = d; }

    /** Convenience: delegates to phaseStrategy so move/fire strategies can scale per phase. */
    public int getPhaseIndex() { return phaseStrategy.getCurrentPhaseIndex(); }
}
