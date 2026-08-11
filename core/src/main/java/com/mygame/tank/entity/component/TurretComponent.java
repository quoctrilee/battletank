package com.mygame.tank.entity.component;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.mygame.tank.config.GameConfig;
import com.mygame.tank.entity.Projectile;
import com.mygame.tank.weapon.WeaponSystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Owns turret orientation and all firing capability.
 *
 * <p>Player turrets delegate to {@link WeaponSystem} for multi-weapon support.
 * The fire entry-point returns a {@link WeaponSystem.FireResult} which may contain
 * projectiles, laser beams, and world effects.
 *
 * <p>Enemy/boss turrets fire directly with fixed stats via {@link #tryFireAt}
 * and {@link #trySpreadFire} — these return plain {@code List<Projectile>}.
 */
public final class TurretComponent {

    private float turretAngle;    // degrees: 0 = right
    private float fireTimer;

    // ── Per-turret stats (enemy / boss only) ─────────────────────────────────
    private final float fireRate;
    private final float bulletSpeed;
    private final float damage;
    private final float muzzleOffsetFactor;

    // ── Owner (enemy / boss only) ────────────────────────────────────────────
    private final Projectile.Owner owner;

    // ── Player weapon system (null for enemies) ───────────────────────────────
    private final WeaponSystem weaponSystem;

    // ─── Constructors ─────────────────────────────────────────────────────────

    /** Player turret — delegates firing to WeaponSystem. */
    public TurretComponent(float muzzleOffsetFactor, WeaponSystem weaponSystem) {
        this.turretAngle        = 90f;
        this.fireRate           = 0f;
        this.bulletSpeed        = 0f;
        this.damage             = 0f;
        this.muzzleOffsetFactor = muzzleOffsetFactor;
        this.owner              = Projectile.Owner.PLAYER;
        this.weaponSystem       = weaponSystem;
        this.fireTimer          = 0f;
    }

    /** Enemy / boss turret — fixed stats, no WeaponSystem. */
    public TurretComponent(float fireRate, float bulletSpeed, float damage,
                           float muzzleOffsetFactor, Projectile.Owner owner) {
        this.turretAngle        = 0f;
        this.fireRate           = fireRate;
        this.bulletSpeed        = bulletSpeed;
        this.damage             = damage;
        this.muzzleOffsetFactor = muzzleOffsetFactor;
        this.owner              = owner;
        this.weaponSystem       = null;
        this.fireTimer          = 0f;
    }

    // ─── Update ───────────────────────────────────────────────────────────────

    /** Must be called every frame to tick cooldowns. */
    public void update(float delta) {
        if (fireTimer > 0f) fireTimer -= delta;
        if (weaponSystem != null) weaponSystem.update(delta);
    }

    // ─── Aim ─────────────────────────────────────────────────────────────────

    public void aimAt(Vector2 origin, Vector2 target) {
        turretAngle = MathUtils.atan2(target.y - origin.y,
                                      target.x - origin.x) * MathUtils.radiansToDegrees;
    }

    public void aimAtAngle(float angleDeg) { this.turretAngle = angleDeg; }

    // ─── Fire (player) ────────────────────────────────────────────────────────

    /**
     * Player fire — delegates to WeaponSystem and returns a {@link WeaponSystem.FireResult}.
     *
     * @param origin        tank center position
     * @param tankSize      used with muzzleOffsetFactor to place the muzzle
     * @param fireRequested true when the fire button is held
     * @param ownerTank     reference to the player tank (for equipment)
     * @return FireResult (never null)
     */
    public WeaponSystem.FireResult tryFirePlayer(Vector2 origin, float tankSize,
                                                  boolean fireRequested,
                                                  com.mygame.tank.entity.Tank ownerTank) {
        if (weaponSystem == null || !fireRequested) return WeaponSystem.FireResult.EMPTY;

        float rad    = MathUtils.degreesToRadians * turretAngle;
        float offset = tankSize * muzzleOffsetFactor;
        float ox     = origin.x + MathUtils.cos(rad) * offset;
        float oy     = origin.y + MathUtils.sin(rad) * offset;

        return weaponSystem.tryFire(ox, oy,
                MathUtils.cos(rad), MathUtils.sin(rad),
                ownerTank, fireRequested);
    }

    /**
     * Activate tactical equipment for the player.
     *
     * @param slot 0/1/2
     * @param ownerTank the player tank
     */
    public WeaponSystem.FireResult useEquipment(int slot,
                                                 com.mygame.tank.entity.Tank ownerTank) {
        if (weaponSystem == null) return WeaponSystem.FireResult.EMPTY;
        return weaponSystem.useEquipment(slot, ownerTank);
    }

    // ─── Fire (enemy single shot) ─────────────────────────────────────────────

    /** Enemy fire — single bullet toward a target. Returns empty if on cooldown. */
    public List<Projectile> tryFireAt(Vector2 origin, Vector2 target, float tankSize) {
        if (fireTimer > 0f) return Collections.emptyList();
        fireTimer = fireRate;

        float dx  = target.x - origin.x;
        float dy  = target.y - origin.y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.01f) return Collections.emptyList();

        float rad    = MathUtils.degreesToRadians * turretAngle;
        float offset = tankSize * muzzleOffsetFactor;
        float ox     = origin.x + MathUtils.cos(rad) * offset;
        float oy     = origin.y + MathUtils.sin(rad) * offset;

        List<Projectile> shots = new ArrayList<>(1);
        shots.add(Projectile.normal(ox, oy, dx / len, dy / len,
                bulletSpeed, damage, this.owner, GameConfig.BULLET_LIFETIME));
        return shots;
    }

    // ─── Fire (boss spread) ──────────────────────────────────────────────────

    /** Boss spread fire — one bullet per angle. Returns empty if on cooldown. */
    public List<Projectile> trySpreadFire(Vector2 origin, float tankSize,
                                           float[] spreadAngles) {
        if (fireTimer > 0f) return Collections.emptyList();
        fireTimer = fireRate;

        List<Projectile> shots = new ArrayList<>(spreadAngles.length);
        for (float angle : spreadAngles) {
            float r      = MathUtils.degreesToRadians * angle;
            float dx     = MathUtils.cos(r);
            float dy     = MathUtils.sin(r);
            float offset = tankSize * muzzleOffsetFactor;
            shots.add(Projectile.normal(
                    origin.x + dx * offset,
                    origin.y + dy * offset,
                    dx, dy,
                    bulletSpeed, damage, this.owner, GameConfig.BULLET_LIFETIME));
        }
        return shots;
    }

    // ─── Weapon-system passthrough (player only) ─────────────────────────────

    /** Toggle the weapon hub. No-op for non-player turrets. */
    public void toggleHub() {
        if (weaponSystem != null) weaponSystem.toggleHub();
    }

    /** True while the weapon hub is open. */
    public boolean isHubOpen() {
        return weaponSystem != null && weaponSystem.isHubOpen();
    }

    /** True while the player's weapon is EMP-suppressed (hub compat check). */
    public boolean isVulnerable() { return false; }

    // ─── Getters ─────────────────────────────────────────────────────────────
    public float        getTurretAngle()  { return turretAngle; }
    public WeaponSystem getWeaponSystem() { return weaponSystem; }
}
