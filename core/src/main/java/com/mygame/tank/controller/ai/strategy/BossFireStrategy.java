package com.mygame.tank.controller.ai.strategy;

import com.badlogic.gdx.math.Vector2;
import com.mygame.tank.controller.ai.BossController;
import com.mygame.tank.entity.Projectile;
import com.mygame.tank.entity.Tank;

import java.util.List;

/**
 * Strategy interface for boss firing patterns.
 * <p>
 * Implementations own their own fire timer, projectile creation logic, and any
 * pattern state (spread angle, burst counter, charge timer, etc.).
 * The {@link BossController} calls {@link #fire} every frame and adds the returned
 * projectiles to the world.
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>Return an empty list when not ready to fire (cooldown, stunned, etc.).</li>
 *   <li>Never return {@code null}.</li>
 *   <li>Do NOT apply movement — that is the responsibility of {@link BossMoveStrategy}.</li>
 * </ul>
 */
public interface BossFireStrategy {

    /**
     * Produces projectiles for this frame.
     *
     * @param tank      the boss tank firing
     * @param delta     seconds elapsed this frame
     * @param playerPos current player world position (for aim calculation)
     * @param context   shared BossController state
     * @return list of projectiles to add to the world — never null, may be empty
     */
    List<Projectile> fire(Tank tank, float delta, Vector2 playerPos, BossController context);
}
