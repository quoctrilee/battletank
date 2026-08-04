package com.mygame.tank.entity.component;

/**
 * Owns HP and stun state for a tank.
 *
 * <ul>
 *   <li>HP — damage, healing, alive flag</li>
 *   <li>Stun — duration timer; while stunned the tank cannot move or fire
 *       (enforced by controllers reading {@link #isStunned()})</li>
 *   <li>EMP suppression — disables Group B/C special weapons (not movement)</li>
 * </ul>
 */
public final class HealthComponent {

    private float   hp;
    private final float maxHp;
    private boolean alive;

    // ─── Stun ─────────────────────────────────────────────────────────────────
    private float stunTimer;       // seconds remaining; 0 = not stunned

    // ─── EMP suppression ─────────────────────────────────────────────────────
    private float empTimer;        // seconds special weapons are disabled

    public HealthComponent(float maxHp) {
        this.maxHp     = maxHp;
        this.hp        = maxHp;
        this.alive     = true;
        this.stunTimer = 0f;
        this.empTimer  = 0f;
    }

    // ─── Update ───────────────────────────────────────────────────────────────

    public void update(float delta) {
        if (stunTimer > 0f) stunTimer = Math.max(0f, stunTimer - delta);
        if (empTimer  > 0f) empTimer  = Math.max(0f, empTimer  - delta);
    }

    // ─── Damage ───────────────────────────────────────────────────────────────

    /** Apply damage. Clamps HP to 0 and marks dead when depleted. */
    public void takeDamage(float amount) {
        if (!alive) return;
        hp -= amount;
        if (hp <= 0f) { hp = 0f; alive = false; }
    }

    // ─── Stun ─────────────────────────────────────────────────────────────────

    /**
     * Apply stun for {@code duration} seconds.
     * Stacking: extends existing stun if new duration is longer.
     */
    public void applyStun(float duration) {
        if (stunTimer < duration) stunTimer = duration;
    }

    /** True while this tank is stunned (cannot move or fire). */
    public boolean isStunned() { return stunTimer > 0f; }

    public float   getStunTimer()   { return stunTimer; }

    // ─── EMP ─────────────────────────────────────────────────────────────────

    /**
     * Suppress special weapons for {@code duration} seconds.
     * Stacking: extends if new duration is longer.
     */
    public void applyEmp(float duration) {
        if (empTimer < duration) empTimer = duration;
    }

    /** True while this tank's special weapons are EMP-suppressed. */
    public boolean isEmpSuppressed() { return empTimer > 0f; }

    // ─── Getters ─────────────────────────────────────────────────────────────
    public boolean isAlive()  { return alive; }
    public float   getHp()    { return hp; }
    public float   getMaxHp() { return maxHp; }
}
