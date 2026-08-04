package com.mygame.tank.weapon;

import com.mygame.tank.config.GameConfig;

/**
 * Tactical equipment carried in the 3 equipment slots (keys 1 / 2 / 3).
 *
 * Equipment is instant-use (no projectile flight), produces a {@link com.mygame.tank.entity.WorldEffect},
 * and recharges on a per-type cooldown.
 */
public enum EquipmentType {

    SMOKE_BOMB   ("Smoke",  "Smoke Bomb",       GameConfig.SMOKE_COOLDOWN),
    ENERGY_SHIELD("Shield", "Shield",    GameConfig.SHIELD_COOLDOWN),
    ANTI_TANK_MINE("Mine",   "Anti-Tank Mine",   GameConfig.MINE_COOLDOWN),
    EMP_FIELD    ("EMP",    "EMP Field",        GameConfig.EMP_COOLDOWN);

    /** Short label for equipment slot icon (4 chars max). */
    public final String shortName;

    /** Full display name shown in HUD tooltip. */
    public final String displayName;

    /** Seconds before this equipment can be used again. */
    public final float cooldown;

    EquipmentType(String shortName, String displayName, float cooldown) {
        this.shortName   = shortName;
        this.displayName = displayName;
        this.cooldown    = cooldown;
    }
}
