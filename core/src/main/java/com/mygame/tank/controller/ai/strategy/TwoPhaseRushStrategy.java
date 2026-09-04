package com.mygame.tank.controller.ai.strategy;

import com.mygame.tank.config.BossConfig;
import com.mygame.tank.controller.ai.BossController;
import com.mygame.tank.entity.Tank;
import com.mygame.tank.entity.component.VisualComponent;

/**
 * Boss 2 — Viper: two-phase strategy with no minion spawning.
 * <p>
 * Phase 0 (HP > phase2Threshold): normal zigzag.
 * Phase 1 (HP ≤ phase2Threshold): signals ZigzagDashMoveStrategy to go faster
 *   (via phaseIndex), tints body with a pulsing toxic-green to signal enrage.
 *
 * Reads params: phase2Threshold (0.45)
 */
public class TwoPhaseRushStrategy implements BossPhaseStrategy {

    private final float phase2Threshold;

    private int   phaseIndex  = 0;
    private float flickerTimer = 0f;

    public TwoPhaseRushStrategy(BossConfig cfg) {
        this.phase2Threshold = cfg.getFloat("phase2Threshold", 0.45f);
    }

    @Override
    public void update(Tank tank, float delta, BossController context) {
        float hpRatio = tank.getHealth().getHp() / tank.getHealth().getMaxHp();

        if (phaseIndex == 0 && hpRatio <= phase2Threshold) {
            phaseIndex = 1;
        }

        VisualComponent visual = tank.getVisual();
        if (visual != null) {
            if (phaseIndex == 1) {
                // Pulsing toxic green — "enrage" visual
                flickerTimer += delta;
                float pulse = (float) Math.sin(flickerTimer * 6f) * 0.5f + 0.5f; // 0..1
                visual.setTint(0.3f + pulse * 0.2f, 1f, 0.3f + pulse * 0.2f, 1f);
            } else {
                visual.resetTint();
            }
        }
    }

    @Override
    public int consumePendingMiniSpawn() {
        return 0; // Viper does not spawn minions
    }

    @Override
    public int getCurrentPhaseIndex() {
        return phaseIndex;
    }
}
