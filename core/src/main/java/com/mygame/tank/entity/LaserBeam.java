package com.mygame.tank.entity;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.mygame.tank.config.GameConfig;

/**
 * Channel-type laser — a two-phase attack spawned by the LASER weapon:
 *
 * <ol>
 *   <li><b>TELEGRAPH</b> ({@link GameConfig#LASER_TELEGRAPH_TIME} s) — a dim, dashed aim
 *       indicator is drawn. The laser has not fired yet, giving enemies visual warning.</li>
 *   <li><b>BEAM</b> ({@link GameConfig#LASER_BEAM_TIME} s) — a bright, solid beam fires
 *       along the aim direction. GameWorld queries {@link #isBeamActive()} every frame
 *       to apply damage to tanks on the line.</li>
 * </ol>
 *
 * After the beam phase the laser marks itself dead and transitions to cooldown
 * inside WeaponSystem (not tracked here).
 */
public class LaserBeam {

    public enum Phase { TELEGRAPH, BEAM, DONE }

    /** Where the laser originates (muzzle position). */
    private final Vector2 origin;

    /** Unit direction vector the laser travels along. */
    private final Vector2 direction;

    /** Total range of the beam in pixels. */
    private final float range;

    /** Damage dealt to each tank on the beam line during the active BEAM phase. */
    private final float damage;

    /** Which team fired this laser (for friendly-fire exclusion). */
    private final Projectile.Owner owner;

    private Phase phase;
    private float phaseTimer;

    /** True for exactly one frame after the beam becomes active — used to apply damage once. */
    private boolean damageAppliedThisBeam;

    public LaserBeam(float originX, float originY, float dirX, float dirY,
                     float range, float damage, Projectile.Owner owner) {
        this.origin    = new Vector2(originX, originY);
        this.direction = new Vector2(dirX, dirY).nor();
        this.range     = range;
        this.damage    = damage;
        this.owner     = owner;
        this.phase     = Phase.TELEGRAPH;
        this.phaseTimer = GameConfig.LASER_TELEGRAPH_TIME;
        this.damageAppliedThisBeam = false;
    }

    // ─── Update ───────────────────────────────────────────────────────────────

    public void update(float delta) {
        phaseTimer -= delta;
        if (phaseTimer <= 0f) {
            advancePhase();
        }
    }

    private void advancePhase() {
        switch (phase) {
            case TELEGRAPH:
                phase     = Phase.BEAM;
                phaseTimer = GameConfig.LASER_BEAM_TIME;
                damageAppliedThisBeam = false;
                break;
            case BEAM:
                phase = Phase.DONE;
                break;
            default:
                break;
        }
    }

    // ─── Geometry helpers ─────────────────────────────────────────────────────

    /** End point of the beam line. */
    public Vector2 getEndPoint() {
        return new Vector2(
                origin.x + direction.x * range,
                origin.y + direction.y * range);
    }

    /**
     * Returns the closest point on the beam segment to {@code point}, used for
     * hit-detection against tank bounding circles.
     */
    public Vector2 closestPointOnBeam(Vector2 point) {
        Vector2 toPoint = new Vector2(point).sub(origin);
        float   t       = toPoint.dot(direction);
        t = MathUtils.clamp(t, 0f, range);
        return new Vector2(origin).add(direction.x * t, direction.y * t);
    }

    /**
     * Returns the perpendicular distance from {@code point} to the beam line.
     * Used to test whether a tank (treated as a circle of radius {@code tankRadius})
     * intersects the beam.
     */
    public float distanceToBeam(Vector2 point) {
        return closestPointOnBeam(point).dst(point);
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public Phase            getPhase()      { return phase; }
    public boolean          isAlive()       { return phase != Phase.DONE; }
    public boolean          isBeamActive()  { return phase == Phase.BEAM; }
    public boolean          isTelegraph()   { return phase == Phase.TELEGRAPH; }
    public Vector2          getOrigin()     { return origin; }
    public Vector2          getDirection()  { return direction; }
    public float            getRange()      { return range; }
    public float            getDamage()     { return damage; }
    public Projectile.Owner getOwner()      { return owner; }

    /** True if damage has already been applied during the current beam phase. */
    public boolean hasDamageBeenApplied() { return damageAppliedThisBeam; }

    /** Mark damage as applied for this beam activation. */
    public void markDamageApplied()       { damageAppliedThisBeam = true; }

    /** Fraction of current phase completed (0 → 1). */
    public float getPhaseProgress() {
        float total = (phase == Phase.TELEGRAPH)
                ? GameConfig.LASER_TELEGRAPH_TIME
                : GameConfig.LASER_BEAM_TIME;
        return 1f - MathUtils.clamp(phaseTimer / total, 0f, 1f);
    }
}
