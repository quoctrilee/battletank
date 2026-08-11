package com.mygame.tank.entity.component.turret;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.mygame.tank.config.GameConfig;
import com.mygame.tank.entity.Projectile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Enemy / boss turret — fixed fire-rate/damage/speed stats, fires straight at a
 * target or in a fixed spread. Spawns {@link Projectile} directly (no WeaponSystem,
 * no ammo economy).
 */
public final class EnemyTurretComponent implements Turret {

    private float turretAngle;
    private float fireTimer;

    private final float fireRate;
    private final float bulletSpeed;
    private final float damage;
    private final float muzzleOffsetFactor;
    private final Projectile.Owner owner;

    public EnemyTurretComponent(float fireRate, float bulletSpeed, float damage,
                                float muzzleOffsetFactor, Projectile.Owner owner) {
        this.turretAngle = 0f;
        this.fireRate = fireRate;
        this.bulletSpeed = bulletSpeed;
        this.damage = damage;
        this.muzzleOffsetFactor = muzzleOffsetFactor;
        this.owner = owner;
        this.fireTimer = 0f;
    }

    @Override
    public void update(float delta) {
        if (fireTimer > 0f) fireTimer -= delta;
    }

    @Override
    public void aimAt(Vector2 origin, Vector2 target) {
        turretAngle = MathUtils.atan2(target.y - origin.y,
            target.x - origin.x) * MathUtils.radiansToDegrees;
    }

    @Override
    public void aimAtAngle(float angleDeg) {
        this.turretAngle = angleDeg;
    }

    // ─── Fire (single shot at target) ────────────────────────────────────────

    /**
     * Fires a single bullet toward {@code target}. Always faces the turret at the
     * target first, so the muzzle position and bullet direction can never disagree
     * — no dependency on the caller having called {@link #aimAt} beforehand.
     * Returns empty if on cooldown.
     */
    public List<Projectile> tryFireAt(Vector2 origin, Vector2 target, float tankSize) {
        if (fireTimer > 0f) return Collections.emptyList();

        Vector2 dir = new Vector2(target).sub(origin);
        float len = dir.len();
        if (len < 0.01f) return Collections.emptyList();
        dir.scl(1f / len);

        fireTimer = fireRate;
        aimAt(origin, target);

        float offset = tankSize * muzzleOffsetFactor;
        float ox = origin.x + dir.x * offset;
        float oy = origin.y + dir.y * offset;

        List<Projectile> shots = new ArrayList<>(1);
        shots.add(Projectile.normal(ox, oy, dir.x, dir.y,
            bulletSpeed, damage, this.owner, GameConfig.BULLET_LIFETIME));
        return shots;
    }

    // ─── Fire (boss spread) ──────────────────────────────────────────────────

    /**
     * Boss spread fire — one bullet per angle in {@code spreadAngles}. Returns empty
     * if on cooldown.
     */
    public List<Projectile> trySpreadFire(Vector2 origin, float tankSize,
                                          float[] spreadAngles) {
        if (fireTimer > 0f) return Collections.emptyList();
        fireTimer = fireRate;

        List<Projectile> shots = new ArrayList<>(spreadAngles.length);
        for (float angle : spreadAngles) {
            float r = MathUtils.degreesToRadians * angle;
            float dx = MathUtils.cos(r);
            float dy = MathUtils.sin(r);
            float offset = tankSize * muzzleOffsetFactor;
            shots.add(Projectile.normal(
                origin.x + dx * offset,
                origin.y + dy * offset,
                dx, dy,
                bulletSpeed, damage, this.owner, GameConfig.BULLET_LIFETIME));
        }
        return shots;
    }

    @Override
    public float getTurretAngle() {
        return turretAngle;
    }
}
