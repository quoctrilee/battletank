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
 * Boss 3 — Siege: alternates between AoE cannon shots and stun shells.
 * <p>
 * Fires in a rotating pattern:
 * <ul>
 *   <li>Phase 0: 1 AoE cannon shot every {@code cannonInterval} s,
 *       aimed directly at player.</li>
 *   <li>Phase 1+: alternates cannon + stun burst (3 stun shells fanned out)
 *       at a faster rate — creates a minefield feel with overlapping AoE + stun zones.</li>
 * </ul>
 *
 * Reads params: cannonInterval (3.0), stunInterval (2.0), cannonSpeed (280),
 *               cannonDamage (40), cannonAoeRadius (90), stunDamage (10),
 *               stunAoeRadius (80), stunDuration (2.0), stunBurstCount (3),
 *               stunBurstAngleDeg (25), bulletSpeed (240),
 *               cannonIntervalP2 (2.0), stunIntervalP2 (1.4)
 */
public class SiegeCannonFireStrategy implements BossFireStrategy {

    private final float cannonInterval;
    private final float cannonIntervalP2;
    private final float stunInterval;
    private final float stunIntervalP2;
    private final float cannonSpeed;
    private final float cannonDamage;
    private final float cannonAoeRadius;
    private final float stunDamage;
    private final float stunAoeRadius;
    private final float stunDuration;
    private final int   stunBurstCount;
    private final float stunBurstAngleDeg;
    private final float bulletSpeed;

    private float cannonTimer;
    private float stunTimer;
    // Alternation flag — ensures cannon and stun shots interleave visually
    private boolean nextIsCannon = true;

    public SiegeCannonFireStrategy(BossConfig cfg) {
        this.cannonInterval    = cfg.getFloat("cannonInterval",    3.0f);
        this.cannonIntervalP2  = cfg.getFloat("cannonIntervalP2",  2.0f);
        this.stunInterval      = cfg.getFloat("stunInterval",      3f);
        this.stunIntervalP2    = cfg.getFloat("stunIntervalP2",    3f);
        this.cannonSpeed       = cfg.getFloat("cannonSpeed",       280f);
        this.cannonDamage      = cfg.getFloat("cannonDamage",      40f);
        this.cannonAoeRadius   = cfg.getFloat("cannonAoeRadius",   90f);
        this.stunDamage        = cfg.getFloat("stunDamage",        10f);
        this.stunAoeRadius     = cfg.getFloat("stunAoeRadius",     60f);
        this.stunDuration      = cfg.getFloat("stunDuration",      1.0f);
        this.stunBurstCount    = cfg.getInt("stunBurstCount",      3);
        this.stunBurstAngleDeg = cfg.getFloat("stunBurstAngleDeg", 25f);
        this.bulletSpeed       = cfg.getFloat("bulletSpeed",       240f);
        this.cannonTimer = cannonInterval;
        this.stunTimer   = stunInterval * 0.5f; // stagger first stun slightly
    }

    @Override
    public List<Projectile> fire(Tank tank, float delta, Vector2 playerPos, BossController context) {
        int phaseIndex = context.getPhaseIndex();

        float ci = (phaseIndex >= 1) ? cannonIntervalP2 : cannonInterval;
        float si = (phaseIndex >= 1) ? stunIntervalP2   : stunInterval;

        cannonTimer -= delta;
        stunTimer   -= delta;

        List<Projectile> shots = new ArrayList<>();
        Vector2 origin = tank.getMovement().getPosition();

        // ── Cannon shot ─────────────────────────────────────────────────────
        if (cannonTimer <= 0f) {
            cannonTimer = ci;
            Vector2 dir = new Vector2(playerPos).sub(origin).nor();
            shots.add(Projectile.aoe(
                origin.x, origin.y, dir.x, dir.y,
                cannonSpeed, cannonDamage,
                Projectile.Owner.BOSS,
                2.0f, cannonAoeRadius
            ));
        }

        // ── Stun burst (phase 1+ only) ───────────────────────────────────────
        if (phaseIndex >= 1 && stunTimer <= 0f) {
            stunTimer = si;
            float baseAngle = MathUtils.atan2(
                playerPos.y - origin.y,
                playerPos.x - origin.x) * MathUtils.radiansToDegrees;

            float totalSpread = stunBurstAngleDeg * (stunBurstCount - 1);
            float startAngle  = baseAngle - totalSpread / 2f;
            for (int i = 0; i < stunBurstCount; i++) {
                float rad = MathUtils.degreesToRadians * (startAngle + i * stunBurstAngleDeg);
                shots.add(Projectile.stunAoe(
                    origin.x, origin.y,
                    MathUtils.cos(rad), MathUtils.sin(rad),
                    bulletSpeed, stunDamage,
                    Projectile.Owner.BOSS,
                    1.8f, stunAoeRadius, stunDuration
                ));
            }
        }

        return shots.isEmpty() ? Collections.emptyList() : shots;
    }
}
