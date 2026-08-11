package com.mygame.tank.entity.component.turret;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.mygame.tank.entity.Tank;
import com.mygame.tank.weapon.WeaponSystem;

/**
 * Player turret — owns orientation only; all firing/ammo/cooldown logic lives in
 * {@link WeaponSystem}. Kept separate from {@link EnemyTurretComponent} so this class
 * never needs to change when enemy weapon behavior changes, and vice versa.
 */
public final class PlayerTurretComponent implements Turret {

    private float turretAngle;
    private final float muzzleOffsetFactor;
    private final WeaponSystem weaponSystem;

    public PlayerTurretComponent(float muzzleOffsetFactor, WeaponSystem weaponSystem) {
        this.turretAngle = 90f;
        this.muzzleOffsetFactor = muzzleOffsetFactor;
        this.weaponSystem = weaponSystem;
    }

    @Override
    public void update(float delta) {
        weaponSystem.update(delta);
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

    // ─── Fire ────────────────────────────────────────────────────────────────

    /**
     * @param origin        tank center position
     * @param tankSize      used with muzzleOffsetFactor to place the muzzle
     * @param fireRequested true when the fire button is held
     * @param ownerTank     reference to the player tank (for equipment)
     * @return FireResult (never null)
     */
    public WeaponSystem.FireResult tryFire(Vector2 origin, float tankSize,
                                           boolean fireRequested, Tank ownerTank) {
        if (!fireRequested) return WeaponSystem.FireResult.EMPTY;

        float rad = MathUtils.degreesToRadians * turretAngle;
        float offset = tankSize * muzzleOffsetFactor;
        float ox = origin.x + MathUtils.cos(rad) * offset;
        float oy = origin.y + MathUtils.sin(rad) * offset;

        return weaponSystem.tryFire(ox, oy,
            MathUtils.cos(rad), MathUtils.sin(rad),
            ownerTank, fireRequested);
    }

    /**
     * @param slot      0/1/2/3
     * @param ownerTank the player tank
     */
    public WeaponSystem.FireResult useEquipment(int slot, Tank ownerTank) {
        return weaponSystem.useEquipment(slot, ownerTank);
    }

    public void toggleHub() {
        weaponSystem.toggleHub();
    }

    public boolean isHubOpen() {
        return weaponSystem.isHubOpen();
    }

    @Override
    public float getTurretAngle() {
        return turretAngle;
    }

    public WeaponSystem getWeaponSystem() {
        return weaponSystem;
    }
}
