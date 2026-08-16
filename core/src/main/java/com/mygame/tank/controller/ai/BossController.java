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
 * - Phase system:
 *   <ul>
 *     <li>PHASE_1: HP > 50% — orbit + spread fire</li>
 *     <li>PHASE_2: HP ≤ 50% — spawn mini-tanks ngay khi transition,
 *         sau đó định kỳ mỗi {@link GameConfig#BOSS_MINI_SPAWN_INTERVAL}s</li>
 *     <li>PHASE_3: HP ≤ 25% — spawn thêm ngay khi transition,
 *         sau đó spawn nhanh hơn mỗi {@link GameConfig#BOSS_PHASE3_MINI_SPAWN_INTERVAL}s
 *         với số lượng lớn hơn ({@link GameConfig#BOSS_PHASE3_MINI_TANK_COUNT})</li>
 *   </ul>
 * - Bounces off walls: GameWorld reports push vector via {@link #onWallCollision}.
 */
public class BossController implements TankController {

    public enum Phase {PHASE_1, PHASE_2, PHASE_3}

    // ── Boss-specific state ───────────────────────────────────────────────────
    private Phase phase;
    private float circleAngle;          // current orbit angle, degrees
    private final Vector2 circleCenter;   // fixed orbit centre
    private float miniSpawnTimer;
    /**
     * Số mini-tank cần spawn trong frame này.
     * 0 = không spawn. Được reset về 0 sau khi {@link #consumePendingMiniSpawn()} đọc.
     */
    private int pendingMiniSpawnCount;
    private float direction = 1f;       // 1 = clockwise, -1 = counter-clockwise; flips on wall bounce

    public BossController(float startX, float startY) {
        this.phase = Phase.PHASE_1;
        this.circleAngle = 0f;
        this.circleCenter = new Vector2(startX, startY);
        this.miniSpawnTimer = GameConfig.BOSS_MINI_SPAWN_INTERVAL;
        this.pendingMiniSpawnCount = 0;
    }

    @Override
    public List<Projectile> update(Tank tank, float delta, UpdateContext ctx) {
        if (!tank.getHealth().isAlive()) return Collections.emptyList();

        // Khi chưa active (Player chưa bước vào phòng / cửa chưa mở), Boss đứng yên ở giữa phòng
        if (!ctx.areaActive) {
            tank.getMovement().setPosition(circleCenter.x, circleCenter.y);
            return Collections.emptyList();
        }

        if (!ctx.playerAlive) return Collections.emptyList();

        tank.getTurret().update(delta);

        if (tank.isStunned()) {
            return Collections.emptyList();
        }

        // ── Phase transition ────────────────────────────────────────────────
        checkPhaseTransitions(tank);

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

        // ── Phase 2/3 — periodic mini-tank spawning ─────────────────────────
        if (phase == Phase.PHASE_2 || phase == Phase.PHASE_3) {
            miniSpawnTimer -= delta;
            if (miniSpawnTimer <= 0f) {
                // Reset timer theo phase hiện tại
                miniSpawnTimer = (phase == Phase.PHASE_3)
                    ? GameConfig.BOSS_PHASE3_MINI_SPAWN_INTERVAL
                    : GameConfig.BOSS_MINI_SPAWN_INTERVAL;
                pendingMiniSpawnCount += (phase == Phase.PHASE_3)
                    ? GameConfig.BOSS_PHASE3_MINI_TANK_COUNT
                    : GameConfig.BOSS_MINI_TANK_COUNT;
            }
        }

        return shots;
    }

    /**
     * Kiểm tra và xử lý chuyển phase.
     * Khi chuyển phase mới → spawn mini-tank ngay lập tức (immediate spawn).
     */
    private void checkPhaseTransitions(Tank tank) {
        float hpRatio = tank.getHealth().getHp() / tank.getHealth().getMaxHp();

        if (phase == Phase.PHASE_1 && hpRatio <= GameConfig.BOSS_PHASE2_THRESHOLD) {
            phase = Phase.PHASE_2;
            miniSpawnTimer = GameConfig.BOSS_MINI_SPAWN_INTERVAL;
            // Spawn ngay lập tức khi vào Phase 2
            pendingMiniSpawnCount += GameConfig.BOSS_MINI_TANK_COUNT;
        }

        if (phase == Phase.PHASE_2 && hpRatio <= GameConfig.BOSS_PHASE3_THRESHOLD) {
            phase = Phase.PHASE_3;
            miniSpawnTimer = GameConfig.BOSS_PHASE3_MINI_SPAWN_INTERVAL;
            // Spawn ngay lập tức khi vào Phase 3
            pendingMiniSpawnCount += GameConfig.BOSS_PHASE3_MINI_TANK_COUNT;
        }
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
     * Checks and clears the mini-spawn pending count.
     * Returns the number of mini-tanks that should be spawned this frame (≥ 0).
     * Returns 0 if no spawn is due — caller must check {@code > 0} before spawning.
     */
    public int consumePendingMiniSpawn() {
        int count = pendingMiniSpawnCount;
        pendingMiniSpawnCount = 0;
        return count;
    }

    public Phase getPhase() {
        return phase;
    }

    public Vector2 getCircleCenter() {
        return circleCenter;
    }
}
