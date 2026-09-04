package com.mygame.tank.controller.ai.strategy;

import com.badlogic.gdx.math.Vector2;
import com.mygame.tank.controller.ai.BossController;
import com.mygame.tank.entity.Tank;

/**
 * Strategy interface for boss movement.
 * <p>
 * Implementations own any state required for their movement pattern
 * (orbit angle, dash velocity, zigzag timer, etc.).
 * The {@link BossController} holds a reference to the active instance and
 * calls {@link #update} every frame.
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>Mutate {@code tank}'s {@link com.mygame.tank.entity.component.MovementComponent}
 *       (position, body angle) as needed.</li>
 *   <li>Read shared state from {@code context} (spawn centre, direction flip, etc.).</li>
 *   <li>Do NOT fire projectiles — that is the responsibility of {@link BossFireStrategy}.</li>
 * </ul>
 */
public interface BossMoveStrategy {

    /**
     * Updates the boss tank's position/angle for this frame.
     *
     * @param tank      the boss tank being moved
     * @param delta     seconds elapsed this frame
     * @param playerPos current player world position (may be used for targeting)
     * @param context   shared BossController state (spawn center, orbit direction, etc.)
     */
    void update(Tank tank, float delta, Vector2 playerPos, BossController context);
}
