package com.mygame.tank.entity;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.mygame.tank.config.GameConfig;

/**
 * A projectile flying through the world.
 * <p>
 * Supports four behaviours via {@link ProjectileType}:
 * <ul>
 *   <li>{@code NORMAL}   — straight-line flight, destroys on first hit</li>
 *   <li>{@code PIERCING} — straight line, passes through multiple tanks</li>
 *   <li>{@code AOE}      — explodes on impact or lifetime expiry</li>
 *   <li>{@code STUN_AOE} — AoE + applies stun to tanks in radius</li>
 *   <li>{@code HOMING}   — steers toward a designated target tank</li>
 * </ul>
 * <p>
 * Laser beams use a separate {@link LaserBeam} entity, not Projectile.
 */
public class Projectile {

    // ─── Types ────────────────────────────────────────────────────────────────

    public enum Owner {PLAYER, ENEMY, BOSS}

    public enum ProjectileType {
        NORMAL,
        PIERCING,
        AOE,
        STUN_AOE,
        HOMING
    }

    // ─── Core fields ─────────────────────────────────────────────────────────

    private final Vector2 position;
    private final Vector2 direction;   // normalized unit vector (mutable for homing)
    private final float speed;
    private final float damage;
    private final Owner owner;
    private final ProjectileType type;
    private final Rectangle bounds;

    private float lifetime;
    private boolean alive;

    // ─── Piercing ────────────────────────────────────────────────────────────

    /**
     * Remaining pierce passes (Group A armor-pierce). Ignored for non-PIERCING types.
     */
    private int pierceCount;

    /**
     * Damage multiplier applied to each subsequent pierce hit.
     */
    private float pierceDamageMult;

    /**
     * Tracks accumulated damage decay after multiple pierces.
     */
    private float currentDamageMult;

    // ─── AoE ─────────────────────────────────────────────────────────────────

    private final float aoeRadius;

    /**
     * True when this projectile has triggered its explosion (AoE / STUN_AOE).
     */
    private boolean exploded;

    // ─── Stun (STUN_AOE only) ────────────────────────────────────────────────

    private final float stunDuration;

    // ─── Homing ──────────────────────────────────────────────────────────────

    /**
     * The tank this missile locks onto. May become null if target dies.
     */
    private Tank homingTarget;

    /**
     * Maximum steering angular rate in degrees/second.
     */
    private final float maxTurnRateDeg;

    // ─── Constructors ─────────────────────────────────────────────────────────

    /**
     * Full constructor — used by factory methods below.
     * Callers should prefer the static factories for clarity.
     */
    private Projectile(float x, float y, float dirX, float dirY, float speed, float damage,
                       Owner owner, float lifetime, ProjectileType type,
                       float aoeRadius, float stunDuration, int pierceCount,
                       float pierceDamageMult, float maxTurnRateDeg) {
        this.position = new Vector2(x, y);
        this.direction = new Vector2(dirX, dirY).nor();
        this.speed = speed;
        this.damage = damage;
        this.owner = owner;
        this.lifetime = lifetime;
        this.alive = true;
        this.type = type;
        this.aoeRadius = aoeRadius;
        this.stunDuration = stunDuration;
        this.pierceCount = pierceCount;
        this.pierceDamageMult = pierceDamageMult;
        this.currentDamageMult = 1f;
        this.maxTurnRateDeg = maxTurnRateDeg;
        this.exploded = false;

        float half = GameConfig.BULLET_SIZE / 2f;
        this.bounds = new Rectangle(x - half, y - half,
            GameConfig.BULLET_SIZE, GameConfig.BULLET_SIZE);
    }

    // ─── Static factories ─────────────────────────────────────────────────────

    public static Projectile normal(float x, float y, float dx, float dy,
                                    float speed, float damage, Owner owner, float lifetime) {
        return new Projectile(x, y, dx, dy, speed, damage, owner, lifetime,
            ProjectileType.NORMAL, 0f, 0f, 0, 1f, 0f);
    }

    public static Projectile piercing(float x, float y, float dx, float dy,
                                      float speed, float damage, Owner owner, float lifetime,
                                      int pierceCount, float damageFalloff) {
        return new Projectile(x, y, dx, dy, speed, damage, owner, lifetime,
            ProjectileType.PIERCING, 0f, 0f, pierceCount, damageFalloff, 0f);
    }

    public static Projectile aoe(float x, float y, float dx, float dy,
                                 float speed, float damage, Owner owner, float lifetime,
                                 float aoeRadius) {
        return new Projectile(x, y, dx, dy, speed, damage, owner, lifetime,
            ProjectileType.AOE, aoeRadius, 0f, 0, 1f, 0f);
    }

    public static Projectile stunAoe(float x, float y, float dx, float dy,
                                     float speed, float damage, Owner owner, float lifetime,
                                     float aoeRadius, float stunDuration) {
        return new Projectile(x, y, dx, dy, speed, damage, owner, lifetime,
            ProjectileType.STUN_AOE, aoeRadius, stunDuration, 0, 1f, 0f);
    }

    public static Projectile homing(float x, float y, float dx, float dy,
                                    float speed, float damage, Owner owner, float lifetime,
                                    float maxTurnRateDeg) {
        return new Projectile(x, y, dx, dy, speed, damage, owner, lifetime,
            ProjectileType.HOMING, 0f, 0f, 0, 1f, maxTurnRateDeg);
    }

    // ─── Update ───────────────────────────────────────────────────────────────

    public void update(float delta) {
        if (!alive) return;

        if (type == ProjectileType.HOMING) {
            steerTowardTarget(delta);
        }

        position.x += direction.x * speed * delta;
        position.y += direction.y * speed * delta;
        bounds.setCenter(position.x, position.y);

        lifetime -= delta;
        if (lifetime <= 0f) {
            // AoE projectiles explode on lifetime expiry
            if (type == ProjectileType.AOE || type == ProjectileType.STUN_AOE) {
                exploded = true;
            }
            alive = false;
        }
    }

    /**
     * Steers direction toward the homing target by at most {@link #maxTurnRateDeg} deg/s.
     */
    private void steerTowardTarget(float delta) {
        if (homingTarget == null || !homingTarget.isAlive()) return;

        Vector2 toTarget = new Vector2(
            homingTarget.getPosition().x - position.x,
            homingTarget.getPosition().y - position.y);
        if (toTarget.len2() < 1f) return;
        toTarget.nor();

        float currentAngle = MathUtils.atan2(direction.y, direction.x) * MathUtils.radiansToDegrees;
        float desiredAngle = MathUtils.atan2(toTarget.y, toTarget.x) * MathUtils.radiansToDegrees;
        float diff = (desiredAngle - currentAngle) % 360f;
        if (diff > 180f) diff -= 360f;
        else if (diff < -180f) diff += 360f;

        float maxTurn = maxTurnRateDeg * delta;
        float turnDeg = MathUtils.clamp(diff, -maxTurn, maxTurn);
        float newAngle = MathUtils.degreesToRadians * (currentAngle + turnDeg);

        direction.set(MathUtils.cos(newAngle), MathUtils.sin(newAngle));
    }

    // ─── Piercing logic ───────────────────────────────────────────────────────

    /**
     * Called when this piercing projectile hits a tank.
     * Reduces pierce count; destroys when depleted.
     *
     * @return actual damage to apply this hit
     */
    public float onPierceHit() {
        float dmg = damage * currentDamageMult;
        currentDamageMult *= pierceDamageMult;
        pierceCount--;
        if (pierceCount <= 0) alive = false;
        return dmg;
    }

    // ─── AoE / Stun ──────────────────────────────────────────────────────────

    /**
     * Trigger an AoE explosion at current position.
     * Sets exploded flag so GameWorld can process radius damage.
     */
    public void triggerExplosion() {
        if (type == ProjectileType.AOE || type == ProjectileType.STUN_AOE) {
            exploded = true;
        }
        alive = false;
    }

    // ─── Standard destroy ─────────────────────────────────────────────────────

    /**
     * Mark for removal without triggering AoE (e.g., wall hit).
     */
    public void destroy() {
        alive = false;
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public boolean isAlive() {
        return alive;
    }

    public boolean hasExploded() {
        return exploded;
    }

    public Vector2 getPosition() {
        return position;
    }

    public Vector2 getDirection() {
        return direction;
    }

    public Rectangle getBounds() {
        return bounds;
    }

    public float getDamage() {
        return damage;
    }

    public float getCurrentDamage() {
        return damage * currentDamageMult;
    }

    public Owner getOwner() {
        return owner;
    }

    public ProjectileType getType() {
        return type;
    }

    public float getAoeRadius() {
        return aoeRadius;
    }

    public float getStunDuration() {
        return stunDuration;
    }

    public Tank getHomingTarget() {
        return homingTarget;
    }

    public void setHomingTarget(Tank target) {
        this.homingTarget = target;
    }
}
