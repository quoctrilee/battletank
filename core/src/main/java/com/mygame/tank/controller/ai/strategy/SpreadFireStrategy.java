package com.mygame.tank.controller.ai.strategy;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.mygame.tank.config.BossConfig;
import com.mygame.tank.controller.ai.BossController;
import com.mygame.tank.entity.Projectile;
import com.mygame.tank.entity.Tank;
import com.mygame.tank.entity.component.turret.EnemyTurretComponent;

import java.util.Collections;
import java.util.List;

/**
 * Boss 1 — Iron Guard: 3-way spread shot toward player.
 * Reads params: spreadAngleDeg (default 30), spreadCount (default 3).
 */
public class SpreadFireStrategy implements BossFireStrategy {

    private final float spreadAngleDeg;
    private final int   spreadCount;

    public SpreadFireStrategy(BossConfig cfg) {
        this.spreadAngleDeg = cfg.getFloat("spreadAngleDeg", 30f);
        this.spreadCount    = cfg.getInt("spreadCount", 3);
    }

    @Override
    public List<Projectile> fire(Tank tank, float delta, Vector2 playerPos, BossController context) {
        EnemyTurretComponent turret = (EnemyTurretComponent) tank.getTurret();
        float base = turret.getTurretAngle();

        float[] angles = new float[spreadCount];
        int half = spreadCount / 2;
        for (int i = 0; i < spreadCount; i++) {
            angles[i] = base + (i - half) * spreadAngleDeg;
        }

        return turret.trySpreadFire(
            tank.getMovement().getPosition(),
            tank.getStats().height,
            angles
        );
    }
}
