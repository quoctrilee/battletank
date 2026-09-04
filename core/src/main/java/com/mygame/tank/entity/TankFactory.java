package com.mygame.tank.entity;

import com.mygame.tank.config.BossConfig;
import com.mygame.tank.config.GameConfig;
import com.mygame.tank.controller.ai.BossController;
import com.mygame.tank.controller.ai.StrategyFactory;
import com.mygame.tank.controller.ai.strategy.BossFireStrategy;
import com.mygame.tank.controller.ai.strategy.BossMoveStrategy;
import com.mygame.tank.controller.ai.strategy.BossPhaseStrategy;
import com.mygame.tank.controller.player.PlayerController;
import com.mygame.tank.entity.component.HealthComponent;
import com.mygame.tank.entity.component.MovementComponent;
import com.mygame.tank.entity.component.TankStats;
import com.mygame.tank.entity.component.VisualComponent;
import com.mygame.tank.entity.component.turret.EnemyTurretComponent;
import com.mygame.tank.entity.component.turret.PlayerTurretComponent;
import com.mygame.tank.controller.ai.EnemyController;
import com.mygame.tank.weapon.WeaponSystem;

/**
 * Static factory — assembles fully wired {@link Tank} instances.
 * <p>
 * All component construction and config wiring lives here, keeping
 * Tank, components, and controllers free of cross-references.
 * Every tank now receives a {@link VisualComponent} so
 * {@link com.mygame.tank.render.GameRenderer} never needs to fall back to
 * hardcoded colours or sizes.
 */
public final class TankFactory {

    private TankFactory() {}

    // ── Player ───────────────────────────────────────────────────────────────

    public static Tank createPlayer(float x, float y) {
        TankStats stats = new TankStats(
            GameConfig.PLAYER_WIDTH,
            GameConfig.PLAYER_HEIGHT,
            GameConfig.PLAYER_MAX_SPEED,
            GameConfig.PLAYER_ACCELERATION,
            GameConfig.PLAYER_DECELERATION);

        HealthComponent health = new HealthComponent(GameConfig.PLAYER_MAX_HP);
        MovementComponent movement = new MovementComponent(
            x, y, stats.width, stats.height,
            stats.maxSpeed, stats.acceleration, stats.deceleration);
        PlayerTurretComponent turret = new PlayerTurretComponent(0.6f, new WeaponSystem());
        PlayerController ctrl = new PlayerController();

        VisualComponent visual = new VisualComponent(
            "player_body",     // bodyTextureId  — key in SpriteAssets
            "player_turret",   // turretTextureId
            "player_bullet",   // bulletTextureId
            1.6f,              // bodyScale
            1.4f               // turretScale
        );

        return new Tank(health, movement, turret, ctrl, stats, null, visual);
    }

    // ── Regular enemy ────────────────────────────────────────────────────────

    public static Tank createEnemy(float x, float y, String areaId) {
        TankStats stats = new TankStats(
            GameConfig.ENEMY_WIDTH,
            GameConfig.ENEMY_HEIGHT,
            GameConfig.ENEMY_BASIC_SPEED,
            0f, 0f);

        HealthComponent health = new HealthComponent(GameConfig.ENEMY_BASIC_HP);
        MovementComponent movement = new MovementComponent(
            x, y, stats.width, stats.height, stats.maxSpeed, 0f, 0f);
        EnemyTurretComponent turret = new EnemyTurretComponent(
            GameConfig.ENEMY_BASIC_FIRE_RATE,
            GameConfig.ENEMY_BASIC_BULLET_SPEED,
            GameConfig.ENEMY_BASIC_DAMAGE,
            0.6f, Projectile.Owner.ENEMY);
        EnemyController ctrl = new EnemyController();

        VisualComponent visual = new VisualComponent(
            "enemy_body",
            "enemy_turret",
            "enemy_bullet",
            1.0f,   // bodyScale  — enemies are drawn at hitbox size (shape renderer scales separately)
            1.0f    // turretScale
        );

        return new Tank(health, movement, turret, ctrl, stats, areaId, visual);
    }

    // ── Boss ─────────────────────────────────────────────────────────────────

    /**
     * Creates a boss tank from a {@link BossConfig}.
     * All stats, strategies, and visual data are taken from the config — no
     * hardcoded constants, allowing designer-driven boss variety via JSON.
     *
     * @param x      spawn X in world coordinates
     * @param y      spawn Y in world coordinates
     * @param config the loaded boss configuration
     * @return a fully wired boss Tank ready to be added to GameWorld
     */
    public static Tank createBoss(float x, float y, BossConfig config) {
        TankStats stats = new TankStats(
            config.width,
            config.height,
            0f, 0f, 0f);  // boss movement is controlled entirely by MoveStrategy

        HealthComponent health = new HealthComponent(config.maxHp);
        MovementComponent movement = new MovementComponent(
            x, y, stats.width, stats.height, 0f, 0f, 0f);
        EnemyTurretComponent turret = new EnemyTurretComponent(
            config.fireRate,
            config.bulletSpeed,
            config.damage,
            1.2f, Projectile.Owner.BOSS);

        // Build strategies from config
        BossMoveStrategy  moveStrategy  = StrategyFactory.createMove(config);
        BossFireStrategy  fireStrategy  = StrategyFactory.createFire(config);
        BossPhaseStrategy phaseStrategy = StrategyFactory.createPhase(config);

        BossController ctrl = new BossController(x, y, moveStrategy, fireStrategy, phaseStrategy);

        VisualComponent visual = new VisualComponent(
            config.bodyTextureId,
            config.turretTextureId,
            config.bulletTextureId,
            config.bodyScale,
            config.turretScale
        );

        return new Tank(health, movement, turret, ctrl, stats, "BOSS_" + config.id, visual);
    }
}
