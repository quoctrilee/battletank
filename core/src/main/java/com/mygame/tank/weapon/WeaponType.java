package com.mygame.tank.weapon;

import com.mygame.tank.config.GameConfig;

/**
 * All playable weapon types, split into three groups:
 *
 * <ul>
 *   <li>Group A — Direct DPS (unlimited ammo, fire-rate controlled)</li>
 *   <li>Group B — AoE (limited ammo + cooldown)</li>
 *   <li>Group C — Special / Control (limited ammo + cooldown)</li>
 * </ul>
 */
public enum WeaponType {

    // ── Group A: Direct DPS ────────────────────────────────────────────────
    DEFAULT_BULLET("Default", "Default Shell", GameConfig.DEFAULT_MAX_AMMO, GameConfig.DEFAULT_FIRE_RATE, 0f),
    SUBMACHINE_GUN("SMG", "Submachine Gun", GameConfig.SMG_MAX_AMMO, GameConfig.SMG_FIRE_RATE, 0f),
    ARMOR_PIERCE("AP", "Armor Piercing", GameConfig.AP_MAX_AMMO, GameConfig.AP_FIRE_RATE, 0f),

    // ── Group B: AoE ───────────────────────────────────────────────────────
    CANNON("Cannon", "Heavy Cannon", GameConfig.CANNON_MAX_AMMO, 0f, GameConfig.CANNON_COOLDOWN),
    STUN_SHELL("Stun", "Stun Shell", GameConfig.STUN_MAX_AMMO, 0f, GameConfig.STUN_COOLDOWN),

    // ── Group C: Special ───────────────────────────────────────────────────
    LASER("Laser", "Laser Beam", GameConfig.LASER_MAX_AMMO, 0f, GameConfig.LASER_COOLDOWN),
    HOMING_MISSILE("Homing", "Homing Missile", GameConfig.HOMING_MAX_AMMO, 0f, GameConfig.HOMING_COOLDOWN);

    // ─── Fields ──────────────────────────────────────────────────────────────

    /**
     * Short code shown in weapon slot (4 chars max).
     */
    public final String shortName;

    /**
     * Full display name shown in the weapon hub.
     */
    public final String displayName;

    /**
     * Maximum ammo count. {@code -1} means unlimited.
     * Group A weapons are unlimited; Group B/C are limited.
     */
    public final int maxAmmo;

    /**
     * Seconds between consecutive shots (fire rate).
     * Used for Group A. Group B/C weapons use {@link #cooldown} instead.
     */
    public final float fireRate;

    /**
     * Post-use cooldown for limited-ammo weapons (Group B/C).
     * {@code 0} means no cooldown (weapon fires at {@link #fireRate} pace).
     */
    public final float cooldown;

    /**
     * True for weapons that have a cooldown + ammo economy.
     */
    public boolean isSpecial() {
        return maxAmmo > 0;
    }

    WeaponType(String shortName, String displayName, int maxAmmo, float fireRate, float cooldown) {
        this.shortName = shortName;
        this.displayName = displayName;
        this.maxAmmo = maxAmmo;
        this.fireRate = fireRate;
        this.cooldown = cooldown;
    }
}
