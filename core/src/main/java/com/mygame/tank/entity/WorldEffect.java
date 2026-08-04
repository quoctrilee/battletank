package com.mygame.tank.entity;

import com.badlogic.gdx.math.Vector2;
import com.mygame.tank.config.GameConfig;

/**
 * Base class for persistent world-space effects placed by tactical equipment.
 *
 * <p>Subclasses represent distinct equipment outcomes:
 * <ul>
 *   <li>{@link SmokeBomb}    — obscures vision, blocks target-lock</li>
 *   <li>{@link Mine}         — detonates on proximity, AoE damage</li>
 *   <li>{@link EmpField}     — disables special weapons in radius</li>
 *   <li>{@link EnergyShield} — absorbs one projectile hit on the owner tank</li>
 * </ul>
 * <p>
 * GameWorld updates every WorldEffect each frame and removes dead ones.
 */
public abstract class WorldEffect {

    protected final Vector2 position;
    protected float lifetime;
    protected boolean alive;

    protected WorldEffect(float x, float y, float lifetime) {
        this.position = new Vector2(x, y);
        this.lifetime = lifetime;
        this.alive = true;
    }

    /**
     * Called every frame. Subclasses should call {@code super.update(delta)}.
     */
    public void update(float delta) {
        lifetime -= delta;
        if (lifetime <= 0f) alive = false;
    }

    public boolean isAlive() {
        return alive;
    }

    public void destroy() {
        alive = false;
    }

    public Vector2 getPosition() {
        return position;
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Smoke Bomb — creates a circular smoke zone.
     * Tanks inside are not targetable by homing missiles or laser auto-aim.
     */
    public static final class SmokeBomb extends WorldEffect {

        private final float radius;

        public SmokeBomb(float x, float y) {
            super(x, y, GameConfig.SMOKE_DURATION);
            this.radius = GameConfig.SMOKE_RADIUS;
        }

        public float getRadius() {
            return radius;
        }

        /**
         * True if {@code point} lies within the smoke area.
         */
        public boolean contains(Vector2 point) {
            return position.dst(point) <= radius;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Anti-Tank Mine — arms after a short delay, then detonates with AoE damage
     * when any enemy tank enters the trigger radius.
     */
    public static final class Mine extends WorldEffect {

        private float armTimer;
        private boolean armed;
        private boolean detonated;

        public Mine(float x, float y) {
            super(x, y, GameConfig.MINE_LIFETIME);
            this.armTimer = GameConfig.MINE_ARM_DELAY;
            this.armed = false;
            this.detonated = false;
        }

        @Override
        public void update(float delta) {
            super.update(delta);
            if (!armed) {
                armTimer -= delta;
                if (armTimer <= 0f) armed = true;
            }
        }

        /**
         * Check whether a tank at {@code tankPos} should trigger this mine.
         * Only armed mines trigger. Returns true if detonated (GameWorld handles AoE).
         */
        public boolean tryTrigger(Vector2 tankPos) {
            if (!armed || detonated || !alive) return false;
            if (position.dst(tankPos) <= GameConfig.MINE_TRIGGER_RADIUS) {
                detonated = true;
                alive = false;
                return true;
            }
            return false;
        }

        public boolean isArmed() {
            return armed;
        }

        public boolean isDetonated() {
            return detonated;
        }

        public float getArmProgress() {
            return 1f - Math.max(0f, armTimer / GameConfig.MINE_ARM_DELAY);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * EMP Field — disables special-weapon firing for enemy tanks inside the radius.
     * GameWorld checks this each frame and sets the EMP-suppressed flag on affected tanks.
     */
    public static final class EmpField extends WorldEffect {

        private final float radius;

        public EmpField(float x, float y) {
            super(x, y, GameConfig.EMP_EFFECT_LIFETIME);
            this.radius = GameConfig.EMP_RADIUS;
        }

        public float getRadius() {
            return radius;
        }

        public boolean contains(Vector2 point) {
            return position.dst(point) <= radius;
        }

        /**
         * Pulse animation: oscillating alpha (0→1) based on remaining lifetime.
         */
        public float getPulseAlpha() {
            return (float) (0.35f + 0.35f * Math.sin(lifetime * 6f));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Energy Shield — attached to the owning tank, absorbs one hit within its duration.
     * Once a hit is blocked the shield collapses immediately.
     */
    public static final class EnergyShield extends WorldEffect {

        private final Tank owner;
        private boolean broken;

        public EnergyShield(Tank owner) {
            super(owner.getPosition().x, owner.getPosition().y, GameConfig.SHIELD_BLOCK_TIME);
            this.owner = owner;
            this.broken = false;
        }

        @Override
        public void update(float delta) {
            // Track owner position so renderer can draw shield at correct location
            position.set(owner.getPosition());
            super.update(delta);
        }

        /**
         * Attempt to intercept a projectile hit on the owner.
         * Returns true (and collapses the shield) if the shield was active.
         */
        public boolean interceptHit() {
            if (!alive || broken) return false;
            broken = true;
            alive = false;
            return true;
        }

        public Tank getOwner() {
            return owner;
        }

        public boolean isBroken() {
            return broken;
        }

        /**
         * Visual pulse rate for the shield ring.
         */
        public float getRingAlpha() {
            float t = 1f - (lifetime / GameConfig.SHIELD_BLOCK_TIME);
            return (float) (0.7f + 0.3f * Math.sin(t * 20f));
        }
    }
}
