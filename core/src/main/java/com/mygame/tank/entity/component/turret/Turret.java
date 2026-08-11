package com.mygame.tank.entity.component.turret;

import com.badlogic.gdx.math.Vector2;

/**
 * Common contract for anything that aims and fires from a tank body.
 * <p>
 * Callers (Tank, Controllers) should depend on this interface rather than on
 * {@link PlayerTurretComponent} or {@link EnemyTurretComponent} directly, so that
 * adding a new enemy weapon behavior never requires touching player-firing code
 * (and vice versa).
 */
public interface Turret {

    /** Must be called every frame to tick cooldowns. */
    void update(float delta);

    /** Current turret facing, in degrees (0 = right). */
    float getTurretAngle();

    /** Point the turret at {@code target} from {@code origin}. */
    void aimAt(Vector2 origin, Vector2 target);

    /** Override turret facing directly (e.g., fixed-angle bosses). */
    void aimAtAngle(float angleDeg);
}
