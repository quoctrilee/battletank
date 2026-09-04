package com.mygame.tank.controller.ai.strategy;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.mygame.tank.config.BossConfig;
import com.mygame.tank.controller.ai.BossController;
import com.mygame.tank.entity.Projectile;
import com.mygame.tank.entity.Tank;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Boss 2 — Viper: fires homing missiles that track the player.
 * <p>
 * Phase 0: fires 1 homing missile per interval.
 * Phase 1+: fires a quick burst of {@code burstCount} missiles slightly fanned out,
 *           each with full homing capability — very hard to dodge.
 *
 * Reads params: fireInterval (2.5), homingTurnRate (110), burstCount (3),
 *               burstAngleDeg (15), bulletSpeed (220), damage (22),
 *               fireIntervalP2 (1.8)
 */
public class HomingFireStrategy implements BossFireStrategy {

    private final float fireInterval;
    private final float fireIntervalP2;
    private final float homingTurnRate;
    private final int   burstCount;
    private final float burstAngleDeg;
    private final float bulletSpeed;
    private final float damage;

    private float fireTimer = 0f;

    public HomingFireStrategy(BossConfig cfg) {
        this.fireInterval   = cfg.getFloat("fireInterval",   2.5f);
        this.fireIntervalP2 = cfg.getFloat("fireIntervalP2", 1.8f);
        this.homingTurnRate = cfg.getFloat("homingTurnRate", 110f);
        this.burstCount     = cfg.getInt("burstCount",       3);
        this.burstAngleDeg  = cfg.getFloat("burstAngleDeg",  15f);
        this.bulletSpeed    = cfg.getFloat("bulletSpeed",    220f);
        this.damage         = cfg.getFloat("damage",         22f);
    }

    @Override
    public List<Projectile> fire(Tank tank, float delta, Vector2 playerPos, BossController context) {
        fireTimer -= delta;
        if (fireTimer > 0f) return Collections.emptyList();

        int phaseIndex = context.getPhaseIndex();
        fireTimer = (phaseIndex >= 1) ? fireIntervalP2 : fireInterval;

        Vector2 origin = tank.getMovement().getPosition();
        Vector2 toPlayer = new Vector2(playerPos).sub(origin).nor();
        float baseAngle = MathUtils.atan2(toPlayer.y, toPlayer.x) * MathUtils.radiansToDegrees;

        List<Projectile> shots = new ArrayList<>();

        if (phaseIndex >= 1) {
            // Burst: fan missiles evenly around aim angle
            float totalSpread = burstAngleDeg * (burstCount - 1);
            float startAngle = baseAngle - totalSpread / 2f;
            for (int i = 0; i < burstCount; i++) {
                float a = MathUtils.degreesToRadians * (startAngle + i * burstAngleDeg);
                shots.add(Projectile.homing(
                    origin.x, origin.y,
                    MathUtils.cos(a), MathUtils.sin(a),
                    bulletSpeed, damage,
                    Projectile.Owner.BOSS,
                    3.0f, homingTurnRate
                ));
            }
        } else {
            // Single missile
            shots.add(Projectile.homing(
                origin.x, origin.y,
                toPlayer.x, toPlayer.y,
                bulletSpeed, damage,
                Projectile.Owner.BOSS,
                3.0f, homingTurnRate
            ));
        }
        return shots;
    }
}
