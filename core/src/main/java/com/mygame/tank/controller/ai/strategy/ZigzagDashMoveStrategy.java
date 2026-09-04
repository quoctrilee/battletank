package com.mygame.tank.controller.ai.strategy;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.mygame.tank.config.BossConfig;
import com.mygame.tank.controller.ai.BossController;
import com.mygame.tank.entity.Tank;

/**
 * Boss 2 — Viper: aggressive zigzag dash pattern that flows into an orbit once close.
 * <p>
 * Behaviour:
 * <ul>
 *   <li>Far away: locks onto player direction and dashes in a zigzag (alternating
 *       left/right offsets), closing distance quickly.</li>
 *   <li>The interval between side-flips is randomised within a small window each time,
 *       so the S-curve rhythm never becomes fully predictable / kite-able.</li>
 *   <li>As the boss nears {@code orbitDist}, its forward (toward-player) component
 *       smoothly fades out over {@code approachFalloff} units — it never "hits a wall",
 *       it just naturally curls sideways into circling the player instead of stopping dead.</li>
 *   <li>Phase 0 (phaseIndex == 0): moderate speed, wider zigzag angle.</li>
 *   <li>Phase 1+ (phaseIndex >= 1): faster dash, tighter zigzag — feels more frenzied.</li>
 * </ul>
 *
 * Reads params: dashSpeed (140), zigzagInterval (1.2), zigzagIntervalJitter (0.4),
 *               zigzagAngleDeg (35), dashSpeedP2 (210), zigzagAngleDegP2 (20),
 *               orbitDist (90), approachFalloff (150)
 */
public class ZigzagDashMoveStrategy implements BossMoveStrategy {

    private final float dashSpeed;
    private final float dashSpeedP2;
    private final float zigzagInterval;
    private final float zigzagIntervalJitter;
    private final float zigzagAngleDeg;
    private final float zigzagAngleDegP2;
    private final float orbitDist;
    private final float approachFalloff;

    private float zigzagTimer  = 0f;
    private float nextFlipTime;
    private int   zigzagSide   = 1;   // +1 or -1

    public ZigzagDashMoveStrategy(BossConfig cfg) {
        this.dashSpeed            = cfg.getFloat("dashSpeed",            140f);
        this.dashSpeedP2          = cfg.getFloat("dashSpeedP2",          210f);
        this.zigzagInterval       = cfg.getFloat("zigzagInterval",       1.2f);
        this.zigzagIntervalJitter = cfg.getFloat("zigzagIntervalJitter", 0.4f);
        this.zigzagAngleDeg       = cfg.getFloat("zigzagAngleDeg",       35f);
        this.zigzagAngleDegP2     = cfg.getFloat("zigzagAngleDegP2",     20f);
        this.orbitDist            = cfg.getFloat("orbitDist",            90f);
        this.approachFalloff      = cfg.getFloat("approachFalloff",      150f);
        this.nextFlipTime         = rollNextFlipTime();
    }

    private float rollNextFlipTime() {
        return zigzagInterval + MathUtils.random(-zigzagIntervalJitter, zigzagIntervalJitter);
    }

    @Override
    public void update(Tank tank, float delta, Vector2 playerPos, BossController context) {
        int phaseIndex = context.getPhaseIndex();
        float speed    = (phaseIndex >= 1) ? dashSpeedP2 : dashSpeed;
        float angle    = (phaseIndex >= 1) ? zigzagAngleDegP2 : zigzagAngleDeg;

        // Flip zigzag side at a randomised interval — keeps the S-curve unpredictable
        zigzagTimer += delta;
        if (zigzagTimer >= nextFlipTime) {
            zigzagTimer   = 0f;
            zigzagSide    = -zigzagSide;
            nextFlipTime  = rollNextFlipTime();
        }

        Vector2 pos = tank.getMovement().getPosition();
        Vector2 toPlayer = new Vector2(playerPos).sub(pos).nor();
        Vector2 perp = new Vector2(-toPlayer.y, toPlayer.x).scl(zigzagSide);

        // How much forward "close the distance" component to keep — 1 = full dash,
        // 0 = fully curled into an orbit. Fades smoothly, no hard stop.
        float distToPlayer = pos.dst(playerPos);
        float approachFactor = MathUtils.clamp(
            (distToPlayer - orbitDist) / approachFalloff, 0f, 1f);

        float rad = MathUtils.degreesToRadians * angle;
        Vector2 zigzagDir = toPlayer.cpy()
            .scl(MathUtils.cos(rad))
            .add(perp.cpy().scl(MathUtils.sin(rad)))
            .nor();

        // Tangential direction around the player, used to blend in as we close in
        Vector2 orbitDir = new Vector2(-toPlayer.y, toPlayer.x).scl(zigzagSide);

        Vector2 moveDir = zigzagDir.scl(approachFactor)
            .add(orbitDir.scl(1f - approachFactor))
            .nor();

        tank.getMovement().setPosition(
            pos.x + moveDir.x * speed * delta,
            pos.y + moveDir.y * speed * delta
        );
        tank.getMovement().faceTarget(playerPos);
        tank.getTurret().aimAt(tank.getMovement().getPosition(), playerPos);
    }
}
