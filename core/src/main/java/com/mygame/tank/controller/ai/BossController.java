package com.mygame.tank.controller.ai;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.mygame.tank.config.GameConfig;
import com.mygame.tank.controller.TankController;
import com.mygame.tank.controller.UpdateContext;
import com.mygame.tank.entity.Projectile;
import com.mygame.tank.entity.Tank;
import com.mygame.tank.entity.component.turret.EnemyTurretComponent;

import java.util.Collections;
import java.util.List;

/**
 * Boss controller — wraps {@link EnemyController} for shared dead-state detection.
 * <p>
 * Boss-specific behaviour:
 * - Skips IDLE/CHASE; always active once spawned
 * - Circular orbit around its spawn centre
 * - 3-way spread shots (via TurretComponent)
 * - Phase 2 transition at 50 % HP → mini-tank spawning
 * - Bounces off walls: BossController has no idea where walls are. When
 * the world's CollisionSystem pushes the boss out of a wall, GameWorld
 * reports that push vector here via {@link #onWallCollision}, and the
 * controller reflects its orbit direction off it — same pattern as a
 * ball bouncing off a surface, driven entirely by the collision outcome
 * rather than the controller probing wall geometry itself.
 */
public class BossController implements TankController {

    public enum Phase {PHASE_1, PHASE_2}

    // ── Wrapped base AI (used only to query dead state consistently) ──────────
    private final EnemyController baseAI;

    // ── Boss-specific state ───────────────────────────────────────────────────
    private Phase phase;
    private float circleAngle;          // current orbit angle, degrees
    private final Vector2 circleCenter;   // fixed orbit centre
    private float miniSpawnTimer;
    private boolean pendingMiniSpawn;
    private float direction = 1f;       // 1 = clockwise, -1 = counter-clockwise; flips on wall bounce

    public BossController(float startX, float startY) {
        this.baseAI = new EnemyController();
        this.phase = Phase.PHASE_1;
        this.circleAngle = 0f;
        this.circleCenter = new Vector2(startX, startY);
        this.miniSpawnTimer = GameConfig.BOSS_MINI_SPAWN_INTERVAL;
        this.pendingMiniSpawn = false;
    }

    @Override
    public List<Projectile> update(Tank tank, float delta, UpdateContext ctx) {
        if (!tank.getHealth().isAlive()) return Collections.emptyList();
        if (!ctx.playerAlive) return Collections.emptyList();

        tank.getTurret().update(delta);

        if (tank.isStunned()) {
            return Collections.emptyList();
        }

        // ── Phase transition ────────────────────────────────────────────────
        if (phase == Phase.PHASE_1 &&
            tank.getHealth().getHp() <= tank.getHealth().getMaxHp()
                * GameConfig.BOSS_PHASE2_THRESHOLD) {
            phase = Phase.PHASE_2;
        }

        // ── Circular orbit ──────────────────────────────────────────────────
        circleAngle += GameConfig.BOSS_CIRCLE_SPEED_DEG * delta * direction;
        float rad = MathUtils.degreesToRadians * circleAngle;
        tank.getMovement().setPosition(
            circleCenter.x + MathUtils.cos(rad) * GameConfig.BOSS_CIRCLE_RADIUS,
            circleCenter.y + MathUtils.sin(rad) * GameConfig.BOSS_CIRCLE_RADIUS);
        tank.getMovement().faceTarget(ctx.playerPos);
        tank.getTurret().aimAt(tank.getMovement().getPosition(), ctx.playerPos);

        // ── Spread fire ─────────────────────────────────────────────────────
        // EMP-suppressed: boss keeps orbiting/tracking but its turret can't fire.
        List<Projectile> shots = tank.isEmpSuppressed()
            ? Collections.emptyList()
            : fireSpread(tank);

        // ── Phase 2 — mini-tank spawning ────────────────────────────────────
        if (phase == Phase.PHASE_2) {
            miniSpawnTimer -= delta;
            if (miniSpawnTimer <= 0f) {
                miniSpawnTimer = GameConfig.BOSS_MINI_SPAWN_INTERVAL;
                pendingMiniSpawn = true;
            }
        }

        return shots;
    }

    /**
     * Called by GameWorld when its wall-resolve step had to push this boss
     * out of a wall this frame. {@code wallNormal} is the push-out vector
     * CollisionSystem applied (points away from the wall surface).
     * <p>
     * Two things happen:
     * 1. circleAngle is resynced to the boss's corrected (post-push)
     * position — otherwise next frame's orbit formula would recompute
     * the old position and immediately drive the boss back into the wall.
     * 2. The orbit's tangential velocity is reflected across the wall
     * normal (v' = v - 2(v·n)n), and whichever orbit direction best
     * matches that reflected velocity is kept — so the boss bounces at
     * the angle it actually hit, not just a blind 180° reversal.
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

    private List<Projectile> fireSpread(Tank tank) {
        float base = tank.getTurret().getTurretAngle();
        float[] angles = {
            base,
            base + GameConfig.BOSS_SPREAD_ANGLE_DEG,
            base - GameConfig.BOSS_SPREAD_ANGLE_DEG
        };
        return ((EnemyTurretComponent) tank.getTurret()).trySpreadFire(
            tank.getMovement().getPosition(),
            tank.getStats().height,
            angles);
    }

    /**
     * Checks and clears the mini-spawn flag.
     * Returns {@code true} once per spawn cycle — caller should spawn mini-tanks.
     */
    public boolean consumePendingMiniSpawn() {
        if (pendingMiniSpawn) {
            pendingMiniSpawn = false;
            return true;
        }
        return false;
    }

    public Phase getPhase() {
        return phase;
    }

    public Vector2 getCircleCenter() {
        return circleCenter;
    }
}
