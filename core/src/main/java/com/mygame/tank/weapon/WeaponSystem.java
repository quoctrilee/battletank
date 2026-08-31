package com.mygame.tank.weapon;

import com.badlogic.gdx.math.MathUtils;
import com.mygame.tank.audio.SfxManager;
import com.mygame.tank.config.GameConfig;
import com.mygame.tank.entity.LaserBeam;
import com.mygame.tank.entity.Projectile;
import com.mygame.tank.entity.Tank;
import com.mygame.tank.entity.WorldEffect;
import com.mygame.tank.entity.VisualEffect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages the player's weapon loadout and tactical equipment.
 *
 * <h3>Weapon Loadout (Damage Weapons)</h3>
 * <ul>
 *   <li>7 slots — chosen from all 7 {@link WeaponType} values</li>
 *   <li>Active slot fires on left-click</li>
 *   <li>Group A weapons: unlimited ammo, fire-rate controlled</li>
 *   <li>Group B/C weapons: limited ammo + per-use cooldown</li>
 *   <li>Slot selection via Weapon Hub (Tab key) — game does NOT pause</li>
 * </ul>
 *
 * <h3>Equipment Loadout (Tactical Equipment)</h3>
 * <ul>
 *   <li>4 slots — chosen from {@link EquipmentType} values</li>
 *   <li>Activated directly via number keys (1 / 2 / 3 / 4); no hub required</li>
 *   <li>Each slot operates on an independent cooldown timer</li>
 * </ul>
 *
 * <h3>Action Output</h3>
 * {@link FireResult} serves as a unified container for all entities (projectiles, laser beams,
 * world effects, and visual effects) produced during a single action (firing a weapon or using equipment)
 * so {@code GameWorld} can process them cleanly.
 */
public class WeaponSystem {

    // ─── Action Result Bundle ─────────────────────────────────────────────────

    /**
     * Holds all entities produced by a single weapon shot or equipment activation.
     */
    public static final class FireResult {
        public final List<Projectile> projectiles;
        public final List<LaserBeam> laserBeams;
        public final List<WorldEffect> worldEffects;
        public final List<VisualEffect> vfx;

        public static final FireResult EMPTY = new FireResult(
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        public FireResult(List<Projectile> projectiles,
                          List<LaserBeam> laserBeams,
                          List<WorldEffect> worldEffects,
                          List<VisualEffect> vfx) {
            this.projectiles = projectiles;
            this.laserBeams = laserBeams;
            this.worldEffects = worldEffects;
            this.vfx = vfx;
        }
    }

    // ─── Constants ────────────────────────────────────────────────────────────

    private static final int WEAPON_SLOTS = GameConfig.DAMAGE_WEAPON_SLOTS;
    private static final int EQUIPMENT_SLOTS = GameConfig.EQUIPMENT_SLOTS;

    // ─── Weapon Loadout ───────────────────────────────────────────────────────

    private final WeaponType[] weaponSlots;
    private final int[] weaponAmmo;        // Current ammo per slot (-1 = unlimited)
    private final float[] weaponCooldown; // Remaining cooldown per slot (in seconds)
    private int activeWeaponSlot;

    /**
     * True while the weapon hub UI is open — weapon firing is blocked.
     */
    private boolean hubOpen;

    // ─── SMG Heat Tracking ────────────────────────────────────────────────────

    /**
     * Current accuracy spread accumulation (in degrees) for the Submachine Gun.
     */
    private float smgSpread;

    // ─── Laser State ──────────────────────────────────────────────────────────

    /**
     * True while laser telegraph or beam is active (fired but cooldown not yet set).
     */
    private boolean laserFiring;

    // ─── SFX ─────────────────────────────────────────────────────────────────

    /**
     * Optional SFX manager — if non-null, plays a weapon-specific sound on each shot.
     * Injected after construction via {@link #setSfxManager(SfxManager)}.
     */
    private SfxManager sfxManager;

    // ─── Equipment Loadout ────────────────────────────────────────────────────

    private final EquipmentType[] equipSlots;
    private final float[] equipCooldown; // Remaining cooldown per slot (in seconds)

    // ─── Constructor ─────────────────────────────────────────────────────────

    /**
     * Initializes with a default loadout — all weapons and equipment unlocked for testing.
     * In production, invoke {@link #setWeaponSlot} / {@link #setEquipSlot} via the progression/unlock system.
     */
    public WeaponSystem() {
        weaponSlots = new WeaponType[WEAPON_SLOTS];
        weaponAmmo = new int[WEAPON_SLOTS];
        weaponCooldown = new float[WEAPON_SLOTS];

        // Default loadout: all 7 weapons
        setWeaponSlot(0, WeaponType.DEFAULT_BULLET);
        setWeaponSlot(1, WeaponType.SUBMACHINE_GUN);
        setWeaponSlot(2, WeaponType.ARMOR_PIERCE);
        setWeaponSlot(3, WeaponType.CANNON);
        setWeaponSlot(4, WeaponType.STUN_SHELL);
        setWeaponSlot(5, WeaponType.LASER);
        setWeaponSlot(6, WeaponType.HOMING_MISSILE);
        activeWeaponSlot = 0;

        equipSlots = new EquipmentType[EQUIPMENT_SLOTS];
        equipCooldown = new float[EQUIPMENT_SLOTS];

        // Default equipment: all 4 equipment types
        setEquipSlot(0, EquipmentType.SMOKE_BOMB);
        setEquipSlot(1, EquipmentType.ENERGY_SHIELD);
        setEquipSlot(2, EquipmentType.ANTI_TANK_MINE);
        setEquipSlot(3, EquipmentType.EMP_FIELD);

        smgSpread = 0f;
        laserFiring = false;
        hubOpen = false;
    }

    // ─── Slot Management ─────────────────────────────────────────────────────

    /**
     * Assigns a weapon to a slot and resets its ammo and cooldown.
     */
    public void setWeaponSlot(int slot, WeaponType type) {
        weaponSlots[slot] = type;
        weaponAmmo[slot] = type.maxAmmo;  // -1 = unlimited
        weaponCooldown[slot] = 0f;
    }

    /**
     * Assigns a tactical equipment to a slot and resets its cooldown.
     */
    public void setEquipSlot(int slot, EquipmentType type) {
        equipSlots[slot] = type;
        equipCooldown[slot] = 0f;
    }

    // ─── Update ───────────────────────────────────────────────────────────────

    public void update(float delta) {
        // Update weapon cooldown timers
        for (int i = 0; i < WEAPON_SLOTS; i++) {
            if (weaponCooldown[i] > 0f)
                weaponCooldown[i] = Math.max(0f, weaponCooldown[i] - delta);
        }
        // Update equipment cooldown timers
        for (int i = 0; i < EQUIPMENT_SLOTS; i++) {
            if (equipCooldown[i] > 0f)
                equipCooldown[i] = Math.max(0f, equipCooldown[i] - delta);
        }
        // Cool down SMG spread when not actively firing
        if (smgSpread > 0f) {
            smgSpread = Math.max(0f, smgSpread - GameConfig.SMG_SPREAD_DECAY_RATE * delta);
        }
    }

    // ─── Weapon Hub ──────────────────────────────────────────────────────────

    public void toggleHub() {
        hubOpen = !hubOpen;
    }

    public void closeHub() {
        hubOpen = false;
    }

    public boolean isHubOpen() {
        return hubOpen;
    }

    /**
     * Selects a weapon slot while the hub is open.
     * Ignored if the hub is closed or slot index is out of bounds.
     */
    public void selectWeaponInHub(int slot) {
        if (!hubOpen || slot < 0 || slot >= WEAPON_SLOTS) return;
        activeWeaponSlot = slot;
    }

    /**
     * Directly sets the active weapon slot regardless of hub state.
     * Used when the player clicks/taps a weapon slot in the HUD directly.
     *
     * @param slot 0-based slot index; silently ignored if out of bounds.
     */
    public void setActiveWeaponSlot(int slot) {
        if (slot < 0 || slot >= WEAPON_SLOTS) return;
        activeWeaponSlot = slot;
    }

    /**
     * Assigns a new weapon type to the currently selected hub slot.
     * Called from the hub UI when the player selects a different weapon card.
     */
    public void assignWeaponToActive(WeaponType type) {
        setWeaponSlot(activeWeaponSlot, type);
    }

    // ─── SFX ─────────────────────────────────────────────────────────────────

    /**
     * Injects the SFX manager used to play weapon sounds on fire.
     * Must be called before the first {@link #tryFire} invocation.
     *
     * @param sfxManager the SFX manager; {@code null} disables weapon SFX.
     */
    public void setSfxManager(SfxManager sfxManager) {
        this.sfxManager = sfxManager;
    }

    // ─── Fire (Damage Weapons) ────────────────────────────────────────────────

    /**
     * Attempts to fire the currently active weapon.
     *
     * @param ox        muzzle origin X (world space)
     * @param oy        muzzle origin Y (world space)
     * @param dirX      normalized aim direction X
     * @param dirY      normalized aim direction Y
     * @param ownerTank reference to the firing tank
     * @param fireHeld  true if the fire button is being held down
     * @return {@link FireResult} — never null; check against {@link FireResult#EMPTY}
     */
    public FireResult tryFire(float ox, float oy, float dirX, float dirY,
                              Tank ownerTank, boolean fireHeld) {
        if (hubOpen) return FireResult.EMPTY;

        WeaponType type = weaponSlots[activeWeaponSlot];
        if (type == null) return FireResult.EMPTY;
        if (weaponCooldown[activeWeaponSlot] > 0f) return FireResult.EMPTY;

        // Out of ammo check
        if (weaponAmmo[activeWeaponSlot] == 0) return FireResult.EMPTY;

        return switch (type) {
            case DEFAULT_BULLET -> fireDefault(ox, oy, dirX, dirY);
            case SUBMACHINE_GUN -> fireSMG(ox, oy, dirX, dirY, fireHeld);
            case ARMOR_PIERCE -> fireArmorPierce(ox, oy, dirX, dirY);
            case CANNON -> fireCannon(ox, oy, dirX, dirY);
            case STUN_SHELL -> fireStunShell(ox, oy, dirX, dirY);
            case LASER -> fireLaser(ox, oy, dirX, dirY);
            case HOMING_MISSILE -> fireHoming(ox, oy, dirX, dirY);
        };
    }

    // ─── Weapon Fire Implementations ──────────────────────────────────────────

    private FireResult fireDefault(float ox, float oy, float dx, float dy) {
        weaponCooldown[activeWeaponSlot] = GameConfig.DEFAULT_FIRE_RATE;
        if (sfxManager != null) sfxManager.playWeaponSfx(WeaponType.DEFAULT_BULLET);
        List<Projectile> shots = new ArrayList<>(1);
        shots.add(Projectile.normal(ox, oy, dx, dy,
            GameConfig.DEFAULT_SPEED, GameConfig.DEFAULT_DAMAGE,
            Projectile.Owner.PLAYER, GameConfig.DEFAULT_LIFETIME));

        List<VisualEffect> vfx = new ArrayList<>(1);
        float angle = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees;
        vfx.add(VisualEffect.muzzleFlash(ox, oy, angle, com.badlogic.gdx.graphics.Color.WHITE));

        return new FireResult(shots, Collections.emptyList(), Collections.emptyList(), vfx);
    }

    private FireResult fireSMG(float ox, float oy, float dx, float dy, boolean fireHeld) {
        weaponCooldown[activeWeaponSlot] = GameConfig.SMG_FIRE_RATE;
        if (sfxManager != null) sfxManager.playWeaponSfx(WeaponType.SUBMACHINE_GUN);

        // Increase bloom/spread if fire button is held
        if (fireHeld) {
            smgSpread = Math.min(GameConfig.SMG_MAX_SPREAD_DEG,
                smgSpread + GameConfig.SMG_SPREAD_BUILD_RATE * GameConfig.SMG_FIRE_RATE);
        }

        float spread = smgSpread + GameConfig.SMG_BASE_SPREAD_DEG;
        float spreadRad = MathUtils.degreesToRadians * ((float) (Math.random() * 2 - 1) * spread);
        float cos = MathUtils.cos(spreadRad), sin = MathUtils.sin(spreadRad);
        float sx = dx * cos - dy * sin;
        float sy = dx * sin + dy * cos;

        List<Projectile> shots = new ArrayList<>(1);
        shots.add(Projectile.normal(ox, oy, sx, sy,
            GameConfig.SMG_SPEED, GameConfig.SMG_DAMAGE,
            Projectile.Owner.PLAYER, GameConfig.SMG_LIFETIME));
        List<VisualEffect> vfx = new ArrayList<>(1);
        float angle = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees;
        vfx.add(VisualEffect.muzzleFlash(ox, oy, angle, com.badlogic.gdx.graphics.Color.WHITE));

        return new FireResult(shots, Collections.emptyList(), Collections.emptyList(), vfx);
    }

    private FireResult fireArmorPierce(float ox, float oy, float dx, float dy) {
        weaponCooldown[activeWeaponSlot] = GameConfig.AP_FIRE_RATE;
        if (sfxManager != null) sfxManager.playWeaponSfx(WeaponType.ARMOR_PIERCE);
        List<Projectile> shots = new ArrayList<>(1);
        shots.add(Projectile.piercing(ox, oy, dx, dy,
            GameConfig.AP_SPEED, GameConfig.AP_DAMAGE,
            Projectile.Owner.PLAYER, GameConfig.AP_LIFETIME,
            GameConfig.AP_MAX_PIERCE, GameConfig.AP_DAMAGE_FALLOFF));
        List<VisualEffect> vfx = new ArrayList<>(1);
        float angle = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees;
        vfx.add(VisualEffect.muzzleFlash(ox, oy, angle, com.badlogic.gdx.graphics.Color.WHITE));

        return new FireResult(shots, Collections.emptyList(), Collections.emptyList(), vfx);
    }

    private FireResult fireCannon(float ox, float oy, float dx, float dy) {
        consumeAmmoAndSetCooldown(GameConfig.CANNON_COOLDOWN);
        if (sfxManager != null) sfxManager.playWeaponSfx(WeaponType.CANNON);
        List<Projectile> shots = new ArrayList<>(1);
        shots.add(Projectile.aoe(ox, oy, dx, dy,
            GameConfig.CANNON_SPEED, GameConfig.CANNON_DIRECT_DAMAGE,
            Projectile.Owner.PLAYER, GameConfig.CANNON_LIFETIME,
            GameConfig.CANNON_AOE_RADIUS));
        List<VisualEffect> vfx = new ArrayList<>(1);
        float angle = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees;
        vfx.add(VisualEffect.muzzleFlash(ox, oy, angle, com.badlogic.gdx.graphics.Color.WHITE));

        return new FireResult(shots, Collections.emptyList(), Collections.emptyList(), vfx);
    }

    private FireResult fireStunShell(float ox, float oy, float dx, float dy) {
        consumeAmmoAndSetCooldown(GameConfig.STUN_COOLDOWN);
        if (sfxManager != null) sfxManager.playWeaponSfx(WeaponType.STUN_SHELL);
        List<Projectile> shots = new ArrayList<>(1);
        shots.add(Projectile.stunAoe(ox, oy, dx, dy,
            GameConfig.STUN_SPEED, GameConfig.STUN_DAMAGE,
            Projectile.Owner.PLAYER, GameConfig.STUN_LIFETIME,
            GameConfig.STUN_AOE_RADIUS, GameConfig.STUN_DURATION));
        List<VisualEffect> vfx = new ArrayList<>(1);
        float angle = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees;
        vfx.add(VisualEffect.muzzleFlash(ox, oy, angle, com.badlogic.gdx.graphics.Color.WHITE));

        return new FireResult(shots, Collections.emptyList(), Collections.emptyList(), vfx);
    }

    private FireResult fireLaser(float ox, float oy, float dx, float dy) {
        consumeAmmoAndSetCooldown(GameConfig.LASER_COOLDOWN);
        if (sfxManager != null) sfxManager.playWeaponSfx(WeaponType.LASER);
        List<LaserBeam> beams = new ArrayList<>(1);
        beams.add(new LaserBeam(ox, oy, dx, dy,
            GameConfig.LASER_RANGE, GameConfig.LASER_DAMAGE,
            Projectile.Owner.PLAYER));
        return new FireResult(Collections.emptyList(), beams, Collections.emptyList(), Collections.emptyList());
    }

    private FireResult fireHoming(float ox, float oy, float dx, float dy) {
        consumeAmmoAndSetCooldown(GameConfig.HOMING_COOLDOWN);
        if (sfxManager != null) sfxManager.playWeaponSfx(WeaponType.HOMING_MISSILE);
        List<Projectile> shots = new ArrayList<>(1);
        // Target assigned later by GameWorld upon detecting nearby enemies
        shots.add(Projectile.homing(ox, oy, dx, dy,
            GameConfig.HOMING_SPEED, GameConfig.HOMING_DAMAGE,
            Projectile.Owner.PLAYER, GameConfig.HOMING_LIFETIME,
            GameConfig.HOMING_TURN_RATE_DEG));
        List<VisualEffect> vfx = new ArrayList<>(1);
        float angle = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees;
        vfx.add(VisualEffect.muzzleFlash(ox, oy, angle, com.badlogic.gdx.graphics.Color.WHITE));

        return new FireResult(shots, Collections.emptyList(), Collections.emptyList(), vfx);
    }

    // ─── Equipment Activation ─────────────────────────────────────────────────

    /**
     * Activates tactical equipment assigned to the specified slot.
     *
     * @param slot      equipment slot index (0, 1, 2, or 3 corresponding to keys 1-4)
     * @param ownerTank player tank instance (required for attaching continuous effects like EnergyShield)
     * @return {@link FireResult} containing the spawned world effects; {@link FireResult#EMPTY} if on cooldown
     */
    public FireResult useEquipment(int slot, Tank ownerTank) {
        if (slot < 0 || slot >= EQUIPMENT_SLOTS) return FireResult.EMPTY;
        EquipmentType equip = equipSlots[slot];
        if (equip == null || equipCooldown[slot] > 0f) return FireResult.EMPTY;

        equipCooldown[slot] = equip.cooldown;

        float ex = ownerTank.getPosition().x;
        float ey = ownerTank.getPosition().y;

        List<WorldEffect> effects = new ArrayList<>(1);
        switch (equip) {
            case SMOKE_BOMB:
                effects.add(new WorldEffect.SmokeBomb(ex, ey));
                break;
            case ENERGY_SHIELD:
                effects.add(new WorldEffect.EnergyShield(ownerTank));
                break;
            case ANTI_TANK_MINE:
                effects.add(new WorldEffect.Mine(ex, ey));
                break;
            case EMP_FIELD:
                effects.add(new WorldEffect.EmpField(ex, ey));
                break;
        }
        return new FireResult(Collections.emptyList(), Collections.emptyList(), effects, Collections.emptyList());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Consumes one ammo unit and sets the cooldown timer for limited-ammo weapons.
     */
    private void consumeAmmoAndSetCooldown(float cooldown) {
        if (weaponAmmo[activeWeaponSlot] > 0) {
            weaponAmmo[activeWeaponSlot]--;
        }
        weaponCooldown[activeWeaponSlot] = cooldown;
    }

    /**
     * Called when the fire button is released.
     */
    public void releaseFire() {
        // Spread decays naturally inside update(); no extra logic required.
    }

    // ─── Queries ─────────────────────────────────────────────────────────────

    public WeaponType getActiveWeapon() {
        return weaponSlots[activeWeaponSlot];
    }

    public int getActiveWeaponSlot() {
        return activeWeaponSlot;
    }

    public WeaponType[] getWeaponSlots() {
        return weaponSlots;
    }

    public int[] getWeaponAmmo() {
        return weaponAmmo;
    }

    public float[] getWeaponCooldown() {
        return weaponCooldown;
    }

    public EquipmentType[] getEquipSlots() {
        return equipSlots;
    }

    public float[] getEquipCooldown() {
        return equipCooldown;
    }

    public float getSmgSpread() {
        return smgSpread;
    }

    /**
     * Returns true if the currently active weapon slot is on cooldown.
     */
    public boolean isActiveOnCooldown() {
        return weaponCooldown[activeWeaponSlot] > 0f;
    }

    /**
     * Returns the remaining cooldown duration of the active weapon slot (0 if ready).
     */
    public float getActiveCooldownRemaining() {
        return weaponCooldown[activeWeaponSlot];
    }

    /**
     * Returns the maximum cooldown reference for the active weapon (used for HUD progress bar rendering).
     */
    public float getActiveCooldownMax() {
        WeaponType t = weaponSlots[activeWeaponSlot];
        if (t == null) return 1f;
        return t.isSpecial() ? t.cooldown : t.fireRate;
    }

    /**
     * Calculates the cooldown progress ratio between 0.0 and 1.0 (0 = just fired, 1 = fully ready).
     */
    public float getActiveCooldownProgress() {
        float max = getActiveCooldownMax();
        if (max <= 0f) return 1f;
        return 1f - (weaponCooldown[activeWeaponSlot] / max);
    }

    /**
     * Returns the remaining ammo count for the active weapon (-1 indicates unlimited ammo).
     */
    public int getActiveAmmo() {
        return weaponAmmo[activeWeaponSlot];
    }

    // Legacy compatibility: preserved for backward compatibility with existing callers

    /**
     * @deprecated Use {@link #getActiveWeapon()} instead.
     */
    @Deprecated
    public WeaponType getActiveWeaponCompat() {
        return getActiveWeapon();
    }

    @Deprecated
    public boolean isSwitching() {
        return false;
    }

    @Deprecated
    public float getSwitchTimer() {
        return 0f;
    }

    @Deprecated
    public float getSwitchProgress() {
        return 1f;
    }

    @Deprecated
    public boolean isVulnerable() {
        return false;
    }

    @Deprecated
    public int getActiveSlot() {
        return activeWeaponSlot;
    }

    @Deprecated
    public WeaponType[] getSlots() {
        return weaponSlots;
    }
}
