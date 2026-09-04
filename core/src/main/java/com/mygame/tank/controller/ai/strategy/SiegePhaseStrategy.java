package com.mygame.tank.controller.ai.strategy;

import com.mygame.tank.config.BossConfig;
import com.mygame.tank.controller.ai.BossController;
import com.mygame.tank.entity.Tank;
import com.mygame.tank.entity.component.VisualComponent;

/**
 * Boss 3 — Siege: two-phase strategy, no minion spawning.
 * <p>
 * Phase 0 (HP > chargeThreshold): stationary artillery mode — tint stays white/steel.
 * Phase 1 (HP ≤ chargeThreshold): mobile charge mode — tint pulses bright orange
 *   to warn the player before each charge, matching {@link StationaryThenChargeMoveStrategy}.
 *
 * Reads params: chargeThreshold (0.5)
 */
public class SiegePhaseStrategy implements BossPhaseStrategy {

    private final float chargeThreshold;

    private int   phaseIndex  = 0;
    private float pulseTimer  = 0f;

    public SiegePhaseStrategy(BossConfig cfg) {
        this.chargeThreshold = cfg.getFloat("chargeThreshold", 0.5f);
    }

    @Override
    public void update(Tank tank, float delta, BossController context) {
        float hpRatio = tank.getHealth().getHp() / tank.getHealth().getMaxHp();

        if (phaseIndex == 0 && hpRatio <= chargeThreshold) {
            phaseIndex = 1;
        }

        VisualComponent visual = tank.getVisual();
        if (visual != null) {
            if (phaseIndex == 1) {
                // Orange pulse — "charge warning"
                pulseTimer += delta;
                float pulse = (float) Math.sin(pulseTimer * 5f) * 0.5f + 0.5f;
                visual.setTint(1f, 0.4f + pulse * 0.4f, 0.1f, 1f);
            } else {
                // Steel-blue tint — "cold artillery" feel
                visual.setTint(0.7f, 0.8f, 1.0f, 1f);
            }
        }
    }

    @Override
    public int consumePendingMiniSpawn() {
        return 0; // Siege does not spawn minions
    }

    @Override
    public int getCurrentPhaseIndex() {
        return phaseIndex;
    }
}
