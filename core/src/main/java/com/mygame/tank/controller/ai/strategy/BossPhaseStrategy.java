package com.mygame.tank.controller.ai.strategy;

import com.mygame.tank.controller.ai.BossController;
import com.mygame.tank.entity.Tank;

/**
 * Strategy interface for boss phase management and minion spawning.
 * <p>
 * Implementations watch HP thresholds (or other conditions), drive phase
 * transitions, and queue mini-tank spawns.  The {@link BossController} calls
 * {@link #update} every frame and drains {@link #consumePendingMiniSpawn()}
 * so {@link com.mygame.tank.world.GameWorld} can actually create the tanks.
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>Only this strategy may write to the boss's {@link com.mygame.tank.entity.component.VisualComponent}
 *       tint (to reflect phase changes).</li>
 *   <li>{@link #consumePendingMiniSpawn()} must atomically return and clear the counter.</li>
 *   <li>Do NOT move the tank — that is {@link BossMoveStrategy}'s job.</li>
 * </ul>
 */
public interface BossPhaseStrategy {

    /**
     * Called every frame; checks conditions and updates phase/tint/spawn queues.
     *
     * @param tank    the boss tank
     * @param delta   seconds elapsed this frame
     * @param context shared BossController state (can be queried for current phase, etc.)
     */
    void update(Tank tank, float delta, BossController context);

    /**
     * Returns and clears the number of mini-tanks that should be spawned this frame.
     * GameWorld calls this after {@link #update} and spawns exactly that many tanks.
     *
     * @return spawn count ≥ 0
     */
    int consumePendingMiniSpawn();

    /**
     * Returns a generic phase index (0 = phase 1, 1 = phase 2, …) so other
     * strategies can scale their behaviour per phase without knowing the
     * concrete phase implementation.
     */
    int getCurrentPhaseIndex();
}
