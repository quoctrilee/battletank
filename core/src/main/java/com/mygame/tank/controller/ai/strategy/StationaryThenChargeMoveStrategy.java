package com.mygame.tank.controller.ai.strategy;

import com.badlogic.gdx.math.Vector2;
import com.mygame.tank.config.BossConfig;
import com.mygame.tank.controller.ai.BossController;
import com.mygame.tank.entity.Tank;

/**
 * Boss 3 — Siege: stationary in phase 0, then charge-dash in phase 1+.
 * <p>
 * Phase 0: Boss stands at its spawn center, only rotates turret to track player.
 *           This forces the player to approach and deal with the heavy firepower.
 * Phase 1 (HP ≤ chargeThreshold): Boss charges directly at the player with high speed,
 *           then waits at a random offset before charging again.
 *
 * Reads params: chargeThreshold (0.5), chargeSpeed (280), chargeDuration (0.7),
 *               chargeCooldown (2.0)
 */
public class StationaryThenChargeMoveStrategy implements BossMoveStrategy {

    private final float chargeThreshold;
    private final float chargeSpeed;
    private final float chargeDuration;
    private final float chargeCooldown;

    private boolean charging        = false;
    private float   chargeTimer     = 0f;
    private float   cooldownTimer   = 0f;
    private Vector2 chargeDirection = new Vector2();

    public StationaryThenChargeMoveStrategy(BossConfig cfg) {
        this.chargeThreshold = cfg.getFloat("chargeThreshold", 0.5f);
        this.chargeSpeed     = cfg.getFloat("chargeSpeed",     280f);
        this.chargeDuration  = cfg.getFloat("chargeDuration",  0.7f);
        this.chargeCooldown  = cfg.getFloat("chargeCooldown",  2.0f);
    }

    @Override
    public void update(Tank tank, float delta, Vector2 playerPos, BossController context) {
        int phaseIndex = context.getPhaseIndex();

        if (phaseIndex == 0) {
            // Phase 0: stand still at spawn center, just aim turret
            Vector2 center = context.getCircleCenter();
            tank.getMovement().setPosition(center.x, center.y);
            tank.getMovement().faceTarget(playerPos);
            tank.getTurret().aimAt(tank.getMovement().getPosition(), playerPos);
            return;
        }

        // Phase 1+: charge pattern
        tank.getTurret().aimAt(tank.getMovement().getPosition(), playerPos);
        tank.getMovement().faceTarget(playerPos);

        if (charging) {
            chargeTimer -= delta;
            Vector2 pos = tank.getMovement().getPosition();
            tank.getMovement().setPosition(
                pos.x + chargeDirection.x * chargeSpeed * delta,
                pos.y + chargeDirection.y * chargeSpeed * delta
            );
            if (chargeTimer <= 0f) {
                charging = false;
                cooldownTimer = chargeCooldown;
            }
        } else {
            cooldownTimer -= delta;
            if (cooldownTimer <= 0f) {
                // Start a new charge toward current player pos
                Vector2 pos = tank.getMovement().getPosition();
                chargeDirection.set(playerPos).sub(pos).nor();
                charging = true;
                chargeTimer = chargeDuration;
            }
        }
    }
}
