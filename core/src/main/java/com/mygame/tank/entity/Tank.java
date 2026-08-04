package com.mygame.tank.entity;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.mygame.tank.controller.TankController;
import com.mygame.tank.controller.UpdateContext;
import com.mygame.tank.entity.component.HealthComponent;
import com.mygame.tank.entity.component.MovementComponent;
import com.mygame.tank.entity.component.TankStats;
import com.mygame.tank.entity.component.TurretComponent;

import java.util.List;

/**
 * Unified tank entity used for both the player and all enemy types.
 *
 * Follows the Composition pattern:
 *   - {@link HealthComponent}   — HP, alive state, stun, EMP suppression
 *   - {@link MovementComponent} — position, bounds, body angle, movement
 *   - {@link TurretComponent}   — turret angle, fire logic, own combat stats
 *   - {@link TankController}    — all behavioural logic (strategy pattern)
 *
 * The controller is the only place that reads input or makes decisions.
 * Components hold state; the controller drives mutations.
 */
public class Tank {

    private final HealthComponent   health;
    private final MovementComponent movement;
    private final TurretComponent   turret;
    private final TankController    controller;
    private final TankStats         stats;
    private final String            areaId;   // null for player; areaId for enemies/boss

    public Tank(HealthComponent   health,
                MovementComponent movement,
                TurretComponent   turret,
                TankController    controller,
                TankStats         stats,
                String            areaId) {
        this.health     = health;
        this.movement   = movement;
        this.turret     = turret;
        this.controller = controller;
        this.stats      = stats;
        this.areaId     = areaId;
    }

    /**
     * Delegates per-frame logic entirely to the controller.
     *
     * @param delta seconds elapsed this frame
     * @param ctx   shared per-frame context (input / player position / area flags)
     * @return projectiles fired this frame — never null
     */
    public List<Projectile> update(float delta, UpdateContext ctx) {
        health.update(delta);
        return controller.update(this, delta, ctx);
    }

    /** Apply incoming damage — routed to HealthComponent. */
    public void takeDamage(float amount) {
        health.takeDamage(amount);
    }

    /** Apply stun effect — routed to HealthComponent. */
    public void applyStun(float duration) {
        health.applyStun(duration);
    }

    /** Apply EMP suppression — routed to HealthComponent. */
    public void applyEmp(float duration) {
        health.applyEmp(duration);
    }

    /** Push entity out of a collision overlap — routed to MovementComponent. */
    public void resolvePosition(float newX, float newY) {
        movement.resolvePosition(newX, newY);
    }

    // ─── Component accessors ─────────────────────────────────────────────────
    public HealthComponent   getHealth()     { return health; }
    public MovementComponent getMovement()   { return movement; }
    public TurretComponent   getTurret()     { return turret; }
    public TankController    getController() { return controller; }
    public TankStats         getStats()      { return stats; }

    // ─── Convenience passthroughs (GameWorld / Renderer / CollisionSystem) ───
    public Vector2   getPosition()        { return movement.getPosition(); }
    public Rectangle getBounds()          { return movement.getBounds(); }
    public float     getBodyAngle()       { return movement.getBodyAngle(); }
    public float     getTurretAngle()     { return turret.getTurretAngle(); }
    public boolean   isAlive()            { return health.isAlive(); }
    public float     getHp()              { return health.getHp(); }
    public float     getMaxHp()           { return health.getMaxHp(); }
    public boolean   isStunned()          { return health.isStunned(); }
    public boolean   isEmpSuppressed()   { return health.isEmpSuppressed(); }
    public String    getAreaId()          { return areaId; }
}
