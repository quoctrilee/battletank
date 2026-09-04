package com.mygame.tank.controller.ai.strategy;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.mygame.tank.config.BossConfig;
import com.mygame.tank.controller.ai.BossController;
import com.mygame.tank.entity.Tank;

/**
 * Boss 1 — Iron Guard: circular orbit around spawn center.
 * Reads params: orbitRadius (default 110), orbitSpeedDeg (default 60).
 */
public class OrbitMoveStrategy implements BossMoveStrategy {

    private final float orbitRadius;
    private final float orbitSpeedDeg;

    public OrbitMoveStrategy(BossConfig cfg) {
        this.orbitRadius   = cfg.getFloat("orbitRadius",   110f);
        this.orbitSpeedDeg = cfg.getFloat("orbitSpeedDeg", 60f);
    }

    @Override
    public void update(Tank tank, float delta, Vector2 playerPos, BossController context) {
        float angle = context.getCircleAngle();
        angle += orbitSpeedDeg * delta * context.getDirection();
        context.setCircleAngle(angle);

        float rad = MathUtils.degreesToRadians * angle;
        Vector2 center = context.getCircleCenter();
        tank.getMovement().setPosition(
            center.x + MathUtils.cos(rad) * orbitRadius,
            center.y + MathUtils.sin(rad) * orbitRadius
        );
        tank.getMovement().faceTarget(playerPos);
        tank.getTurret().aimAt(tank.getMovement().getPosition(), playerPos);
    }
}
