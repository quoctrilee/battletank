package com.mygame.tank.controller.ai.strategy;

import com.mygame.tank.config.BossConfig;
import com.mygame.tank.controller.ai.BossController;
import com.mygame.tank.entity.Tank;
import com.mygame.tank.entity.component.VisualComponent;

/**
 * Boss 1 — Iron Guard: 3-phase system driven by HP thresholds.
 * <ul>
 *   <li>Phase 0 (HP > phase2Threshold): normal orbit + spread fire</li>
 *   <li>Phase 1 (HP ≤ phase2Threshold): spawn mini-tanks immediately, then periodically</li>
 *   <li>Phase 2 (HP ≤ phase3Threshold): faster + bigger spawns, tint flicker red</li>
 * </ul>
 *
 * Reads params:
 *   phase2Threshold (0.5), phase3Threshold (0.25),
 *   miniSpawnInterval (12), miniTankCount (2),
 *   miniSpawnIntervalP3 (7), miniTankCountP3 (3)
 */
public class ThresholdPhaseStrategy implements BossPhaseStrategy {

    private final float phase2Threshold;
    private final float phase3Threshold;
    private final float miniSpawnInterval;
    private final int   miniTankCount;
    private final float miniSpawnIntervalP3;
    private final int   miniTankCountP3;

    private int   phaseIndex       = 0;
    private float miniSpawnTimer;
    private int   pendingSpawn     = 0;
    private float flickerTimer     = 0f;

    public ThresholdPhaseStrategy(BossConfig cfg) {
        this.phase2Threshold      = cfg.getFloat("phase2Threshold",      0.50f);
        this.phase3Threshold      = cfg.getFloat("phase3Threshold",      0.25f);
        this.miniSpawnInterval    = cfg.getFloat("miniSpawnInterval",    12f);
        this.miniTankCount        = cfg.getInt("miniTankCount",          2);
        this.miniSpawnIntervalP3  = cfg.getFloat("miniSpawnIntervalP3",  7f);
        this.miniTankCountP3      = cfg.getInt("miniTankCountP3",        3);
        this.miniSpawnTimer       = miniSpawnInterval;
    }

    @Override
    public void update(Tank tank, float delta, BossController context) {
        float hpRatio = tank.getHealth().getHp() / tank.getHealth().getMaxHp();
        VisualComponent visual = tank.getVisual();

        // ── Phase transitions ───────────────────────────────────────────────
        if (phaseIndex == 0 && hpRatio <= phase2Threshold) {
            phaseIndex = 1;
            miniSpawnTimer = miniSpawnInterval;
            pendingSpawn += miniTankCount;   // immediate spawn on transition
        }
        if (phaseIndex == 1 && hpRatio <= phase3Threshold) {
            phaseIndex = 2;
            miniSpawnTimer = miniSpawnIntervalP3;
            pendingSpawn += miniTankCountP3;
        }

        // ── Periodic spawn (phase 1 & 2) ───────────────────────────────────
        if (phaseIndex >= 1) {
            miniSpawnTimer -= delta;
            if (miniSpawnTimer <= 0f) {
                if (phaseIndex == 2) {
                    miniSpawnTimer = miniSpawnIntervalP3;
                    pendingSpawn += miniTankCountP3;
                } else {
                    miniSpawnTimer = miniSpawnInterval;
                    pendingSpawn += miniTankCount;
                }
            }
        }

        // ── Tint: phase 2 flickers red, phase 1 solid red tint ─────────────
        if (visual != null) {
            if (phaseIndex == 2) {
                flickerTimer += delta;
                boolean bright = ((int)(flickerTimer * 4f) % 2) == 0;
                if (bright) visual.setTint(1f, 0.3f, 0.3f, 1f);
                else        visual.setTint(1f, 0.7f, 0.7f, 1f);
            } else if (phaseIndex == 1) {
                visual.setTint(1f, 0.5f, 0.5f, 1f);
            } else {
                visual.resetTint();
            }
        }
    }

    @Override
    public int consumePendingMiniSpawn() {
        int n = pendingSpawn;
        pendingSpawn = 0;
        return n;
    }

    @Override
    public int getCurrentPhaseIndex() {
        return phaseIndex;
    }
}
