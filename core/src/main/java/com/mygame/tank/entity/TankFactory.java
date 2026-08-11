package com.mygame.tank.entity;

import com.mygame.tank.config.GameConfig;
import com.mygame.tank.controller.ai.BossController;
import com.mygame.tank.controller.ai.EnemyController;
import com.mygame.tank.controller.player.PlayerController;
import com.mygame.tank.entity.component.turret.EnemyTurretComponent;
import com.mygame.tank.entity.component.HealthComponent;
import com.mygame.tank.entity.component.MovementComponent;
import com.mygame.tank.entity.component.turret.PlayerTurretComponent;
import com.mygame.tank.entity.component.TankStats;
import com.mygame.tank.weapon.WeaponSystem;

/**
 * Static factory — assembles fully wired {@link Tank} instances.
 *
 * All component construction and config wiring lives here, keeping
 * Tank, components, and controllers free of cross-references.
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

        HealthComponent   health   = new HealthComponent(GameConfig.PLAYER_MAX_HP);
        MovementComponent movement = new MovementComponent(
                x, y, stats.width, stats.height,
                stats.maxSpeed, stats.acceleration, stats.deceleration);
        // Player turret uses WeaponSystem; its combat stats live inside WeaponSystem
        PlayerTurretComponent turret = new PlayerTurretComponent(0.6f, new WeaponSystem());
        PlayerController      ctrl   = new PlayerController();

        return new Tank(health, movement, turret, ctrl, stats, null);
    }

    // ── Regular enemy ────────────────────────────────────────────────────────

    public static Tank createEnemy(float x, float y, String areaId) {
        TankStats stats = new TankStats(
                GameConfig.ENEMY_WIDTH,
                GameConfig.ENEMY_HEIGHT,
                GameConfig.ENEMY_BASIC_SPEED,
                0f, 0f);   // enemies use moveToward — no accel/decel curve

        HealthComponent   health   = new HealthComponent(GameConfig.ENEMY_BASIC_HP);
        MovementComponent movement = new MovementComponent(
                x, y, stats.width, stats.height, stats.maxSpeed, 0f, 0f);
        // Enemy turret owns its own combat stats
        EnemyTurretComponent turret = new EnemyTurretComponent(
                GameConfig.ENEMY_BASIC_FIRE_RATE,
                GameConfig.ENEMY_BASIC_BULLET_SPEED,
                GameConfig.ENEMY_BASIC_DAMAGE,
                0.6f, Projectile.Owner.ENEMY);
        EnemyController      ctrl   = new EnemyController();

        return new Tank(health, movement, turret, ctrl, stats, areaId);
    }

    // ── Boss ─────────────────────────────────────────────────────────────────

    public static Tank createBoss(float x, float y) {
        TankStats stats = new TankStats(
                GameConfig.BOSS_WIDTH,
                GameConfig.BOSS_HEIGHT,
                0f, 0f, 0f);  // boss uses setPosition (orbit) — speed irrelevant

        HealthComponent   health   = new HealthComponent(GameConfig.BOSS_HP);
        MovementComponent movement = new MovementComponent(
                x, y, stats.width, stats.height, 0f, 0f, 0f);
        // Boss turret owns spread-shot stats
        EnemyTurretComponent turret = new EnemyTurretComponent(
                GameConfig.BOSS_SPREAD_FIRE_RATE,
                GameConfig.BOSS_BULLET_SPEED,
                GameConfig.BOSS_BULLET_DAMAGE,
                1.2f, Projectile.Owner.BOSS);
        BossController       ctrl   = new BossController(x, y);

        return new Tank(health, movement, turret, ctrl, stats, "BOSS");
    }
}
