package com.mygame.tank.controller.ai;

import com.mygame.tank.config.BossConfig;
import com.mygame.tank.controller.ai.strategy.*;

/**
 * Creates concrete {@link BossMoveStrategy}, {@link BossFireStrategy}, and
 * {@link BossPhaseStrategy} instances from the short class name stored in
 * {@link BossConfig}.
 *
 * <h3>Registering a new strategy</h3>
 * Add a {@code case} to the appropriate switch statement below.
 * No reflection is used — this keeps the factory straightforward and
 * GWT/Android-safe without any special configuration.
 */
public final class StrategyFactory {

    private StrategyFactory() {}

    // ─── Move ─────────────────────────────────────────────────────────────────

    public static BossMoveStrategy createMove(BossConfig cfg) {
        return switch (cfg.moveStrategyClass) {
            case "OrbitMoveStrategy"                 -> new OrbitMoveStrategy(cfg);
            case "ZigzagDashMoveStrategy"            -> new ZigzagDashMoveStrategy(cfg);
            case "StationaryThenChargeMoveStrategy"  -> new StationaryThenChargeMoveStrategy(cfg);
            default -> throw new IllegalArgumentException(
                "Unknown BossMoveStrategy: " + cfg.moveStrategyClass);
        };
    }

    // ─── Fire ─────────────────────────────────────────────────────────────────

    public static BossFireStrategy createFire(BossConfig cfg) {
        return switch (cfg.fireStrategyClass) {
            case "SpreadFireStrategy"     -> new SpreadFireStrategy(cfg);
            case "HomingFireStrategy"     -> new HomingFireStrategy(cfg);
            case "SiegeCannonFireStrategy"-> new SiegeCannonFireStrategy(cfg);
            default -> throw new IllegalArgumentException(
                "Unknown BossFireStrategy: " + cfg.fireStrategyClass);
        };
    }

    // ─── Phase ────────────────────────────────────────────────────────────────

    public static BossPhaseStrategy createPhase(BossConfig cfg) {
        return switch (cfg.phaseStrategyClass) {
            case "ThresholdPhaseStrategy" -> new ThresholdPhaseStrategy(cfg);
            case "TwoPhaseRushStrategy"   -> new TwoPhaseRushStrategy(cfg);
            case "SiegePhaseStrategy"     -> new SiegePhaseStrategy(cfg);
            default -> throw new IllegalArgumentException(
                "Unknown BossPhaseStrategy: " + cfg.phaseStrategyClass);
        };
    }
}
